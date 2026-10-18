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
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

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
    description = "Try system PiP from a TVer video screen, with Home-key playback fallback.",
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
                // v0 is a verified local in all onViewCreated methods.
                // Temporarily reuse p1/p2/p0 only immediately before a return.
                // This enables Android 12+ automatic PiP on Home gestures.
                """
                    const-string v0, "TVerPipDebug"
                    const-string p1, "PLAYER_VISIBLE: $descriptor"
                    invoke-static {v0, p1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getActivity()Landroidx/fragment/app/FragmentActivity;
                    move-result-object v0
                    if-eqz v0, :finished
                    move-object p1, v0
                    invoke-virtual {p1}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :finished
                    const-string p2, "$VIDEO_VISIBLE_EXTRA"
                    const/4 p0, 0x1
                    invoke-virtual {v0, p2, p0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
                    new-instance v0, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v0}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    const/4 p0, 0x1
                    invoke-virtual {v0, p0}, Landroid/app/PictureInPictureParams${'$'}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${'$'}Builder;
                    move-result-object v0
                    invoke-virtual {v0}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v0
                    invoke-virtual {p1, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
                """.trimIndent()
            } else {
                // v0/v1 are both locals in all onDestroyView methods.
                // p0 is free to reuse because the method is about to return.
                """
                    const-string v0, "TVerPipDebug"
                    const-string v1, "PLAYER_CLOSED: $descriptor"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getActivity()Landroidx/fragment/app/FragmentActivity;
                    move-result-object v0
                    if-eqz v0, :finished
                    move-object p0, v0
                    new-instance v0, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v0}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    const/4 v1, 0x0
                    invoke-virtual {v0, v1}, Landroid/app/PictureInPictureParams${'$'}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${'$'}Builder;
                    move-result-object v0
                    invoke-virtual {v0}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v0
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
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
                    const-string v0, "TVerPipDebug"
                    const-string v1, "ON_USER_LEAVE_HINT: received"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    invoke-super {p0}, $parent->onUserLeaveHint()V
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :done
                    const-string v1, "$VIDEO_VISIBLE_EXTRA"
                    const/4 v2, 0x0
                    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                    move-result v2
                    if-nez v2, :pip_allowed
                    # Player fragment visibility may never be set by the
                    # Compose playback UI. Fall back to active audio on Home.
                    const-string v0, "audio"
                    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, Landroid/media/AudioManager;
                    if-eqz v1, :done
                    check-cast v0, Landroid/media/AudioManager;
                    invoke-virtual {v0}, Landroid/media/AudioManager;->isMusicActive()Z
                    move-result v2
                    const-string v0, "TVerPipDebug"
                    const-string v1, "HOME_AUDIO_FALLBACK: music active = "
                    invoke-static {v2}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
                    move-result-object v3
                    invoke-virtual {v1, v3}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    :pip_allowed
                    if-eqz v2, :done
                    new-instance v2, Landroid/app/PictureInPictureParams${'
                    invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v2
                    invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
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

        // Diagnostic overlay intentionally appears on the entire activity,
        // rather than depending on a player fragment lifecycle callback.
        // This distinguishes a missing player hook from a rejected PiP request.
        fun addDiagnosticOverlay(activityType: String) {
            val cls = mutableClassDefBy(classDefBy(activityType))
            val listener = "Landroid/view/View${'$'}OnClickListener;"
            check(listener !in cls.interfaces) { "Activity already implements View.OnClickListener" }
            cls.interfaces.add(listener)

            fun addMethod(
                name: String,
                parameters: List<String>,
                registerCount: Int,
                smali: String,
            ) {
                check(cls.methods.none { it.name == name && it.parameterTypes.size == parameters.size }) {
                    "Diagnostic method already exists: $name"
                }
                cls.methods.add(
                    ImmutableMethod(
                        activityType,
                        name,
                        parameters.map { ImmutableMethodParameter(it, null, null) },
                        "V",
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(registerCount),
                    ).toMutable().apply {
                        addInstructions(0, smali.trimIndent())
                    }
                )
            }

            addMethod(
                "onClick", listOf("Landroid/view/View;"), 6,
                """
                    const-string v0, "TVerPipDebug"
                    const-string v1, "BUTTON_CLICK: requesting system PiP"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    new-instance v2, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v2
                    invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
                    move-result v2
                    const-string v1, "BUTTON_RESULT: enterPictureInPictureMode returned "
                    invoke-static {v2}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    return-void
                """,
            )

            addMethod(
                "morpheSetPipDebugVisible", listOf("Z"), 12,
                """
                    const v0, 0x1020002
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :debug_end
                    const-string v1, "morphe.tver.pip.debug.button"
                    invoke-virtual {v0, v1}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;
                    move-result-object v2
                    if-nez v2, :debug_existing
                    if-eqz p1, :debug_end
                    new-instance v2, Landroid/widget/Button;
                    invoke-direct {v2, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
                    const-string v3, "PiP TEST"
                    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    invoke-virtual {v2, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    invoke-virtual {v2, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                    new-instance v4, Landroid/widget/FrameLayout${'$'}LayoutParams;
                    const/16 v5, -0x2
                    const/16 v6, 0x35
                    invoke-direct {v4, v5, v5, v6}, Landroid/widget/FrameLayout${'$'}LayoutParams;-><init>(III)V
                    const/16 v6, 0x10
                    const/16 v7, 0x48
                    invoke-virtual {v4, v6, v7, v6, v6}, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->setMargins(IIII)V
                    check-cast v0, Landroid/view/ViewGroup;
                    invoke-virtual {v0, v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup${'$'}LayoutParams;)V
                    const-string v3, "TVerPipDebug"
                    const-string v4, "BUTTON_CREATED: visible overlay added"
                    invoke-static {v3, v4}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    :debug_existing
                    if-eqz p1, :debug_hide
                    const/4 v3, 0x0
                    goto :debug_vis
                    :debug_hide
                    const/16 v3, 0x8
                    :debug_vis
                    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V
                    :debug_end
                    return-void
                """,
            )

            addMethod(
                "onPictureInPictureModeChanged",
                listOf("Z", "Landroid/content/res/Configuration;"),
                6,
                """
                    invoke-super {p0, p1, p2}, $activityType->onPictureInPictureModeChanged(ZLandroid/content/res/Configuration;)V
                    const-string v0, "TVerPipDebug"
                    const-string v1, "PIP_MODE_CHANGED: "
                    invoke-static {p1}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    return-void
                """.replace(
                    "$activityType->onPictureInPictureModeChanged",
                    (if (activityType.contains("OlympicVideoActivity")) {
                        "Ljp/hamitv/hamiand1/tver/ui/olympic/activity/Hilt_OlympicVideoActivity;"
                    } else {
                        "Ljp/hamitv/hamiand1/tver/ui/activities/Hilt_MainActivity;"
                    }) + "->onPictureInPictureModeChanged"
                ),
            )

            val onCreate = cls.methods.single {
                it.name == "onCreate" && it.parameterTypes.size == 1
            }
            val returns = requireNotNull(onCreate.implementation).instructions
                .mapIndexedNotNull { i, instruction ->
                    i.takeIf { instruction.opcode == Opcode.RETURN_VOID }
                }
            check(returns.isNotEmpty()) { "Could not instrument Activity.onCreate" }
            returns.asReversed().forEach { at ->
                onCreate.addInstructions(
                    at,
                    """
                        const/4 v0, 0x1
                        invoke-virtual {p0, v0}, $activityType->morpheSetPipDebugVisible(Z)V
                    """.trimIndent(),
                )
            }
        }

        addDiagnosticOverlay("Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;")
        addDiagnosticOverlay("Ljp/hamitv/hamiand1/tver/ui/olympic/activity/OlympicVideoActivity;")

    }
}
}Builder;
                    invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v2
                    invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
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

        // Diagnostic overlay intentionally appears on the entire activity,
        // rather than depending on a player fragment lifecycle callback.
        // This distinguishes a missing player hook from a rejected PiP request.
        fun addDiagnosticOverlay(activityType: String) {
            val cls = mutableClassDefBy(classDefBy(activityType))
            val listener = "Landroid/view/View${'$'}OnClickListener;"
            check(listener !in cls.interfaces) { "Activity already implements View.OnClickListener" }
            cls.interfaces.add(listener)

            fun addMethod(
                name: String,
                parameters: List<String>,
                registerCount: Int,
                smali: String,
            ) {
                check(cls.methods.none { it.name == name && it.parameterTypes.size == parameters.size }) {
                    "Diagnostic method already exists: $name"
                }
                cls.methods.add(
                    ImmutableMethod(
                        activityType,
                        name,
                        parameters.map { ImmutableMethodParameter(it, null, null) },
                        "V",
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(registerCount),
                    ).toMutable().apply {
                        addInstructions(0, smali.trimIndent())
                    }
                )
            }

            addMethod(
                "onClick", listOf("Landroid/view/View;"), 6,
                """
                    const-string v0, "TVerPipDebug"
                    const-string v1, "BUTTON_CLICK: requesting system PiP"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    new-instance v2, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v2
                    invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
                    move-result v2
                    const-string v1, "BUTTON_RESULT: enterPictureInPictureMode returned "
                    invoke-static {v2}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    return-void
                """,
            )

            addMethod(
                "morpheSetPipDebugVisible", listOf("Z"), 12,
                """
                    const v0, 0x1020002
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :debug_end
                    const-string v1, "morphe.tver.pip.debug.button"
                    invoke-virtual {v0, v1}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;
                    move-result-object v2
                    if-nez v2, :debug_existing
                    if-eqz p1, :debug_end
                    new-instance v2, Landroid/widget/Button;
                    invoke-direct {v2, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
                    const-string v3, "PiP TEST"
                    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    invoke-virtual {v2, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    invoke-virtual {v2, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                    new-instance v4, Landroid/widget/FrameLayout${'$'}LayoutParams;
                    const/16 v5, -0x2
                    const/16 v6, 0x35
                    invoke-direct {v4, v5, v5, v6}, Landroid/widget/FrameLayout${'$'}LayoutParams;-><init>(III)V
                    const/16 v6, 0x10
                    const/16 v7, 0x48
                    invoke-virtual {v4, v6, v7, v6, v6}, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->setMargins(IIII)V
                    check-cast v0, Landroid/view/ViewGroup;
                    invoke-virtual {v0, v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup${'$'}LayoutParams;)V
                    const-string v3, "TVerPipDebug"
                    const-string v4, "BUTTON_CREATED: visible overlay added"
                    invoke-static {v3, v4}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    :debug_existing
                    if-eqz p1, :debug_hide
                    const/4 v3, 0x0
                    goto :debug_vis
                    :debug_hide
                    const/16 v3, 0x8
                    :debug_vis
                    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V
                    :debug_end
                    return-void
                """,
            )

            addMethod(
                "onPictureInPictureModeChanged",
                listOf("Z", "Landroid/content/res/Configuration;"),
                6,
                """
                    invoke-super {p0, p1, p2}, $activityType->onPictureInPictureModeChanged(ZLandroid/content/res/Configuration;)V
                    const-string v0, "TVerPipDebug"
                    const-string v1, "PIP_MODE_CHANGED: "
                    invoke-static {p1}, Ljava/lang/String;->valueOf(Z)Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v1, v2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    return-void
                """.replace(
                    "$activityType->onPictureInPictureModeChanged",
                    (if (activityType.contains("OlympicVideoActivity")) {
                        "Ljp/hamitv/hamiand1/tver/ui/olympic/activity/Hilt_OlympicVideoActivity;"
                    } else {
                        "Ljp/hamitv/hamiand1/tver/ui/activities/Hilt_MainActivity;"
                    }) + "->onPictureInPictureModeChanged"
                ),
            )

            val onCreate = cls.methods.single {
                it.name == "onCreate" && it.parameterTypes.size == 1
            }
            val returns = requireNotNull(onCreate.implementation).instructions
                .mapIndexedNotNull { i, instruction ->
                    i.takeIf { instruction.opcode == Opcode.RETURN_VOID }
                }
            check(returns.isNotEmpty()) { "Could not instrument Activity.onCreate" }
            returns.asReversed().forEach { at ->
                onCreate.addInstructions(
                    at,
                    """
                        const/4 v0, 0x1
                        invoke-virtual {p0, v0}, $activityType->morpheSetPipDebugVisible(Z)V
                    """.trimIndent(),
                )
            }
        }

        addDiagnosticOverlay("Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;")
        addDiagnosticOverlay("Ljp/hamitv/hamiand1/tver/ui/olympic/activity/OlympicVideoActivity;")

    }
}
