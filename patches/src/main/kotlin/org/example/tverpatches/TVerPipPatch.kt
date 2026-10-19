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
    description = "Try system PiP from TVer video screen, with a Home audio playback fallback.",
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

        // Regular replay playback is hosted in MainActivity, but the currently
        // instrumented player fragments may not fire for the active video UI.
        // Sample while MainActivity has window focus so auto-enter params are
        // configured *before* the system begins the Home gesture.

        // A weak handle to the episode coordinator lets the existing MainActivity
        // polling distinguish manual PAUSED from initial BUFFERING. Do not cache
        // the controller or hold the retained ViewModel strongly.
        run {
            val coordinatorType = "Ljp/hamitv/hamiand1/feature/episode/playback/EpisodePlaybackCoordinator;"
            val coordinatorCls = mutableClassDefBy(classDefBy(coordinatorType))
            val weakRefType = "Ljava/lang/ref/WeakReference;"
            check(coordinatorCls.fields.none { it.name == "morphePipCoordinator" })
            coordinatorCls.fields.add(
                ImmutableField(
                    coordinatorType,
                    "morphePipCoordinator",
                    weakRefType,
                    AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                    null, null, null
                ).toMutable()
            )

            fun staticMethod(name: String, params: List<String>, registers: Int, instructions: String) {
                check(coordinatorCls.methods.none { it.name == name })
                coordinatorCls.methods.add(
                    ImmutableMethod(
                        coordinatorType, name,
                        params.map { ImmutableMethodParameter(it, null, null) },
                        if (name == "morpheIsEpisodePaused") "Z" else "V",
                        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                        null, null, MutableMethodImplementation(registers)
                    ).toMutable().apply { addInstructions(0, instructions.trimIndent()) }
                )
            }

            staticMethod("morpheRegisterPipCoordinator", listOf(coordinatorType), 3,
                """
                    new-instance v0, Ljava/lang/ref/WeakReference;
                    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V
                    sput-object v0, $coordinatorType->morphePipCoordinator:Ljava/lang/ref/WeakReference;
                    return-void
                """
            )
            staticMethod("morpheIsEpisodePaused", emptyList(), 3,
                """
                    sget-object v0, $coordinatorType->morphePipCoordinator:Ljava/lang/ref/WeakReference;
                    if-eqz v0, :not_paused
                    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, $coordinatorType
                    if-eqz v1, :not_paused
                    check-cast v0, $coordinatorType
                    invoke-virtual {v0}, $coordinatorType->getActiveController()Lkotlinx/coroutines/flow/StateFlow;
                    move-result-object v0
                    if-eqz v0, :not_paused
                    invoke-interface {v0}, Lkotlinx/coroutines/flow/StateFlow;->getValue()Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, Ljp/play/tverplayer/player/contract/PlayerController;
                    if-eqz v1, :not_paused
                    check-cast v0, Ljp/play/tverplayer/player/contract/PlayerController;
                    invoke-interface {v0}, Ljp/play/tverplayer/player/contract/PlayerController;->getState()Lkotlinx/coroutines/flow/StateFlow;
                    move-result-object v0
                    if-eqz v0, :not_paused
                    invoke-interface {v0}, Lkotlinx/coroutines/flow/StateFlow;->getValue()Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, Ljp/play/tverplayer/player/contract/PlaybackState;
                    if-eqz v1, :not_paused
                    check-cast v0, Ljp/play/tverplayer/player/contract/PlaybackState;
                    invoke-virtual {v0}, Ljp/play/tverplayer/player/contract/PlaybackState;->getPhase()Ljp/play/tverplayer/player/contract/PlaybackPhase;
                    move-result-object v0
                    sget-object v1, Ljp/play/tverplayer/player/contract/PlaybackPhase;->PAUSED:Ljp/play/tverplayer/player/contract/PlaybackPhase;
                    if-ne v0, v1, :not_paused
                    const/4 v0, 0x1
                    return v0
                    :not_paused
                    const/4 v0, 0x0
                    return v0
                """
            )
            val ctor = coordinatorCls.methods.single { it.name == "<init>" }
            val returns = requireNotNull(ctor.implementation).instructions.toList().indices.filter {
                ctor.getInstruction(it).opcode == Opcode.RETURN_VOID
            }
            check(returns.size == 1) { "Unexpected EpisodePlaybackCoordinator constructor returns: ${returns.size}" }
            ctor.addInstructions(
                returns.single(),
                "invoke-static/range {p0 .. p0}, $coordinatorType->morpheRegisterPipCoordinator($coordinatorType)V"
            )
        }

        fun addReplayAutoEnterPolling(activityType: String, parent: String) {
            val cls = mutableClassDefBy(classDefBy(activityType))
            val runnable = "Ljava/lang/Runnable;"
            check(runnable !in cls.interfaces) { "Activity already implements Runnable" }
            check(cls.methods.none { it.name == "run" && it.parameterTypes.isEmpty() }) {
                "Activity already defines run()"
            }
            cls.interfaces.add(runnable)

            fun addVoidMethod(name: String, params: List<String>, registers: Int, instructions: String) {
                check(cls.methods.none { it.name == name && it.parameterTypes.size == params.size }) {
                    "Duplicate replay polling method: $name"
                }
                cls.methods.add(
                    ImmutableMethod(
                        activityType,
                        name,
                        params.map { ImmutableMethodParameter(it, null, null) },
                        "V",
                        AccessFlags.PUBLIC.value,
                        null,
                        null,
                        MutableMethodImplementation(registers),
                    ).toMutable().apply { addInstructions(0, instructions.trimIndent()) }
                )
            }


            // TVer 7.2.1: EpisodePersistentPlayerHostView is used by episode
            // playback (classes12.dex); home auto-previews use HomePreviewArbiter.
            // Unlike tab_layout visibility, checking the actual episode host
            // works when the navigation bar is rendered by Compose.
            check(cls.methods.none { it.name == "morpheFindReplayView" || it.name == "morpheIsReplayVisible" })
            cls.methods.add(
                ImmutableMethod(
                    activityType,
                    "morpheFindReplayView",
                    listOf(ImmutableMethodParameter("Landroid/view/View;", null, null)),
                    "Z",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null,
                    null,
                    MutableMethodImplementation(6),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            if-eqz p0, :not_found
                            invoke-virtual {p0}, Landroid/view/View;->isShown()Z
                            move-result v0
                            if-eqz v0, :not_found
                            instance-of v0, p0, Ljp/hamitv/hamiand1/feature/episode/ui/screen/video/EpisodePersistentPlayerHostView;
                            if-eqz v0, :scan_children
                            const/4 v0, 0x1
                            return v0
                            :scan_children
                            instance-of v0, p0, Landroid/view/ViewGroup;
                            if-eqz v0, :not_found
                            check-cast p0, Landroid/view/ViewGroup;
                            invoke-virtual {p0}, Landroid/view/ViewGroup;->getChildCount()I
                            move-result v2
                            const/4 v3, 0x0
                            :loop
                            if-ge v3, v2, :not_found
                            invoke-virtual {p0, v3}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;
                            move-result-object v4
                            invoke-static {v4}, $activityType->morpheFindReplayView(Landroid/view/View;)Z
                            move-result v0
                            if-nez v0, :found
                            add-int/lit8 v3, v3, 0x1
                            goto :loop
                            :found
                            const/4 v0, 0x1
                            return v0
                            :not_found
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                }
            )
            cls.methods.add(
                ImmutableMethod(
                    activityType,
                    "morpheIsReplayVisible",
                    emptyList(),
                    "Z",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(2),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                            move-result-object v0
                            if-eqz v0, :not_found
                            invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                            move-result-object v0
                            if-eqz v0, :not_found
                            invoke-static {v0}, $activityType->morpheFindReplayView(Landroid/view/View;)Z
                            move-result v0
                            return v0
                            :not_found
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                }
            )

            addVoidMethod(
                "morphePipWindowFocusChanged", listOf("Z"), 3,
                """
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v0
                    if-eqz v0, :done
                    invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :done
                    invoke-virtual {v0, p0}, Landroid/view/View;->removeCallbacks(Ljava/lang/Runnable;)Z
                    if-eqz p1, :done
                    invoke-virtual {v0, p0}, Landroid/view/View;->post(Ljava/lang/Runnable;)Z
                    :done
                    return-void
                """,
            )

            addVoidMethod(
                "run", emptyList(), 7,
                """
                    invoke-virtual {p0}, Landroid/app/Activity;->hasWindowFocus()Z
                    move-result v0
                    if-eqz v0, :done
                    # A visible episode host is sufficient even while buffering.
                    # Home preview sessions lack this host and stay excluded.
                    invoke-virtual {p0}, $activityType->morpheIsReplayVisible()Z
                    move-result v1
                    if-eqz v1, :episode_state_ready
                    invoke-static {}, Ljp/hamitv/hamiand1/feature/episode/playback/EpisodePlaybackCoordinator;->morpheIsEpisodePaused()Z
                    move-result v1
                    if-eqz v1, :episode_can_pip
                    const-string v0, "TVerPipDebug"
                    const-string v3, "REPLAY_PAUSED: disabling automatic PiP"
                    invoke-static {v0, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    const/4 v1, 0x0
                    goto :episode_state_ready
                    :episode_can_pip
                    const/4 v1, 0x1
                    :episode_state_ready
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v2
                    if-eqz v2, :apply
                    const-string v3, "org.example.tverpatches.replay_auto_enter_enabled"
                    const/4 v4, 0x0
                    invoke-virtual {v2, v3, v4}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                    move-result v4
                    if-eq v1, v4, :schedule
                    invoke-virtual {v2, v3, v1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
                    :apply
                    new-instance v3, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v3}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v3, v1}, Landroid/app/PictureInPictureParams${'$'}Builder;->setAutoEnterEnabled(Z)Landroid/app/PictureInPictureParams${'$'}Builder;
                    move-result-object v3
                    invoke-virtual {v3}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v3
                    invoke-virtual {p0, v3}, Landroid/app/Activity;->setPictureInPictureParams(Landroid/app/PictureInPictureParams;)V
                    const-string v0, "TVerPipDebug"
                    if-eqz v1, :auto_off
                    const-string v2, "REPLAY_AUTO_ENTER: enabled before Home"
                    goto :log_auto
                    :auto_off
                    const-string v2, "REPLAY_AUTO_ENTER: disabled (not playing)"
                    :log_auto
                    invoke-static {v0, v2}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    :schedule
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v3
                    if-eqz v3, :done
                    invoke-virtual {v3}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                    move-result-object v3
                    if-eqz v3, :done
                    const-wide/16 v4, 0x1f4
                    invoke-virtual {v3, p0, v4, v5}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z
                    :done
                    return-void
                """,
            )

            val existingFocusMethod = cls.methods.firstOrNull {
                it.name == "onWindowFocusChanged" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].toString() == "Z"
            }
            if (existingFocusMethod != null) {
                existingFocusMethod.addInstructions(
                    0, "invoke-virtual {p0, p1}, $activityType->morphePipWindowFocusChanged(Z)V"
                )
            } else {
                addVoidMethod(
                    "onWindowFocusChanged", listOf("Z"), 3,
                    """
                        invoke-super {p0, p1}, $parent->onWindowFocusChanged(Z)V
                        invoke-virtual {p0, p1}, $activityType->morphePipWindowFocusChanged(Z)V
                        return-void
                    """,
                )
            }
        }

        addReplayAutoEnterPolling(
            "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;",
            "Ljp/hamitv/hamiand1/tver/ui/activities/Hilt_MainActivity;",
        )

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
                    ${if (descriptor == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") """
                    invoke-virtual {p0}, $descriptor->morpheIsReplayVisible()Z
                    move-result v0
                    if-eqz v0, :not_a_replay
                    invoke-static {}, Ljp/hamitv/hamiand1/feature/episode/playback/EpisodePlaybackCoordinator;->morpheIsEpisodePaused()Z
                    move-result v0
                    if-nez v0, :paused_leave
                    goto :pip_allowed
                    :paused_leave
                    const-string v0, "TVerPipDebug"
                    const-string v1, "REPLAY_PAUSED: block leave-hint PiP"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    goto :done
                    :not_a_replay
                    const-string v0, "TVerPipDebug"
                    const-string v1, "REPLAY_VIEW_MISSING: block leave-hint PiP"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    goto :done
                    """.trimIndent() else ""}
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v0
                    if-eqz v0, :check_audio
                    const-string v1, "$VIDEO_VISIBLE_EXTRA"
                    const/4 v2, 0x0
                    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                    move-result v2
                    if-nez v2, :pip_allowed
                    :check_audio
                    const-string v0, "audio"
                    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, Landroid/media/AudioManager;
                    if-eqz v1, :done
                    check-cast v0, Landroid/media/AudioManager;
                    invoke-virtual {v0}, Landroid/media/AudioManager;->isMusicActive()Z
                    move-result v2
                    if-eqz v2, :fallback_inactive
                    const-string v0, "TVerPipDebug"
                    const-string v1, "HOME_AUDIO_FALLBACK: active"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    goto :pip_allowed
                    :fallback_inactive
                    const-string v0, "TVerPipDebug"
                    const-string v1, "HOME_AUDIO_FALLBACK: inactive"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    goto :done
                    :pip_allowed
                    new-instance v2, Landroid/app/PictureInPictureParams${'$'}Builder;
                    invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                    invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                    move-result-object v2
                    invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
                    move-result v2
                    if-eqz v2, :leave_pip_failed
                    const-string v0, "TVerPipDebug"
                    const-string v1, "LEAVE_HINT_RESULT: true"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                    goto :done
                    :leave_pip_failed
                    const-string v0, "TVerPipDebug"
                    const-string v1, "LEAVE_HINT_RESULT: false"
                    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
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

        // Android 11+ can request PiP directly when the user moves Home.
        // Returning false keeps the system's legacy onUserLeaveHint fallback.
        fun addPictureInPictureRequestedHook(activityType: String) {
            val cls = mutableClassDefBy(classDefBy(activityType))
            check(cls.methods.none { it.name == "onPictureInPictureRequested" }) {
                "Activity already overrides onPictureInPictureRequested"
            }
            cls.methods.add(
                ImmutableMethod(
                    activityType,
                    "onPictureInPictureRequested",
                    emptyList(),
                    "Z",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(4),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            const-string v0, "TVerPipDebug"
                            const-string v1, "ON_PIP_REQUESTED: system callback"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            ${if (activityType == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") """
                            invoke-virtual {p0}, $activityType->morpheIsReplayVisible()Z
                            move-result v0
                            if-eqz v0, :request_home_blocked
                            invoke-static {}, Ljp/hamitv/hamiand1/feature/episode/playback/EpisodePlaybackCoordinator;->morpheIsEpisodePaused()Z
                            move-result v0
                            if-nez v0, :request_paused_blocked
                            goto :request_pip
                            :request_paused_blocked
                            const-string v0, "TVerPipDebug"
                            const-string v1, "REPLAY_PAUSED: block system PiP"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x0
                            return v0
                            """.trimIndent() else ""}
                            invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                            move-result-object v0
                            if-eqz v0, :check_audio
                            const-string v1, "$VIDEO_VISIBLE_EXTRA"
                            const/4 v2, 0x0
                            invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                            move-result v2
                            if-nez v2, :request_pip
                            :check_audio
                            const-string v0, "audio"
                            invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
                            move-result-object v0
                            instance-of v1, v0, Landroid/media/AudioManager;
                            if-eqz v1, :not_playing
                            check-cast v0, Landroid/media/AudioManager;
                            invoke-virtual {v0}, Landroid/media/AudioManager;->isMusicActive()Z
                            move-result v2
                            if-eqz v2, :not_playing
                            :request_pip
                            new-instance v2, Landroid/app/PictureInPictureParams${'$'}Builder;
                            invoke-direct {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;-><init>()V
                            invoke-virtual {v2}, Landroid/app/PictureInPictureParams${'$'}Builder;->build()Landroid/app/PictureInPictureParams;
                            move-result-object v2
                            invoke-virtual {p0, v2}, Landroid/app/Activity;->enterPictureInPictureMode(Landroid/app/PictureInPictureParams;)Z
                            move-result v2
                            if-eqz v2, :request_failed
                            const-string v0, "TVerPipDebug"
                            const-string v1, "PIP_REQUEST_RESULT: true"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x1
                            return v0
                            :request_failed
                            const-string v0, "TVerPipDebug"
                            const-string v1, "PIP_REQUEST_RESULT: false; allowing legacy Home fallback"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x0
                            return v0
                            ${if (activityType == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") """
                            :request_home_blocked
                            const-string v0, "TVerPipDebug"
                            const-string v1, "REPLAY_VIEW_MISSING: block system PiP"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x0
                            return v0
                            """.trimIndent() else ""}
                            :not_playing
                            const-string v0, "TVerPipDebug"
                            const-string v1, "PIP_REQUEST_NO_PLAYBACK: falling back to leave hint"
                            invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent(),
                    )
                }
            )
        }

        addPictureInPictureRequestedHook(
            "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;",
        )
        addPictureInPictureRequestedHook(
            "Ljp/hamitv/hamiand1/tver/ui/olympic/activity/OlympicVideoActivity;",
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
