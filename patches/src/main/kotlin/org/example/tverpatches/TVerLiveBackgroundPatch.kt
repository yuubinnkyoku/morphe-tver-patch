package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Keep live (realtime / DVR) playback running when the app goes background.
 *
 * LivePlaybackControllerView pauses the Streaks player and usually releases
 * it via toBackground(), which every background path funnels through:
 * onPause/onStop (lifecycle), onShowModal and the screen-recording
 * suspend fallback. Prepending return-void turns those into no-ops, so a
 * backgrounded live stream keeps playing audio, and a Home gesture can
 * hand the still-playing video over to system PiP (TVerPipPatch gates
 * auto-enter on morpheIsLiveVisible()).
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
    description = "Live/DVR streams keep playing (audio, and video via PiP) when the app is backgrounded instead of pausing. Paused-on-background bookkeeping and screen-recording suspension for live are disabled as a side effect.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))

    execute {
        val liveController = "Ljp/hamitv/hamiand1/tver/legacy/player/live/LivePlaybackControllerView;"
        val cls = mutableClassDefBy(classDefBy(liveController))
        val toBackground = cls.methods.single { it.name == "toBackground" }
        requireNotNull(toBackground.implementation)
        toBackground.addInstructions(0, "return-void")
    }
}
