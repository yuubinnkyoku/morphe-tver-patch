package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Experimental offline-only transparent WebView comments synchronized with TVer.
 * The Android extension has no compile-time dependency on TVer's classes.
 */
@Suppress("unused")
val tverNicoCommentOverlayPatch = bytecodePatch(
    name = "TVer: Nico comment overlay prototype (7.2.1)",
    description = "Experimental transparent WebView with offline test comments and episode playback synchronization.",
    default = false,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1"))
    // The PiP patch supplies TVer's in-app Morphe settings screen.
    dependsOn(tverPipPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val surfaceType = "Ljp/hamitv/hamiand1/feature/episode/ui/screen/video/EpisodePersistentPlayerSurfaceKt;"
        val holderType = "Ljp/hamitv/hamiand1/feature/episode/ui/screen/video/EpisodeVideoLayerHolder;"
        val hostType = "Ljp/hamitv/hamiand1/feature/episode/ui/screen/video/EpisodePersistentPlayerHostView;"
        val bridgeType = "Lorg/example/tverpatches/extension/NicoOverlayBridge;"

        val cls = mutableClassDefBy(classDefBy(surfaceType))
        val update = cls.methods.single {
            it.name == "EpisodePersistentPlayerSurface\$lambda\$5\$0" &&
                it.parameterTypes.map(CharSequence::toString) ==
                    listOf(holderType, "Landroidx/compose/runtime/State;", hostType)
        }
        check(update.implementation?.registerCount == 4) {
            "Unexpected TVer update registers; refusing to inject"
        }
        // Index 2: after host null-check. p0 and p2 still hold original values;
        // later in this method p0 is overwritten, so do NOT hook the method end.
        update.addInstructions(2, """
            invoke-virtual {p0}, $holderType->getController()Ljp/play/tverplayer/player/contract/PlayerController;
            move-result-object v0
            invoke-interface {v0}, Ljp/play/tverplayer/player/contract/PlayerController;->getState()Lkotlinx/coroutines/flow/StateFlow;
            move-result-object v0
            invoke-static {p2, v0}, $bridgeType->bind(Landroid/view/ViewGroup;Ljava/lang/Object;)V
        """.trimIndent())

        val release = cls.methods.single {
            it.name == "EpisodePersistentPlayerSurface\$lambda\$4\$0" &&
                it.parameterTypes.map(CharSequence::toString) == listOf(hostType)
        }
        check(release.implementation != null)
        // The Compose AndroidView onRelease path; insert before setLayer(null).
        release.addInstructions(2,
            "invoke-static {p0}, $bridgeType->unbind(Landroid/view/ViewGroup;)V")

        // Enhance the existing settings LinearLayout (v7) before the original
        // AlertDialog.Builder.setView() call. This patch runs after PiP because
        // it depends on that patch's generated morpheShowSettingsDialog method.
        val activityType = "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;"
        val activity = mutableClassDefBy(classDefBy(activityType))
        val settings = activity.methods.single {
            it.name == "morpheShowSettingsDialog" && it.parameterTypes.isEmpty()
        }
        check(settings.implementation?.registerCount == 9) {
            "Unexpected Morphe settings dialog registers"
        }
        val insertion = settings.implementation!!.instructions.indexOfFirst { instruction ->
            val method = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            method?.definingClass == "Landroid/app/AlertDialog\$Builder;" &&
                method.name == "setView"
        }
        check(insertion >= 0) { "Morphe settings setView call not found" }
        settings.addInstructions(insertion, """
            invoke-static {p0, v7}, $bridgeType->enhanceSettings(Landroid/app/Activity;Landroid/widget/LinearLayout;)Landroid/view/View;
            move-result-object v7
        """.trimIndent())
    }
}
