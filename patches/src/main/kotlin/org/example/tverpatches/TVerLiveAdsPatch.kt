package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Live (realtime / DVR) ad suppression for TVer's legacy live controller.
 *
 * Live playback is driven by LivePlaybackControllerView on top of the
 * Streaks player. TVer inserts two client-side ad surfaces there:
 *
 *  - CSAI pre-roll: setMedia builds the AdParams with
 *    skipPreRoll(!isPlayFromBeginning || adRollState.getPreRollPlayed()),
 *    so a "play from the beginning" start always queues a pre-roll ad.
 *    Forcing AdRollState.getPreRollPlayed() to true makes that flag always
 *    true, which selects skipPreRoll(true) on every load. The flag already
 *    means "pre-roll has been handled", and stock code itself sets it in
 *    onPreRollSkipped()/onAdRollStarted(PRE_ROLL), so no new state appears.
 *
 *  - Companion ads: onStartAd forwards an ad to the listener only when
 *    LiveCompanionAD.isCompanionAdAvailable() accepts it. Forcing that to
 *    false keeps the stock "companion ad unavailable" branch, which calls
 *    resetCompanionAd and notifies the listener with a null ad, so the
 *    Compose companion-ad overlay never receives creative to render.
 *
 * SSAI mid-rolls are stitched into the live stream itself and are NOT
 * touched: removing them would require editing the manifest selector and
 * risks breaking playback (see handleSsaiSessionInvalid auto-reload).
 */
@Suppress("unused")
val tverLiveAdsPatch = bytecodePatch(
    name = "TVer: live pre-roll and companion ad suppression (7.2.1/7.4.1)",
    description = "Skip the client-side pre-roll ad on live/DVR starts and never hand companion ads to the live overlay. SSAI mid-rolls stitched into the stream are out of scope.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))

    execute {
        fun patch(className: String, name: String, smali: String) {
            val cls = mutableClassDefBy(classDefBy(className))
            val method = cls.methods.single { it.name == name }
            requireNotNull(method.implementation)
            method.addInstructions(0, smali.trimIndent())
        }

        // Always treat the pre-roll as already handled -> skipPreRoll(true).
        patch(
            "Ljp/hamitv/hamiand1/tver/legacy/player/live/AdRollState;",
            "getPreRollPlayed",
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        // Never present a companion ad to the live detail overlay.
        patch(
            "Ljp/hamitv/hamiand1/tver/legacy/player/live/LiveCompanionAD;",
            "isCompanionAdAvailable",
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
