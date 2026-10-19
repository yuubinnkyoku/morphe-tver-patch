package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Experimental TVer 7.2.1 playback-ad suppression.
 * Intentionally does not block arbitrary network requests or touch media streams.
 */
@Suppress("unused")
val tverAdsPatch = bytecodePatch(
    name = "TVer: suppress player ads (7.2.1)",
    description = "Experimentally suppress selected player ad requests and hooks; full ad blocking is not guaranteed.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1"))

    execute {
        fun patch(className: String, name: String, smali: String) {
            val cls = mutableClassDefBy(classDefBy(className))
            val method = cls.methods.single { it.name == name }
            requireNotNull(method.implementation)
            method.addInstructions(0, smali.trimIndent())
        }

        patch(
            "Ljp/play/tverplayer/player/ads/DefaultAdScheduler;",
            "shouldRequestPreRoll",
            """
                const/4 v0, 0x0
                return v0
            """,
        )
        patch(
            "Ljp/play/tverplayer/player/contract/AdConfig;",
            "getSkipPreRoll",
            """
                const/4 v0, 0x1
                return v0
            """,
        )
        patch(
            "Ljp/hamitv/hamiand1/tver/player/model/PlaybackPolicy;",
            "getSkipPreRoll",
            """
                const/4 v0, 0x1
                return v0
            """,
        )
        patch(
            "Ljp/play/tverplayer/player/ads/AdsPlugin;",
            "onPlayerCreated",
            "return-void",
        )
        patch(
            "Ljp/play/tverplayer/player/ads/DefaultCompanionAdPresenter;",
            "present",
            "return-void",
        )
    }
}
