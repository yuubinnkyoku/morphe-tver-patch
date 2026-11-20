package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val PICKER =
    "Lorg/example/tverpatches/extension/LiveQualityPicker;"
private const val LIVE_VIEW =
    "Ljp/hamitv/hamiand1/tver/legacy/player/live/LivePlaybackControllerView;"

/**
 * Replaces the legacy live player's fixed quality sheet with the actual
 * per-stream rendition list.
 *
 * LivePlaybackControllerView.onClickQualityFrame() is the funnel for the
 * LIVE and DVR quality buttons; stock opens a fixed 4-choice sheet
 * (AUTO/HIGH/MIDDLE/LOW) that only nudges a bitrate ceiling and shows the
 * same menu regardless of what the server provides. The injected bridge
 * enumerates the real renditions from the Streaks SDK
 * (STRPlayBackController.getSwitchableTracks(), the same model classes
 * the VOD picker uses) and pins the chosen track with
 * STRSelectionOverride + updateTrackSelection().
 *
 * Falls back to TVer's original sheet when the bridge returns false
 * (player not attached, no host Activity, reflection failure).
 */
@Suppress("unused")
val tverLiveQualityPickerPatch = bytecodePatch(
    name = "TVer: live resolution quality picker (7.2.1/7.4.1)",
    description = "Choose live/DVR video resolutions from the actual rendition list (Auto, 1080p, 720p, etc.) instead of the fixed 4-choice quality sheet.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))
    extendWith("extensions/extension.mpe")

    execute {
        val cls = mutableClassDefBy(classDefBy(LIVE_VIEW))
        val qualityMethod = cls.methods.firstOrNull {
            it.name == "onClickQualityFrame" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == "Landroid/view/View;"
        } ?: return@execute // Older builds keep the stock quality sheet.
        val impl = requireNotNull(qualityMethod.implementation)
        require(impl.registerCount >= 2) {
            "onClickQualityFrame must have one free local register"
        }
        val first = qualityMethod.getInstruction(0)
        qualityMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0}, $PICKER->open(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :stock_quality_menu
                return-void
            """.trimIndent(),
            ExternalLabel("stock_quality_menu", first),
        )
    }
}
