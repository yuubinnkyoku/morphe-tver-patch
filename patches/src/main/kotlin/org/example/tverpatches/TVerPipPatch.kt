package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val VIDEO_VISIBLE_EXTRA = "org.example.tverpatches.player_view_visible"
private val videoFragments = listOf(
    "Ljp/play/tverplayer/ui/player/PlayerFragment;",
    "Ljp/play/tverplayer/scenes/ui/TPPlayerFragment;",
    "Ljp/hamitv/hamiand1/tver/ui/olympic/widget/OlympicLiveJapanesePlayerFragment;",
)

/** Opt the video-hosting activities in to Android's system PiP mode. */
@Suppress("unused")
val tverPipManifestPatch = resourcePatch {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1"))

    execute {
        val activityNames = setOf(
            "jp.hamitv.hamiand1.tver.ui.activities.MainActivity",
            "jp.hamitv.hamiand1.tver.ui.olympic.activity.OlympicVideoActivity",
        )
        document("AndroidManifest.xml").use { doc ->
            // Morphe's binary-XML document uses prefixed attribute names.
            // getAttributeNS() can return "" even though android:name exists.
            val elements = doc.getElementsByTagName("activity")
            val found = mutableSetOf<String>()
            val available = mutableListOf<String>()
            for (i in 0 until elements.length) {
                val element = elements.item(i) as org.w3c.dom.Element
                val rawName = element.getAttribute("android:name").ifEmpty {
                    element.getAttribute("name")
                }
                available += rawName
                val fullName = when {
                    rawName.startsWith(".") -> "jp.hamitv.hamiand1$rawName"
                    '.' !in rawName -> "jp.hamitv.hamiand1.$rawName"
                    else -> rawName
                }
                if (fullName !in activityNames) continue
                element.setAttribute("android:supportsPictureInPicture", "true")
                element.setAttribute("android:resizeableActivity", "true")
                found += fullName
            }
            check(found == activityNames) {
                "PiP activity mismatch: missing=${activityNames - found}, present=$available"
            }
        }
    }
}

/**
 * Enter Android system PiP on Home while a recognized TVer player fragment exists.
 * Does not guarantee continued playback if TVer pauses video in the background.
 */
@Suppress("unused")
val tverPipPatch = bytecodePatch(
    name = "TVer: Android system PiP (7.2.1)",
    description = "Try to enter system picture-in-picture from a TVer video screen.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1"))
    dependsOn(tverPipManifestPatch)

    execute {
        fun markFragment(descriptor: String, lifecycleName: String, visible: Boolean) {
            val cls = mutableClassDefBy(classDefBy(descriptor))
            val method = cls.methods.single { it.name == lifecycleName }
            val instructions = requireNotNull(method.implementation).instructions.toList()
            // Match the Opcode enum, not Opcode.name (which is the Smali spelling
            // "return-void", not the enum constant "RETURN_VOID").
            // Reverse order preserves original instruction indices.
            val returnIndices = instructions.indices.filter {
                instructions[it].opcode == Opcode.RETURN_VOID
            }
            check(returnIndices.isNotEmpty()) {
                "No return-void found in $descriptor->$lifecycleName; " +
                    "last opcode: ${instructions.lastOrNull()?.opcode}"
            }

            // APK inspection confirms at least one local register in onViewCreated
            // and two locals in onDestroyView for all three supported fragments.
            // Reuse incoming parameters only immediately before returning, so
            // original playback logic never observes clobbered registers.
            val snippet = if (visible) {
                """
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getActivity()Landroidx/fragment/app/FragmentActivity;
                    move-result-object v0
                    if-eqz v0, :finished
                    invoke-virtual {v0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :finished
                    const-string p1, "$VIDEO_VISIBLE_EXTRA"
                    const/4 p2, 0x1
                    invoke-virtual {v0, p1, p2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
                """.trimIndent()
            } else {
                """
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getActivity()Landroidx/fragment/app/FragmentActivity;
                    move-result-object v0
                    if-eqz v0, :finished
                    invoke-virtual {v0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :finished
                    const-string v1, "$VIDEO_VISIBLE_EXTRA"
                    const/4 p0, 0x0
                    invoke-virtual {v0, v1, p0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
                """.trimIndent()
            }
            returnIndices.asReversed().forEach { index ->
                // Branch target is the original return, not newly inserted code.
                val originalReturn = method.getInstruction(index)
                method.addInstructionsWithLabels(
                    index,
                    snippet,
                    ExternalLabel("finished", originalReturn),
                )
            }
        }

        videoFragments.forEach { descriptor ->
            markFragment(descriptor, "onViewCreated", true)
            markFragment(descriptor, "onDestroyView", false)
        }

        fun addLeaveHook(descriptor: String, parent: String) {
            val cls = mutableClassDefBy(classDefBy(descriptor))
            check(cls.methods.none { it.name == "onUserLeaveHint" }) {
                "Activity already overrides onUserLeaveHint"
            }
            val method = ImmutableMethod(
                descriptor,
                "onUserLeaveHint",
                emptyList(),
                "V",
                AccessFlags.PROTECTED.value,
                null,
                null,
                MutableMethodImplementation(4),
            ).toMutable()
            cls.methods.add(method)
            method.addInstructions(0, "return-void")
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-super {p0}, $parent->onUserLeaveHint()V
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :done
                    const-string v1, "$VIDEO_VISIBLE_EXTRA"
                    const/4 v2, 0x0
                    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                    move-result v2
                    if-eqz v2, :done
                    invoke-virtual {p0}, Landroid/app/Activity;->enterPictureInPictureMode()Z
                    move-result v2
                """.trimIndent(),
                ExternalLabel("done", method.getInstruction(0)),
            )
        }

        addLeaveHook(
            "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;",
            "Ljp/hamitv/hamiand1/tver/ui/activities/Hilt_MainActivity;",
        )
        addLeaveHook(
            "Ljp/hamitv/hamiand1/tver/ui/olympic/activity/OlympicVideoActivity;",
            "Ljp/hamitv/hamiand1/tver/ui/olympic/activity/Hilt_OlympicVideoActivity;",
        )
    }
}
