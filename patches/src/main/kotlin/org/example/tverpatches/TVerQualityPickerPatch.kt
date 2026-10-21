package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val PICKER =
    "Lapp/template/extension/quality/VideoQualityPicker;"
private const val PLAYER_UI_STATE =
    "Ljp/play/tverplayer/player/ui/compose/state/PlayerUiState;"
private const val RESOLVER =
    "Ljp/play/tverplayer/player/core/mapper/QualityResolver;"
private const val VIDEO_QUALITY =
    "Ljp/play/tverplayer/player/contract/VideoQuality;"
private const val VIDEO_SIZE =
    "Ljp/play/tverplayer/player/core/mapper/QualityResolver\$VideoSize;"
private const val MAIN_ACTIVITY =
    "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;"
private const val MAIN_PARENT =
    "Ljp/hamitv/hamiand1/tver/ui/activities/Hilt_MainActivity;"

/**
 * Replaces the Compose player's quality popup with a per-video rendition list.
 * No static menu entries: options are enumerated from the Streaks SDK at runtime.
 * Falls back to TVer's original 4-choice popup before tracks are available.
 *
 * Also lifts the new VOD player's phone-only AUTO/HIGH 720p ceiling to 1080p.
 * This does not create a 1080p stream if the server doesn't provide one.
 */
@Suppress("unused")
val tverQualityPickerPatch = bytecodePatch(
    name = "TVer: resolution quality picker (7.2.1)",
    description = "Choose available video resolutions from a list (Auto, 1080p, 720p, etc.) and allow 1080p on phones for regular VOD.",
    default = false,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1"))
    extendWith("extensions/extension.mpe")

    execute {
        // Existing '画質' button -> PlayerUiState.openQualityPicker().
        // Do not replace the Compose layout itself; use the host Activity to
        // present a native single-choice list. The bridge returns false when
        // the SDK is not ready, so the stock dialog remains available.
        val stateClass = mutableClassDefBy(classDefBy(PLAYER_UI_STATE))
        val openMethod = stateClass.methods.single {
            it.name == "openQualityPicker" && it.parameterTypes.isEmpty()
        }
        val openImpl = requireNotNull(openMethod.implementation)
        require(openImpl.registerCount >= 2) {
            "PlayerUiState.openQualityPicker must have one free local register"
        }
        val first = openMethod.getInstruction(0)
        openMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0}, $PICKER->open(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :stock_quality_picker
                return-void
            """.trimIndent(),
            ExternalLabel("stock_quality_picker", first),
        )

        // Record only a weak reference to the foreground Activity. This also
        // composes with the PiP patch, which may insert the same lifecycle
        // override; either patch order is valid.
        val activityClass = mutableClassDefBy(classDefBy(MAIN_ACTIVITY))
        val focusMethod = activityClass.methods.firstOrNull {
            it.name == "onWindowFocusChanged" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == "Z"
        }
        if (focusMethod != null) {
            focusMethod.addInstructions(
                0,
                "invoke-static {p0}, $PICKER->rememberActivity(Landroid/app/Activity;)V",
            )
        } else {
            activityClass.methods.add(
                ImmutableMethod(
                    MAIN_ACTIVITY,
                    "onWindowFocusChanged",
                    listOf(ImmutableMethodParameter("Z", null, null)),
                    "V",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(2),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            invoke-super {p0, p1}, $MAIN_PARENT->onWindowFocusChanged(Z)V
                            invoke-static {p0}, $PICKER->rememberActivity(Landroid/app/Activity;)V
                            return-void
                        """.trimIndent(),
                    )
                }
            )
        }

        // For the new VOD engine, PHONE AUTO/HIGH normally resolve to 720p,
        // whereas TABLET AUTO/HIGH resolve to 1080p. Lift only normal VOD;
        // Shorts, live streams, medium and low settings remain unchanged.
        val resolverClass = mutableClassDefBy(classDefBy(RESOLVER))
        val resolveMethod = resolverClass.methods.single {
            it.name == "resolveNormal" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0].toString() == VIDEO_QUALITY &&
                it.parameterTypes[1].toString() == "Z"
        }
        val resolveImpl = requireNotNull(resolveMethod.implementation)
        require(resolveImpl.registerCount >= 5) {
            "QualityResolver.resolveNormal needs two local registers"
        }
        val original = resolveMethod.getInstruction(0)
        resolveMethod.addInstructionsWithLabels(
            0,
            """
                if-nez p2, :original_normal_quality
                sget-object v0, $VIDEO_QUALITY->AUTO:$VIDEO_QUALITY
                if-eq p1, v0, :unlock_full_hd
                sget-object v0, $VIDEO_QUALITY->HIGH:$VIDEO_QUALITY
                if-ne p1, v0, :original_normal_quality
                :unlock_full_hd
                new-instance v0, $VIDEO_SIZE
                const/16 v1, 0x780
                const/16 p2, 0x438
                invoke-direct {v0, v1, p2}, $VIDEO_SIZE-><init>(II)V
                return-object v0
            """.trimIndent(),
            ExternalLabel("original_normal_quality", original),
        )
    }
}
