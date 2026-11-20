package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Suppresses TVer's splash-screen forced-version gate.
 *
 * TVer compares its own package version against `version.min.android` /
 * `version.latest.android` of the CDN start-config response in
 * SplashFragment.onCdnStartConfigUrlResponse, blocks startup behind an
 * uncancellable store-redirect dialog when the app is older, and gates both
 * branches behind ForceUpdateUtil.shouldForceUpdate (SDK_INT >= 28).
 *
 * This patch makes that single gate always return false, so the
 * forced-update dialog and the newest-version recommendation never appear
 * and splash boot continues into the normal startup flow. It does not
 * touch any network request, does not spoof a version to servers, and
 * leaves the separate maintenance screen untouched.
 */
@Suppress("unused")
val tverForceUpdatePatch = bytecodePatch(
    name = "TVer: skip forced version gate (7.2.1/7.4.1)",
    description = "Experimental: suppress the splash version gate dialogs (forced store redirect and newest-version recommendation) by making ForceUpdateUtil.shouldForceUpdate return false. Content APIs are not modified.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))

    execute {
        val cls = mutableClassDefBy(classDefBy("Ljp/hamitv/hamiand1/tver/util/ForceUpdateUtil;"))
        val method = cls.methods.single { it.name == "shouldForceUpdate" }
        requireNotNull(method.implementation)
        method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
    }
}
