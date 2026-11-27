package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private const val HOLD =
    "Lorg/example/tverpatches/extension/LiveBackgroundHold;"

/**
 * Keep live (realtime / DVR) playback running only when the app itself
 * leaves the foreground, and restore stock behavior for in-app navigation.
 *
 * LivePlaybackControllerView pauses the Streaks player and usually releases
 * it via toBackground(), which every background path funnels through:
 * onPause/onStop (lifecycle), onShowModal and the screen-recording
 * suspend fallback. Prepending a bridge call turns toBackground() into a
 * no-op only when LiveBackgroundHold.shouldHold(view) reports that the
 * whole app is going to the background:
 *
 *  - No Activity of this process is resumed (tracked via
 *    Application.ActivityLifecycleCallbacks.onActivityPrePaused), or
 *  - the Activity is entering picture-in-picture.
 *
 * Effects:
 *  - Home gesture / recents: a backgrounded live stream keeps playing
 *    audio, and system PiP (TVerPipPatch gates auto-enter on
 *    morpheIsLiveVisible()) receives the still-playing video.
 *  - In-app navigation (live tab -> episode detail, zapping cards, ...):
 *    the Activity stays resumed, so stock pause + release runs again.
 *    This restores TVer's original single-stream behavior that the
 *    previous unconditional return-void build broke (several live
 *    streams playing at once / "matryoshka" layers of live surfaces).
 *  - Modal sheets and screen-recording suspension also keep stock
 *    behavior because the Activity stays resumed in those flows.
 *
 * Notes:
 *  - Audio focus handling is untouched; another app taking focus still
 *    pauses the stream through LiveAudioFocusGate.
 *  - TVer ships no media foreground service. Android may freeze the cached
 *    process after a while (Android 12+ cached-app freezer), which ends
 *    background audio until the app is foregrounded again. PiP keeps the
 *    process alive because the activity stays visible.
 *  - destroy()/stopVideo(true) on teardown is NOT stubbed, so navigation
 *    away from the live screen still releases the player normally.
 */
@Suppress("unused")
val tverLiveBackgroundPatch = bytecodePatch(
    name = "TVer: keep live playback in background (7.2.1/7.4.1)",
    description = "Live/DVR streams keep playing (audio, and video via PiP) when the app itself is backgrounded, e.g. Home gesture, while in-app navigation and modal sheets pause/release them as in the stock app.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))
    extendWith("extensions/extension.mpe")

    execute {
        val liveController = "Ljp/hamitv/hamiand1/tver/legacy/player/live/LivePlaybackControllerView;"
        val cls = mutableClassDefBy(classDefBy(liveController))
        val toBackground = cls.methods.single { it.name == "toBackground" }
        val impl = requireNotNull(toBackground.implementation)
        require(impl.registerCount >= 2) {
            "toBackground must have one free local register"
        }
        val first = toBackground.getInstruction(0)
        toBackground.addInstructionsWithLabels(
            0,
            """
                invoke-static {p0}, $HOLD->shouldHold(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :stock_background
                const/4 v0, 0x1
                iput-boolean v0, p0, Ljp/hamitv/hamiand1/tver/legacy/player/live/LivePlaybackControllerView;->isBackgrounded:Z
                return-void
            """.trimIndent(),
            ExternalLabel("stock_background", first),
        )
        // Marking isBackgrounded mirrors what the stock toBackground body
        // does first. LivePlaybackControllerView.onStop() early-returns when
        // the flag is set; without it, a Home/PiP transition that skips the
        // stock body falls through to onStop's direct pause() (it only
        // runs when toBackground did not), which stops the held stream a
        // moment after the pause-side hold. onResume() clears the flag.
    }
}
