package org.example.tverpatches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable as toMutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
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
    description = "Automatically enter system PiP for replay episodes; excludes paused episodes and home previews.",
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
                ).toMutableField()
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


            // Match an active EpisodeKey before the video host is created.
            check(cls.methods.none { it.name == "morpheFindEpisodeNavigation" })
            cls.methods.add(
                ImmutableMethod(
                    activityType,
                    "morpheFindEpisodeNavigation",
                    listOf(ImmutableMethodParameter("Landroidx/fragment/app/FragmentManager;", null, null)),
                    "Z",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(7),
                ).toMutable().apply {
                    addInstructions(0, """
                        if-eqz p0, :not_found
                        invoke-virtual {p0}, Landroidx/fragment/app/FragmentManager;->getFragments()Ljava/util/List;
                        move-result-object v0
                        invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                        move-result-object v1
                        :loop
                        invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                        move-result v2
                        if-eqz v2, :not_found
                        invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                        move-result-object v2
                        instance-of v3, v2, Landroidx/fragment/app/Fragment;
                        if-eqz v3, :loop
                        check-cast v2, Landroidx/fragment/app/Fragment;
                        invoke-virtual {v2}, Landroidx/fragment/app/Fragment;->isResumed()Z
                        move-result v3
                        if-eqz v3, :loop
                        instance-of v3, v2, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;
                        if-eqz v3, :children
                        move-object v3, v2
                        check-cast v3, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;->getNav3Controller()Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController;
                        move-result-object v3
                        if-eqz v3, :children
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController;->topEntry()Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController${'$'}Entry;
                        move-result-object v3
                        if-eqz v3, :children
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController${'$'}Entry;->getDestinationKey()Landroidx/navigation3/runtime/NavKey;
                        move-result-object v3
                        instance-of v3, v3, Ljp/hamitv/hamiand1/tver/core/navigation/EpisodeKey;
                        if-nez v3, :found
                        :children
                        invoke-virtual {v2}, Landroidx/fragment/app/Fragment;->getChildFragmentManager()Landroidx/fragment/app/FragmentManager;
                        move-result-object v3
                        invoke-static {v3}, $activityType->morpheFindEpisodeNavigation(Landroidx/fragment/app/FragmentManager;)Z
                        move-result v3
                        if-nez v3, :found
                        goto :loop
                        :found
                        const/4 v0, 0x1
                        return v0
                        :not_found
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                }
            )

            // Identify settings top only, not nested settings pages.
            check(cls.methods.none { it.name == "morpheFindSettingsNavigation" })
            cls.methods.add(
                ImmutableMethod(
                    activityType,
                    "morpheFindSettingsNavigation",
                    listOf(ImmutableMethodParameter("Landroidx/fragment/app/FragmentManager;", null, null)),
                    "Z",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(7),
                ).toMutable().apply {
                    addInstructions(0, """
                        if-eqz p0, :not_found
                        invoke-virtual {p0}, Landroidx/fragment/app/FragmentManager;->getFragments()Ljava/util/List;
                        move-result-object v0
                        invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
                        move-result-object v1
                        :loop
                        invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
                        move-result v2
                        if-eqz v2, :not_found
                        invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                        move-result-object v2
                        instance-of v3, v2, Landroidx/fragment/app/Fragment;
                        if-eqz v3, :loop
                        check-cast v2, Landroidx/fragment/app/Fragment;
                        invoke-virtual {v2}, Landroidx/fragment/app/Fragment;->isResumed()Z
                        move-result v3
                        if-eqz v3, :loop
                        instance-of v3, v2, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;
                        if-eqz v3, :children
                        move-object v3, v2
                        check-cast v3, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/BaseFragment;->getNav3Controller()Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController;
                        move-result-object v3
                        if-eqz v3, :children
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController;->topEntry()Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController${'$'}Entry;
                        move-result-object v3
                        if-eqz v3, :children
                        invoke-virtual {v3}, Ljp/hamitv/hamiand1/tver/core/common/ui/fragment/container/nav3/Nav3StackController${'$'}Entry;->getDestinationKey()Landroidx/navigation3/runtime/NavKey;
                        move-result-object v3
                        instance-of v3, v3, Ljp/hamitv/hamiand1/tver/core/navigation/SettingKey;
                        if-nez v3, :found
                        :children
                        invoke-virtual {v2}, Landroidx/fragment/app/Fragment;->getChildFragmentManager()Landroidx/fragment/app/FragmentManager;
                        move-result-object v3
                        invoke-static {v3}, $activityType->morpheFindSettingsNavigation(Landroidx/fragment/app/FragmentManager;)Z
                        move-result v3
                        if-nez v3, :found
                        goto :loop
                        :found
                        const/4 v0, 0x1
                        return v0
                        :not_found
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                }
            )

            check(cls.methods.none { it.name == "morpheIsSettingsTop" })
            cls.methods.add(ImmutableMethod(
                activityType, "morpheIsSettingsTop", emptyList(), "Z",
                AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(2)
            ).toMutable().apply { addInstructions(0, """
                invoke-virtual {p0}, Landroidx/fragment/app/FragmentActivity;->getSupportFragmentManager()Landroidx/fragment/app/FragmentManager;
                move-result-object v0
                invoke-static {v0}, $activityType->morpheFindSettingsNavigation(Landroidx/fragment/app/FragmentManager;)Z
                move-result v0
                return v0
            """.trimIndent()) })

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
                            invoke-virtual {p0}, Landroidx/fragment/app/FragmentActivity;->getSupportFragmentManager()Landroidx/fragment/app/FragmentManager;
                            move-result-object v0
                            invoke-static {v0}, $activityType->morpheFindEpisodeNavigation(Landroidx/fragment/app/FragmentManager;)Z
                            move-result v0
                            if-nez v0, :found
                            invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                            move-result-object v0
                            if-eqz v0, :not_found
                            invoke-virtual {v0}, Landroid/view/Window;->getDecorView()Landroid/view/View;
                            move-result-object v0
                            if-eqz v0, :not_found
                            invoke-static {v0}, $activityType->morpheFindReplayView(Landroid/view/View;)Z
                            move-result v0
                            return v0
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
                    invoke-virtual {p0}, $activityType->morpheIsSettingsTop()Z
                    move-result v0
                    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;
                    move-result-object v1
                    if-eqz v1, :show_settings_entry
                    const-string v2, "morphe.settings.route.enabled"
                    const/4 v3, 0x0
                    invoke-virtual {v1, v2, v3}, Landroid/content/Intent;->getBooleanExtra(Ljava/lang/String;Z)Z
                    move-result v3
                    if-eq v0, v3, :settings_checked
                    invoke-virtual {v1, v2, v0}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Z)Landroid/content/Intent;
                    :show_settings_entry
                    invoke-virtual {p0, v0}, $activityType->morpheSetSettingsEntryVisible(Z)V
                    :settings_checked
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
                    const-wide/16 v4, 0x64
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
            if (activityType == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") {
                val callback = "Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;"
                check(callback !in cls.interfaces)
                cls.interfaces.add(callback)
            }

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


            if (activityType == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") {
                // In-page entry shown only while the SettingKey destination is active.
                addMethod("morpheSetSettingsEntryVisible", listOf("Z"), 12, """
                    const v0, 0x1020002
                    invoke-virtual {p0, v0}, Landroid/app/Activity;->findViewById(I)Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :done
                    const-string v1, "morphe.tver.settings.entry"
                    invoke-virtual {v0, v1}, Landroid/view/View;->findViewWithTag(Ljava/lang/Object;)Landroid/view/View;
                    move-result-object v2
                    if-nez v2, :found
                    if-eqz p1, :done
                    new-instance v2, Landroid/widget/Button;
                    invoke-direct {v2, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
                    const-string v3, "Morphe設定  ›"
                    invoke-virtual {v2, v3}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    invoke-virtual {v2, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    invoke-virtual {v2, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                    new-instance v4, Landroid/widget/FrameLayout${'$'}LayoutParams;
                    const/4 v5, -0x1
                    const/4 v6, -0x2
                    const/16 v7, 0x50
                    invoke-direct {v4, v5, v6, v7}, Landroid/widget/FrameLayout${'$'}LayoutParams;-><init>(III)V
                    const/16 v5, 0x18
                    const/16 v6, 0x50
                    invoke-virtual {v4, v5, v5, v5, v6}, Landroid/view/ViewGroup${'$'}MarginLayoutParams;->setMargins(IIII)V
                    check-cast v0, Landroid/view/ViewGroup;
                    invoke-virtual {v0, v2, v4}, Landroid/view/ViewGroup;->addView(Landroid/view/View;Landroid/view/ViewGroup${'$'}LayoutParams;)V
                    :found
                    if-eqz p1, :hide
                    const/4 v3, 0x0
                    goto :set_visibility
                    :hide
                    const/16 v3, 0x8
                    :set_visibility
                    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V
                    :done
                    return-void
                """)

                addMethod("morpheShowSettingsDialog", emptyList(), 9, """
                    new-instance v0, Landroid/widget/Switch;
                    invoke-direct {v0, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
                    const-string v1, "PiP TESTボタンを表示"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const/16 v1, 0x28
                    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
                    const-string v1, "morphe.tver.settings"
                    const/4 v2, 0x0
                    invoke-virtual {p0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v1
                    const-string v2, "pip_test_visible"
                    const/4 v3, 0x1
                    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    invoke-virtual {v0, v3}, Landroid/widget/CompoundButton;->setChecked(Z)V
                    invoke-virtual {v0, p0}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                    new-instance v7, Landroid/widget/LinearLayout;
                    invoke-direct {v7, p0}, Landroid/widget/LinearLayout;-><init>(Landroid/content/Context;)V
                    const/4 v6, 0x1
                    invoke-virtual {v7, v6}, Landroid/widget/LinearLayout;->setOrientation(I)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    new-instance v0, Landroid/widget/Switch;
                    invoke-direct {v0, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
                    const-string v1, "起動アニメーションをスキップ（次回起動から）"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const/16 v1, 0x28
                    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
                    const-string v1, "skip_startup_animation"
                    invoke-virtual {v0, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    const-string v1, "morphe.tver.settings"
                    const/4 v2, 0x0
                    invoke-virtual {p0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v1
                    const-string v2, "skip_startup_animation"
                    const/4 v3, 0x0
                    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    invoke-virtual {v0, v3}, Landroid/widget/CompoundButton;->setChecked(Z)V
                    invoke-virtual {v0, p0}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    # Experimental: use Firebase's own fetch interval cache on
                    # subsequent launches instead of fetching every time.
                    new-instance v0, Landroid/widget/Switch;
                    invoke-direct {v0, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
                    const-string v1, "Remote Configを5分キャッシュ（実験・次回起動から）"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const/16 v1, 0x28
                    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
                    const-string v1, "cache_remote_config_5m"
                    invoke-virtual {v0, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    const-string v1, "morphe.tver.settings"
                    const/4 v2, 0x0
                    invoke-virtual {p0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v1
                    const-string v2, "cache_remote_config_5m"
                    const/4 v3, 0x0
                    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    invoke-virtual {v0, v3}, Landroid/widget/CompoundButton;->setChecked(Z)V
                    invoke-virtual {v0, p0}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    # Independent parallel init, default OFF.
                    # subsequent launches instead of fetching every time.
                    new-instance v0, Landroid/widget/Switch;
                    invoke-direct {v0, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
                    const-string v1, "Remote ConfigとTVerAppを並行初期化（実験・次回起動）"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const/16 v1, 0x28
                    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
                    const-string v1, "parallel_remote_tverapp"
                    invoke-virtual {v0, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    const-string v1, "morphe.tver.settings"
                    const/4 v2, 0x0
                    invoke-virtual {p0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v1
                    const-string v2, "parallel_remote_tverapp"
                    const/4 v3, 0x0
                    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    invoke-virtual {v0, v3}, Landroid/widget/CompoundButton;->setChecked(Z)V
                    invoke-virtual {v0, p0}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    # Time-sync parallelization is opt-in, default OFF.
                    new-instance v0, Landroid/widget/Switch;
                    invoke-direct {v0, p0}, Landroid/widget/Switch;-><init>(Landroid/content/Context;)V
                    const-string v1, "サーバー時刻同期を非同期化（実験・時刻ずれ注意・次回起動）"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const/16 v1, 0x28
                    invoke-virtual {v0, v1, v1, v1, v1}, Landroid/view/View;->setPadding(IIII)V
                    const-string v1, "async_server_time_sync"
                    invoke-virtual {v0, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    const-string v1, "morphe.tver.settings"
                    const/4 v2, 0x0
                    invoke-virtual {p0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v1
                    const-string v2, "async_server_time_sync"
                    const/4 v3, 0x0
                    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    invoke-virtual {v0, v3}, Landroid/widget/CompoundButton;->setChecked(Z)V
                    invoke-virtual {v0, p0}, Landroid/widget/CompoundButton;->setOnCheckedChangeListener(Landroid/widget/CompoundButton${'$'}OnCheckedChangeListener;)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    new-instance v0, Landroid/widget/Button;
                    invoke-direct {v0, p0}, Landroid/widget/Button;-><init>(Landroid/content/Context;)V
                    const-string v1, "起動ログを表示・コピー"
                    invoke-virtual {v0, v1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V
                    const-string v1, "morphe.tver.startup.view"
                    invoke-virtual {v0, v1}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    invoke-virtual {v0, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                    invoke-virtual {v7, v0}, Landroid/view/ViewGroup;->addView(Landroid/view/View;)V
                    new-instance v4, Landroid/app/AlertDialog${'$'}Builder;
                    invoke-direct {v4, p0}, Landroid/app/AlertDialog${'$'}Builder;-><init>(Landroid/content/Context;)V
                    const-string v5, "Morphe設定"
                    invoke-virtual {v4, v5}, Landroid/app/AlertDialog${'$'}Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog${'$'}Builder;
                    invoke-virtual {v4, v7}, Landroid/app/AlertDialog${'$'}Builder;->setView(Landroid/view/View;)Landroid/app/AlertDialog${'$'}Builder;
                    const-string v5, "閉じる"
                    const/4 v6, 0x0
                    invoke-virtual {v4, v5, v6}, Landroid/app/AlertDialog${'$'}Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)Landroid/app/AlertDialog${'$'}Builder;
                    invoke-virtual {v4}, Landroid/app/AlertDialog${'$'}Builder;->show()Landroid/app/AlertDialog;
                    return-void
                """)


                addMethod("morpheShowStartupLogDialog", emptyList(), 8, """
                    new-instance v0, Landroid/app/AlertDialog${'$'}Builder;
                    invoke-direct {v0, p0}, Landroid/app/AlertDialog${'$'}Builder;-><init>(Landroid/content/Context;)V
                    const-string v1, "起動ログ（アプリ終了まで保持）"
                    invoke-virtual {v0, v1}, Landroid/app/AlertDialog${'$'}Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog${'$'}Builder;
                    invoke-static {}, Ljp/hamitv/hamiand1/tver/ui/fragments/splash/SplashViewModel;->morpheGetStartupLog()Ljava/lang/String;
                    move-result-object v2
                    invoke-virtual {v0, v2}, Landroid/app/AlertDialog${'$'}Builder;->setMessage(Ljava/lang/CharSequence;)Landroid/app/AlertDialog${'$'}Builder;
                    const/4 v2, 0x0
                    const-string v1, "閉じる"
                    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog${'$'}Builder;->setNegativeButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)Landroid/app/AlertDialog${'$'}Builder;
                    const-string v1, "コピー"
                    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog${'$'}Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface${'$'}OnClickListener;)Landroid/app/AlertDialog${'$'}Builder;
                    invoke-virtual {v0}, Landroid/app/AlertDialog${'$'}Builder;->show()Landroid/app/AlertDialog;
                    move-result-object v3
                    const/4 v4, -0x1
                    invoke-virtual {v3, v4}, Landroid/app/AlertDialog;->getButton(I)Landroid/widget/Button;
                    move-result-object v3
                    const-string v4, "morphe.tver.startup.copy"
                    invoke-virtual {v3, v4}, Landroid/view/View;->setTag(Ljava/lang/Object;)V
                    invoke-virtual {v3, p0}, Landroid/view/View;->setOnClickListener(Landroid/view/View${'$'}OnClickListener;)V
                    return-void
                """)

                addMethod("morpheCopyStartupLog", emptyList(), 6, """
                    const-string v0, "clipboard"
                    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
                    move-result-object v0
                    instance-of v1, v0, Landroid/content/ClipboardManager;
                    if-eqz v1, :done
                    check-cast v0, Landroid/content/ClipboardManager;
                    invoke-static {}, Ljp/hamitv/hamiand1/tver/ui/fragments/splash/SplashViewModel;->morpheGetStartupLog()Ljava/lang/String;
                    move-result-object v1
                    const-string v2, "TVer起動ログ"
                    invoke-static {v2, v1}, Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;
                    move-result-object v1
                    invoke-virtual {v0, v1}, Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V
                    :done
                    return-void
                """)

                addMethod("onCheckedChanged", listOf("Landroid/widget/CompoundButton;", "Z"), 6, """
                    const-string v0, "morphe.tver.settings"
                    const/4 v1, 0x0
                    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v1
                    const-string v2, "skip_startup_animation"
                    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :cache_setting
                    const-string v1, "skip_startup_animation"
                    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
                    return-void
                    :cache_setting
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v1
                    const-string v2, "cache_remote_config_5m"
                    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :parallel_setting
                    const-string v1, "cache_remote_config_5m"
                    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
                    return-void
                    :parallel_setting
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v1
                    const-string v2, "parallel_remote_tverapp"
                    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :clock_setting
                    const-string v1, "parallel_remote_tverapp"
                    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
                    return-void
                    :clock_setting
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v1
                    const-string v2, "async_server_time_sync"
                    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :pip_setting
                    const-string v1, "async_server_time_sync"
                    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
                    return-void
                    :pip_setting
                    const-string v1, "pip_test_visible"
                    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    move-result-object v0
                    invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
                    const/4 v0, 0x1
                    invoke-virtual {p0, v0}, $activityType->morpheSetPipDebugVisible(Z)V
                    return-void
                """)
            }

            addMethod(
                "onClick", listOf("Landroid/view/View;"), 6,
                """
                    ${if (activityType == "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;") """
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v0
                    const-string v1, "morphe.tver.settings.entry"
                    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :startup_view
                    invoke-virtual {p0}, $activityType->morpheShowSettingsDialog()V
                    return-void
                    :startup_view
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v0
                    const-string v1, "morphe.tver.startup.view"
                    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :startup_copy
                    invoke-virtual {p0}, $activityType->morpheShowStartupLogDialog()V
                    return-void
                    :startup_copy
                    invoke-virtual {p1}, Landroid/view/View;->getTag()Ljava/lang/Object;
                    move-result-object v0
                    const-string v1, "morphe.tver.startup.copy"
                    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :pip_test
                    invoke-virtual {p0}, $activityType->morpheCopyStartupLog()V
                    return-void
                    :pip_test
                    """.trimIndent() else ""}
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
                    const-string v8, "morphe.tver.settings"
                    const/4 v9, 0x0
                    invoke-virtual {p0, v8, v9}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    move-result-object v8
                    const-string v9, "pip_test_visible"
                    const/4 v3, 0x1
                    invoke-interface {v8, v9, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                    move-result v3
                    if-nez v3, :enabled
                    const/4 p1, 0x0
                    :enabled
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

        // TVer 7.2.1: bypass only the app's video splash and its waiting period.
        // Keep SplashFragment SDK initialization and the Android system splash intact.
        run {
            val splashType = "Ljp/hamitv/hamiand1/tver/ui/fragments/splash/SplashVideoPresenter;"
            val splashCls = mutableClassDefBy(classDefBy(splashType))
            // Process-local setting snapshot for the home handoff.
            check(splashCls.fields.none { it.name == "morpheStartupSkipCached" })
            splashCls.fields.add(
                ImmutableField(
                    splashType, "morpheStartupSkipCached", "Z",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, null,
                ).toMutableField()
            )
            check(splashCls.methods.none { it.name == "morpheShouldSkipStartupAnimation" })
            splashCls.methods.add(
                ImmutableMethod(
                    splashType, "morpheShouldSkipStartupAnimation", emptyList(), "Z",
                    AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(4)
                ).toMutable().apply {
                    addInstructions(0, """
                        iget-object v0, p0, $splashType->host:Landroid/view/ViewGroup;
                        invoke-virtual {v0}, Landroid/view/ViewGroup;->getContext()Landroid/content/Context;
                        move-result-object v0
                        const-string v1, "morphe.tver.settings"
                        const/4 v2, 0x0
                        invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                        move-result-object v0
                        const-string v1, "skip_startup_animation"
                        invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                        move-result v0
                        sput-boolean v0, $splashType->morpheStartupSkipCached:Z
                        return v0
                    """.trimIndent())
                }
            )
            val start = splashCls.methods.single {
                it.name == "start" && it.parameterTypes.isEmpty()
            }
            // Plain addInstructions supports inline labels; external-label overload
            // throws length=0, index=0 when called without ExternalLabel arguments.
            check(requireNotNull(start.implementation).instructions.any()) {
                "SplashVideoPresenter.start has no bytecode"
            }
            start.addInstructions(0, """
                invoke-virtual {p0}, $splashType->morpheShouldSkipStartupAnimation()Z
                move-result v0
                if-eqz v0, :morphe_original_start
                return-void
                :morphe_original_start
                nop
            """.trimIndent())
            val wait = splashCls.methods.single {
                it.name == "awaitMinimumDisplay" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].toString() == "Lkotlin/jvm/functions/Function0;"
            }
            check(requireNotNull(wait.implementation).instructions.any()) {
                "SplashVideoPresenter.awaitMinimumDisplay has no bytecode"
            }
            wait.addInstructions(0, """
                invoke-virtual {p0}, $splashType->morpheShouldSkipStartupAnimation()Z
                move-result v0
                if-eqz v0, :morphe_original_wait
                invoke-interface {p1}, Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;
                move-result-object v0
                return-void
                :morphe_original_wait
                nop
            """.trimIndent())

            // Keep SDK and home prefetch running, but do not hold the handoff
            // for the extra (up to 1000ms) SplashGrace when skip is enabled.
            val vmType = "Ljp/hamitv/hamiand1/tver/ui/fragments/splash/SplashViewModel;"
            val vmCls = mutableClassDefBy(classDefBy(vmType))

            // Process-local, synchronized, payload-free startup event recorder.
            check(vmCls.fields.none { it.name == "morpheStartupBuffer" || it.name == "morpheStartupZeroMs" })
            vmCls.fields.add(ImmutableField(
                vmType, "morpheStartupBuffer", "Ljava/lang/StringBuffer;",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null, null, null,
            ).toMutableField())
            vmCls.fields.add(ImmutableField(
                vmType, "morpheStartupZeroMs", "J",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null, null, null,
            ).toMutableField())
            check(vmCls.methods.none { it.name == "morpheLogStartup" || it.name == "morpheGetStartupLog" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheLogStartup",
                listOf(ImmutableMethodParameter("Ljava/lang/String;", null, null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNCHRONIZED.value,
                null, null, MutableMethodImplementation(10),
            ).toMutable().apply { addInstructions(0, """
                sget-object v0, $vmType->morpheStartupBuffer:Ljava/lang/StringBuffer;
                if-nez v0, :buffer_ready
                new-instance v0, Ljava/lang/StringBuffer;
                invoke-direct {v0}, Ljava/lang/StringBuffer;-><init>()V
                sput-object v0, $vmType->morpheStartupBuffer:Ljava/lang/StringBuffer;
                invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
                move-result-wide v1
                sput-wide v1, $vmType->morpheStartupZeroMs:J
                :buffer_ready
                invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
                move-result-wide v1
                sget-wide v3, $vmType->morpheStartupZeroMs:J
                sub-long/2addr v1, v3
                new-instance v5, Ljava/lang/StringBuilder;
                invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V
                const-string v6, "+"
                invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v5, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
                const-string v6, " ms  "
                invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v5, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v6
                invoke-virtual {v0, v6}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;
                const-string v7, "\n"
                invoke-virtual {v0, v7}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;
                const-string v7, "MorpheStartup"
                invoke-static {v7, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            """.trimIndent()) })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheGetStartupLog", emptyList(), "Ljava/lang/String;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.SYNCHRONIZED.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                sget-object v0, $vmType->morpheStartupBuffer:Ljava/lang/StringBuffer;
                if-nez v0, :ready
                const-string v0, "この起動ではログがまだ記録されていません。"
                return-object v0
                :ready
                invoke-virtual {v0}, Ljava/lang/StringBuffer;->toString()Ljava/lang/String;
                move-result-object v0
                return-object v0
            """.trimIndent()) })

            // Warm-start experiment: leave Firebase Remote Config's standard
            // fetchAndActivate flow intact, but let its normal cache suppress
            // repeated network fetches for up to 300 seconds when opted in.
            // IMPORTANT: disabled by default and never skips SDK/user consent.
            val remoteConfigBuilderType =
                "Ljp/hamitv/hamiand1/tver/core/common/domain/remoteconfig/data/RemoteConfigInteractor" +
                    "$" + "Builder;"
            check(vmCls.methods.none { it.name == "morpheApplyRemoteConfigCacheInterval" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheApplyRemoteConfigCacheInterval",
                listOf(ImmutableMethodParameter(remoteConfigBuilderType, null, null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(8),
            ).toMutable().apply { addInstructions(0, """
                invoke-static {}, Lcom/google/firebase/FirebaseApp;->getInstance()Lcom/google/firebase/FirebaseApp;
                move-result-object v0
                invoke-virtual {v0}, Lcom/google/firebase/FirebaseApp;->getApplicationContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "morphe.tver.settings"
                const/4 v2, 0x0
                invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "cache_remote_config_5m"
                invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :remote_cache_done
                const-wide/16 v0, 0x12c
                invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;
                move-result-object v0
                invoke-virtual {p0, v0}, $remoteConfigBuilderType->setMinimumFetchIntervalInSeconds(Ljava/lang/Long;)V
                const-string v0, "REMOTE_CONFIG_5MIN_CACHE_ENABLED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :remote_cache_done
                return-void
            """.trimIndent()) })

            // The app's own callback first sets a zero-second interval. Only
            // after that original instruction, optionally change it to 300s.
            // This is the builder stage, before fetchAndActivate() starts.
            val remoteConfigLambdaType =
                "Ljp/hamitv/hamiand1/tver/core/common/domain/remoteconfig/data/RemoteConfigInteractor" +
                    "$" + "fetchAndActivate" + "$" + "2" + "$" + "1;"
            val remoteLambdaCls = mutableClassDefBy(classDefBy(remoteConfigLambdaType))
            val remoteLambdaMethod = remoteLambdaCls.methods.single {
                it.name == "invoke" && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].toString() == remoteConfigBuilderType
            }
            val remoteReturns = requireNotNull(remoteLambdaMethod.implementation).instructions
                .mapIndexedNotNull { i, op -> i.takeIf { op.opcode == Opcode.RETURN_VOID } }
            check(remoteReturns.size == 1) {
                "Remote Config fetch callback unexpectedly has ${remoteReturns.size} returns"
            }
            remoteLambdaMethod.addInstructions(
                remoteReturns.single(),
                "invoke-static {p1}, $vmType->morpheApplyRemoteConfigCacheInterval($remoteConfigBuilderType)V",
            )

            // No argument registers are read in the conditional bypass itself.
            // Calling the zero-arg helper avoids a v0 reference/boolean verifier
            // conflict in SplashViewModel.appliesHomeLoadGrace() (Android 16).
            check(vmCls.methods.none { it.name == "morpheNoteHomeGraceSkipped" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheNoteHomeGraceSkipped", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(1),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "HOME_GRACE_SKIPPED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })

            val grace = vmCls.methods.single {
                it.name == "appliesHomeLoadGrace" && it.parameterTypes.isEmpty()
            }
            check(requireNotNull(grace.implementation).registerCount >= 2) {
                "SplashViewModel.appliesHomeLoadGrace needs a local register"
            }
            grace.addInstructions(0, """
                sget-boolean v0, $splashType->morpheStartupSkipCached:Z
                if-eqz v0, :morphe_original_grace
                invoke-static {}, $vmType->morpheNoteHomeGraceSkipped()V
                const/4 v0, 0x0
                return v0
                :morphe_original_grace
                nop
            """.trimIndent())

            // Safe instrumentation: each target method receives ONLY a
            // zero-argument invoke-static. It never clobbers locals (especially
            // v0, which can change verifier type across existing control flow).
            // The separate helper owns its private scratch register.
            var milestoneId = 0
            fun traceEntry(
                descriptor: String, name: String, event: String,
                afterCall: String = "",
            ) {
                val helperName = "morpheStartupMilestone" + milestoneId++
                check(vmCls.methods.none { it.name == helperName })
                vmCls.methods.add(ImmutableMethod(
                    vmType, helperName, emptyList(), "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(1),
                ).toMutable().apply { addInstructions(0, """
                    const-string v0, "$event"
                    invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    return-void
                """.trimIndent()) })
                val klass = mutableClassDefBy(classDefBy(descriptor))
                val method = klass.methods.single { it.name == name }
                check(requireNotNull(method.implementation).instructions.any()) {
                    "No bytecode for startup milestone: $descriptor->$name"
                }
                method.addInstructions(0, """
                    invoke-static {}, $vmType->$helperName()V
                    $afterCall
                """.trimIndent())
            }

            // TVer 7.2.1: the setting.json request is made in
            // TVerApp.init\$lambda\$3 via okhttp3.Call.execute(). Replace its
            // 3-code-unit invoke-interface with a 3-code-unit invoke-static.
            // No surrounding offsets, register values or try ranges change.
            val tverAppType = "Ljp/tver/appsdk/TVerApp;"
            val tverAppCls = mutableClassDefBy(classDefBy(tverAppType))
            val requestMethod = tverAppCls.methods.single { it.name == "init" + "$" + "lambda" + "$" + "3" }
            val requestInstructions = requireNotNull(requestMethod.implementation).instructions.toList()
            val executeMatches = requestInstructions.indices.filter { index ->
                val inst = requestInstructions[index]
                val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                inst.opcode == Opcode.INVOKE_INTERFACE &&
                    ref?.definingClass == "Lokhttp3/Call;" &&
                    ref.name == "execute" &&
                    ref.parameterTypes.isEmpty() &&
                    ref.returnType == "Lokhttp3/Response;"
            }
            check(executeMatches.size == 1) {
                "Expected exactly one Call.execute() in TVerApp.init worker; got ${executeMatches.size}"
            }
            val executeIndex = executeMatches.single()
            val oldCall = requestInstructions[executeIndex] as Instruction35c
            check(oldCall.registerCount == 1) {
                "Expected one register for okhttp Call.execute(), got ${oldCall.registerCount}"
            }

            // The wrapper records only event names and timestamps; it never
            // exposes request headers, URLs, responses or user data.
            val executeWrapperName = "morpheExecuteSettingRequest"
            check(vmCls.methods.none { it.name == executeWrapperName })
            vmCls.methods.add(ImmutableMethod(
                vmType, executeWrapperName,
                listOf(ImmutableMethodParameter("Lokhttp3/Call;", null, null)),
                "Lokhttp3/Response;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SETTINGS_HTTP_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-interface {p0}, Lokhttp3/Call;->execute()Lokhttp3/Response;
                move-result-object v0
                invoke-static {}, $vmType->morpheNoteSettingsHttpDone()V
                return-object v0
            """.trimIndent()) })
            check(vmCls.methods.none { it.name == "morpheNoteSettingsHttpDone" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheNoteSettingsHttpDone", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(1),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SETTINGS_HTTP_RESPONSE_RECEIVED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            val workerImplementation =
                requireNotNull(requestMethod.implementation) as MutableMethodImplementation
            workerImplementation.replaceInstruction(
                executeIndex,
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 1, oldCall.registerC, 0, 0, 0, 0,
                    ImmutableMethodReference(
                        vmType, executeWrapperName,
                        listOf("Lokhttp3/Call;"), "Lokhttp3/Response;"
                    ),
                ),
            )
            // Only a single entry insertion per method; no register clobbering.
            traceEntry(tverAppType, "init" + "$" + "lambda" + "$" + "3", "SETTINGS_WORKER_START")
            traceEntry(
                tverAppType,
                "init" + "$" + "lambda" + "$" + "3" + "$" + "lambda" + "$" + "1",
                "SETTINGS_CONFIG_APPLIED_CALLBACK",
            )
            traceEntry(
                tverAppType,
                "init" + "$" + "lambda" + "$" + "3" + "$" + "lambda" + "$" + "2",
                "SETTINGS_HTTP_ERROR_CALLBACK",
            )
            val homeTabType = "Ljp/hamitv/hamiand1/tver/ui/fragments/top/TabMainActivityFragment;"
            val homeTab = mutableClassDefBy(classDefBy(homeTabType))
            // Android 10+ frame-commit notification: unlike a posted Runnable
            // or a pre-draw callback, this fires after a frame is submitted to
            // the GPU swap chain. The callback is one-shot by platform design.
            // It tracks the FIRST frame committed after tab initialization,
            // not necessarily fully loaded remote home content.
            check("Ljava/lang/Runnable;" !in homeTab.interfaces) {
                "Home tab unexpectedly already implements Runnable"
            }
            check(homeTab.methods.none {
                it.name in setOf("run", "morpheArmHomeFrameCommit") &&
                    it.parameterTypes.isEmpty()
            })
            homeTab.interfaces.add("Ljava/lang/Runnable;")
            homeTab.fields.add(ImmutableField(
                homeTabType, "morpheHomeFrameCallbackArmed", "Z",
                AccessFlags.PRIVATE.value,
                null, null, null,
            ).toMutableField())
            homeTab.methods.add(ImmutableMethod(
                homeTabType, "morpheArmHomeFrameCommit", emptyList(), "V",
                AccessFlags.PUBLIC.value,
                null, null, MutableMethodImplementation(4),
            ).toMutable().apply { addInstructions(0, """
                iget-boolean v0, p0, $homeTabType->morpheHomeFrameCallbackArmed:Z
                if-nez v0, :done
                invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->getView()Landroid/view/View;
                move-result-object v0
                if-eqz v0, :done
                invoke-virtual {v0}, Landroid/view/View;->getViewTreeObserver()Landroid/view/ViewTreeObserver;
                move-result-object v0
                invoke-virtual {v0}, Landroid/view/ViewTreeObserver;->isAlive()Z
                move-result v1
                if-eqz v1, :done
                const/4 v1, 0x1
                iput-boolean v1, p0, $homeTabType->morpheHomeFrameCallbackArmed:Z
                invoke-virtual {v0, p0}, Landroid/view/ViewTreeObserver;->registerFrameCommitCallback(Ljava/lang/Runnable;)V
                const-string v0, "HOME_FRAME_COMMIT_ARMED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :done
                return-void
            """.trimIndent()) })
            homeTab.methods.add(ImmutableMethod(
                homeTabType, "run", emptyList(), "V",
                AccessFlags.PUBLIC.value,
                null, null, MutableMethodImplementation(1),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "HOME_FIRST_FRAME_COMMITTED_AFTER_INIT"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            traceEntry(homeTabType, "onViewCreated", "HOME_TAB_VIEW_CREATED")
            // Inject one small sequence, once, at method entry. No custom
            // branches or register clobbering in the original initTabs code.
            traceEntry(
                homeTabType, "initTabs", "HOME_TAB_CONTENT_INIT_START",
                "invoke-virtual {p0}, $homeTabType->morpheArmHomeFrameCommit()V",
            )
            traceEntry(homeTabType, "toHome", "HOME_NAVIGATION_START")

            // Measure the actual remote-config interactor entry plus the
            // completion callback that gates initializeSdkPlugins(). The
            // callback also runs after an exception; this measures total
            // elapsed time until the gate is released, not only network RTT.
            val remoteType =
                "Ljp/hamitv/hamiand1/tver/core/common/domain/remoteconfig/data/RemoteConfigInteractor;"
            traceEntry(vmType, "updateAppRemoteConfig", "REMOTE_CONFIG_SCHEDULED")
            traceEntry(remoteType, "fetchAndActivate", "REMOTE_CONFIG_FETCH_ENTER")
            val fragmentType = "Ljp/hamitv/hamiand1/tver/ui/fragments/splash/SplashFragment;"
            listOf(
                "onViewCreated" to "SPLASH_VIEW_CREATED",
                "onCreateView" to "SPLASH_CREATE_VIEW_ENTER",
                "onCreateView" + "$" + "lambda" + "$" + "0" to "REMOTE_CONFIG_GATE_RELEASED",
                "initializeSdkPlugins" to "SDK_PLUGIN_INIT_START",
                "tverAppInitSuccessProcess" to "TVER_APP_INIT_SUCCESS",
                "onCdnStartConfigUrlResponse" to "CDN_CONFIG_RECEIVED",
                "onCdnStartConfigUrlError" to "CDN_CONFIG_ERROR",
                "onSdkAgreementVersionResponse" to "SDK_AGREEMENT_RECEIVED",
                "onSdkAgreementVersionError" to "SDK_AGREEMENT_ERROR",
                "onSdkInitResponse" to "SDK_INIT_SUCCESS_USER_INIT_START",
                "onSdkInitError" to "SDK_INIT_ERROR",
                "transitionToHome" to "TRANSITION_TO_HOME_REQUESTED",
            ).forEach { (name, event) -> traceEntry(fragmentType, name, event) }
            // Distinguish SDK network / callback work from CDN startup. Both
            // requests are scheduled after TVerApp.init, and run independently.
            val cdnPresenterType =
                "Ljp/hamitv/hamiand1/tver/presenters/cdn/CdnStartConfigUrlPresenter;"
            val sdkPresenterType =
                "Ljp/hamitv/hamiand1/tver/presenters/sdk/SdkInitPresenter;"
            val sdkUpdateListenerType =
                sdkPresenterType.dropLast(1) + "$" + "getInitResponse" + "$" + "1;"
            traceEntry(cdnPresenterType, "getStartConfigUrlData", "CDN_CONFIG_REQUEST_START")
            traceEntry(sdkPresenterType, "getInitResponse", "SDK_INIT_REQUEST_START")
            traceEntry(sdkUpdateListenerType, "onUpdate", "SDK_PROFILE_CALLBACK_BEGIN")

            // TVer 7.2.1's SDK implementation is obfuscated as b.l in classes8.dex.
            // TVer.init() schedules l.a(Context) on its own ExecutorService.
            // Only event names are recorded, never profile IDs/tokens/bodies.
            val sdkInternalType = "Lb/l;"
            val sdkProfileType = "Lb/p;"
            val sdkInternalCls = mutableClassDefBy(classDefBy(sdkInternalType))
            var sdkInternalTraceId = 0
            fun traceSdkInternal(
                name: String,
                params: List<String>,
                event: String,
            ) {
                val actualMethods = sdkInternalCls.methods.filter { method ->
                    method.name == name &&
                        method.parameterTypes.map { it.toString() } == params &&
                        method.returnType == "V"
                }
                check(actualMethods.size == 1) {
                    "Expected unique TVer SDK method $sdkInternalType->$name$params: " +
                        "found " + actualMethods.size
                }
                val target = actualMethods.single()
                val helperName = "morpheSdkInternalMilestone" + sdkInternalTraceId++
                check(vmCls.methods.none { it.name == helperName })
                vmCls.methods.add(ImmutableMethod(
                    vmType, helperName, emptyList(), "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(1),
                ).toMutable().apply { addInstructions(0, """
                    const-string v0, "$event"
                    invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    return-void
                """.trimIndent()) })
                check(requireNotNull(target.implementation).instructions.any()) {
                    "Missing DEX implementation for TVer SDK method $name$params"
                }
                target.addInstructions(0, "invoke-static {}, $vmType->$helperName()V")
            }
            traceSdkInternal("a", listOf("Landroid/content/Context;"),
                "SDK_INTERNAL_WORKER_START")
            traceSdkInternal("b", listOf(sdkProfileType),
                "SDK_INTERNAL_PROFILE_SETUP_START")
            traceSdkInternal("g", listOf(sdkProfileType),
                "SDK_INTERNAL_PROFILE_CALLBACK_QUEUED")
            traceSdkInternal("h", listOf(sdkProfileType),
                "SDK_INTERNAL_PROFILE_CALLBACK_DISPATCHED")

            // The independent jp.co.tver.sdk initialization fetches its
            // *own* CDN /setting.json through b.n$a.a(String), separate from
            // TVerApp.init's earlier settings HTTP request. Instrument that
            // precise call to determine if its cold network fetch is the
            // variable ~2-second bottleneck. Keep original OkHttp semantics.
            val sdkCdnConfigType = "Lb/n" + "$" + "a;"
            val sdkCdnConfigCls = mutableClassDefBy(classDefBy(sdkCdnConfigType))
            val sdkCdnConfigMethod = sdkCdnConfigCls.methods.single {
                it.name == "a" &&
                    it.parameterTypes.map { p -> p.toString() } == listOf("Ljava/lang/String;") &&
                    it.returnType == "Lb/n;"
            }
            val sdkCdnOps = requireNotNull(sdkCdnConfigMethod.implementation).instructions.toList()
            val sdkCdnExecuteMatches = sdkCdnOps.indices.filter { i ->
                val instruction = sdkCdnOps[i]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    ref?.definingClass == "Lokhttp3/Call;" &&
                    ref.name == "execute" &&
                    ref.parameterTypes.isEmpty() &&
                    ref.returnType == "Lokhttp3/Response;"
            }
            check(sdkCdnExecuteMatches.size == 1) {
                "Expected exactly one SDK CDN setting.json Call.execute(), got " +
                    sdkCdnExecuteMatches.size
            }
            val sdkCdnExecuteWrapperName = "morpheExecuteSdkCdnSettingRequest"
            check(vmCls.methods.none { it.name == sdkCdnExecuteWrapperName })
            vmCls.methods.add(ImmutableMethod(
                vmType, sdkCdnExecuteWrapperName,
                listOf(ImmutableMethodParameter("Lokhttp3/Call;", null, null)),
                "Lokhttp3/Response;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_CDN_SETTINGS_HTTP_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-interface {p0}, Lokhttp3/Call;->execute()Lokhttp3/Response;
                move-result-object v0
                invoke-static {}, $vmType->morpheSdkCdnSettingsHttpDone()V
                return-object v0
            """.trimIndent()) })
            check(vmCls.methods.none { it.name == "morpheSdkCdnSettingsHttpDone" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheSdkCdnSettingsHttpDone", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(1),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_CDN_SETTINGS_HTTP_END"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            val oldSdkCdnExecute = sdkCdnOps[sdkCdnExecuteMatches.single()] as Instruction35c
            check(oldSdkCdnExecute.registerCount == 1)
            requireNotNull(
                sdkCdnConfigMethod.implementation as? MutableMethodImplementation
            ).replaceInstruction(
                sdkCdnExecuteMatches.single(),
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 1, oldSdkCdnExecute.registerC, 0, 0, 0, 0,
                    ImmutableMethodReference(
                        vmType, sdkCdnExecuteWrapperName,
                        listOf("Lokhttp3/Call;"), "Lokhttp3/Response;",
                    ),
                ),
            )


            // b.l.a(Context) performs b.c.a(3) after SDK CDN config and
            // before profile setup. This synchronizes /v2/api/public/current_time,
            // retries nonzero API result up to three times with a 1000ms sleep.
            // Isolate server-time API and explicit retry delay; no changes to
            // time values, retry logic, failure handling or HTTP request data.
            val sdkClockSyncType = "Lb/c;"
            val sdkTimeApiType = "Lc/p;"
            val sdkTimeApiResultType = "Lc/b;"
            val sdkClockCls = mutableClassDefBy(classDefBy(sdkClockSyncType))
            val sdkClockMethod = sdkClockCls.methods.single {
                it.name == "a" &&
                    it.parameterTypes.map { type -> type.toString() } == listOf("I") &&
                    it.returnType == "V"
            }
            val sdkClockOps = requireNotNull(sdkClockMethod.implementation).instructions.toList()
            val clockCallIndices = sdkClockOps.indices.filter { i ->
                val instruction = sdkClockOps[i]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    ref?.definingClass == sdkTimeApiType &&
                    ref.name == "a" && ref.parameterTypes.isEmpty() &&
                    ref.returnType == sdkTimeApiResultType
            }
            check(clockCallIndices.size == 1) {
                "Expected exactly one TVer SDK time API call, found " + clockCallIndices.size
            }
            val clockSleepIndices = sdkClockOps.indices.filter { i ->
                val instruction = sdkClockOps[i]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_STATIC &&
                    ref?.definingClass == "Ljava/lang/Thread;" &&
                    ref.name == "sleep" &&
                    ref.parameterTypes.map { type -> type.toString() } == listOf("J") &&
                    ref.returnType == "V"
            }
            check(clockSleepIndices.size == 1) {
                "Expected one TVer SDK time retry sleep, got " + clockSleepIndices.size
            }
            fun addClockLogHelper(name: String, event: String) {
                check(vmCls.methods.none { it.name == name })
                vmCls.methods.add(ImmutableMethod(
                    vmType, name, emptyList(), "V",
                    AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(1),
                ).toMutable().apply { addInstructions(0, """
                    const-string v0, "$event"
                    invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    return-void
                """.trimIndent()) })
            }
            addClockLogHelper("morpheTimeSyncAttempt",
                "SDK_CURRENT_TIME_SYNC_ATTEMPT")
            check(vmCls.methods.none { it.name == "morpheRunSdkCurrentTimeApi" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheRunSdkCurrentTimeApi",
                listOf(ImmutableMethodParameter(sdkTimeApiType, null, null)),
                sdkTimeApiResultType,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_CURRENT_TIME_API_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-virtual {p0}, $sdkTimeApiType->a()$sdkTimeApiResultType
                move-result-object v0
                invoke-static {}, $vmType->morpheCurrentTimeApiReturned()V
                return-object v0
            """.trimIndent()) })
            addClockLogHelper("morpheCurrentTimeApiReturned",
                "SDK_CURRENT_TIME_API_END")
            check(vmCls.methods.none { it.name == "morpheSdkCurrentTimeRetrySleep" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheSdkCurrentTimeRetrySleep",
                listOf(ImmutableMethodParameter("J", null, null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(3),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_CURRENT_TIME_RETRY_SLEEP_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-static {p0, p1}, Ljava/lang/Thread;->sleep(J)V
                const-string v0, "SDK_CURRENT_TIME_RETRY_SLEEP_END"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            val sdkClockImpl =
                requireNotNull(sdkClockMethod.implementation as? MutableMethodImplementation)
            val originalTimeCall = sdkClockOps[clockCallIndices.single()] as Instruction35c
            check(originalTimeCall.registerCount == 1)
            sdkClockImpl.replaceInstruction(
                clockCallIndices.single(),
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 1, originalTimeCall.registerC, 0, 0, 0, 0,
                    ImmutableMethodReference(
                        vmType, "morpheRunSdkCurrentTimeApi",
                        listOf(sdkTimeApiType), sdkTimeApiResultType,
                    ),
                ),
            )
            val originalClockSleep = sdkClockOps[clockSleepIndices.single()] as Instruction35c
            check(originalClockSleep.registerCount == 2)
            sdkClockImpl.replaceInstruction(
                clockSleepIndices.single(),
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 2,
                    originalClockSleep.registerC, originalClockSleep.registerD, 0, 0, 0,
                    ImmutableMethodReference(
                        vmType, "morpheSdkCurrentTimeRetrySleep",
                        listOf("J"), "V",
                    ),
                ),
            )

            // Prepend entry instrumentation AFTER fixed-index replacements:
            // inserting first would shift original instruction indices by one.
            sdkClockMethod.addInstructions(
                0, "invoke-static {}, $vmType->morpheTimeSyncAttempt()V",
            )

            // EXPERIMENTAL, default OFF. Clock b.c starts with device
            // wall time; the original blocking 3-retry sync catches errors.
            // Only when opted in: run the original SDK clock sync on its own
            // thread, allowing the genuine Home initialization to proceed.
            // Time-sensitive SDK operations may observe device time before
            // the background sync finishes (risk if device clock is wrong).
            check(!sdkClockCls.interfaces.contains("Ljava/lang/Runnable;"))
            check(sdkClockCls.methods.none {
                it.name == "run" && it.parameterTypes.isEmpty()
            })
            sdkClockCls.interfaces.add("Ljava/lang/Runnable;")
            sdkClockCls.methods.add(ImmutableMethod(
                sdkClockSyncType, "run", emptyList(), "V",
                AccessFlags.PUBLIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_CURRENT_TIME_BACKGROUND_BEGIN"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                const/4 v0, 0x3
                invoke-virtual {p0, v0}, $sdkClockSyncType->a(I)V
                const-string v0, "SDK_CURRENT_TIME_BACKGROUND_DONE"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            val dispatchClockName = "morpheStartClockSyncMaybeAsync"
            check(vmCls.methods.none { it.name == dispatchClockName })
            vmCls.methods.add(ImmutableMethod(
                vmType, dispatchClockName,
                listOf(
                    ImmutableMethodParameter(sdkClockSyncType, null, null),
                    ImmutableMethodParameter("I", null, null),
                ), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(5),
            ).toMutable().apply { addInstructions(0, """
                sget-object v0, Lb/j;->c:Landroid/content/Context;
                if-eqz v0, :original_clock_sync
                const-string v1, "morphe.tver.settings"
                const/4 v2, 0x0
                invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "async_server_time_sync"
                invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v0
                if-eqz v0, :original_clock_sync
                const-string v0, "SDK_CURRENT_TIME_BACKGROUND_SCHEDULED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                new-instance v0, Ljava/lang/Thread;
                invoke-direct {v0, p0}, Ljava/lang/Thread;-><init>(Ljava/lang/Runnable;)V
                const-string v1, "MorpheSDKClock"
                invoke-virtual {v0, v1}, Ljava/lang/Thread;->setName(Ljava/lang/String;)V
                invoke-virtual {v0}, Ljava/lang/Thread;->start()V
                return-void
                :original_clock_sync
                invoke-virtual {p0, p1}, $sdkClockSyncType->a(I)V
                return-void
            """.trimIndent()) })
            val sdkInitMethod = sdkInternalCls.methods.single {
                it.name == "a" &&
                    it.parameterTypes.map { t -> t.toString() } ==
                        listOf("Landroid/content/Context;") &&
                    it.returnType == "V"
            }
            val sdkInitCalls = requireNotNull(sdkInitMethod.implementation).instructions.toList()
            val sdkClockStartupCalls = sdkInitCalls.indices.filter { index ->
                val instruction = sdkInitCalls[index]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    ref?.definingClass == sdkClockSyncType &&
                    ref.name == "a" &&
                    ref.parameterTypes.map { p -> p.toString() } == listOf("I") &&
                    ref.returnType == "V"
            }
            check(sdkClockStartupCalls.size == 1) {
                "Expected one SDK startup clock sync call, found " +
                    sdkClockStartupCalls.size
            }
            val oldClockStartupCall =
                sdkInitCalls[sdkClockStartupCalls.single()] as Instruction35c
            check(oldClockStartupCall.registerCount == 2)
            requireNotNull(
                sdkInitMethod.implementation as? MutableMethodImplementation
            ).replaceInstruction(
                sdkClockStartupCalls.single(),
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 2,
                    oldClockStartupCall.registerC, oldClockStartupCall.registerD,
                    0, 0, 0,
                    ImmutableMethodReference(
                        vmType, dispatchClockName, listOf(sdkClockSyncType, "I"), "V",
                    ),
                ),
            )

            // TVer SDK b.t holds a 24-hour cached salt in tversdk_prefs.
            // When absent/expired, a(String cdnEndpoint, Context) synchronously
            // fetches /data/string/s.json. This happens *between* SDK CDN
            // settings fetch and PROFILE_SETUP_START in b.l.a(Context).
            // Instrument only the actual Call.execute(), not the cached path.
            val sdkSaltType = "Lb/t;"
            val sdkSaltCls = mutableClassDefBy(classDefBy(sdkSaltType))
            val sdkSaltFetch = sdkSaltCls.methods.single {
                it.name == "a" &&
                    it.parameterTypes.map { type -> type.toString() } ==
                        listOf("Ljava/lang/String;", "Landroid/content/Context;") &&
                    it.returnType == "Ljava/lang/String;"
            }
            val sdkSaltOps = requireNotNull(sdkSaltFetch.implementation).instructions.toList()
            val sdkSaltExecuteCalls = sdkSaltOps.indices.filter { i ->
                val instruction = sdkSaltOps[i]
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_INTERFACE &&
                    ref?.definingClass == "Lokhttp3/Call;" &&
                    ref.name == "execute" &&
                    ref.parameterTypes.isEmpty() &&
                    ref.returnType == "Lokhttp3/Response;"
            }
            check(sdkSaltExecuteCalls.size == 1) {
                "Expected one SDK salt HTTP Call.execute(), got " +
                    sdkSaltExecuteCalls.size
            }
            check(vmCls.methods.none { it.name == "morpheExecuteSdkSaltRequest" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheExecuteSdkSaltRequest",
                listOf(ImmutableMethodParameter("Lokhttp3/Call;", null, null)),
                "Lokhttp3/Response;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(2),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_SALT_HTTP_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-interface {p0}, Lokhttp3/Call;->execute()Lokhttp3/Response;
                move-result-object v0
                invoke-static {}, $vmType->morpheSdkSaltRequestCompleted()V
                return-object v0
            """.trimIndent()) })
            check(vmCls.methods.none { it.name == "morpheSdkSaltRequestCompleted" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheSdkSaltRequestCompleted", emptyList(), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(1),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_SALT_HTTP_END"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })
            val originalSaltCall =
                sdkSaltOps[sdkSaltExecuteCalls.single()] as Instruction35c
            check(originalSaltCall.registerCount == 1)
            requireNotNull(
                sdkSaltFetch.implementation as? MutableMethodImplementation
            ).replaceInstruction(
                sdkSaltExecuteCalls.single(),
                BuilderInstruction35c(
                    Opcode.INVOKE_STATIC, 1, originalSaltCall.registerC, 0, 0, 0, 0,
                    ImmutableMethodReference(
                        vmType, "morpheExecuteSdkSaltRequest",
                        listOf("Lokhttp3/Call;"), "Lokhttp3/Response;",
                    ),
                ),
            )

            // The SDK's b.l.b(profile) conditionally registers a missing
            // browser identifier at /v2/api/platform_users/browser/create.
            // Replace ONLY its sync f.a.a(request) call with an instrumented
            // static wrapper of the same 3-code-unit instruction length.
            // The wrapper performs the exact original request, without
            // logging identifiers, request paths, headers or response body.
            val browserReqType = "Lf/a" + "$" + "a;"
            val browserResType = "Lf/a" + "$" + "b;"
            val browserCreateMethod = sdkInternalCls.methods.single {
                it.name == "b" &&
                    it.parameterTypes.map { t -> t.toString() } == listOf(sdkProfileType) &&
                    it.returnType == "V"
            }
            val browserOps = requireNotNull(browserCreateMethod.implementation).instructions.toList()
            val browserCallIndices = browserOps.indices.filter { i ->
                val inst = browserOps[i]
                val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                inst.opcode == Opcode.INVOKE_VIRTUAL &&
                    ref?.definingClass == "Lf/a;" &&
                    ref.name == "a" &&
                    ref.parameterTypes.map { t -> t.toString() } == listOf(browserReqType) &&
                    ref.returnType == browserResType
            }
            check(browserCallIndices.size == 1) {
                "Expected exactly one browser/create request in SDK profile setup; got " +
                    browserCallIndices.size
            }
            check(vmCls.methods.none { it.name == "morpheTraceBrowserCreateCall" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheTraceBrowserCreateCall",
                listOf(
                    ImmutableMethodParameter("Lf/a;", null, null),
                    ImmutableMethodParameter(browserReqType, null, null),
                ),
                browserResType,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(4),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_BROWSER_CREATE_HTTP_START"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-virtual {p0, p1}, Lf/a;->a($browserReqType)$browserResType
                move-result-object v0
                const-string v1, "SDK_BROWSER_CREATE_HTTP_END"
                invoke-static {v1}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-object v0
            """.trimIndent()) })
            val originalBrowserCall = browserOps[browserCallIndices.single()] as Instruction35c
            check(originalBrowserCall.registerCount == 2)
            requireNotNull(browserCreateMethod.implementation as? MutableMethodImplementation)
                .replaceInstruction(
                    browserCallIndices.single(),
                    BuilderInstruction35c(
                        Opcode.INVOKE_STATIC, 2,
                        originalBrowserCall.registerC, originalBrowserCall.registerD, 0, 0, 0,
                        ImmutableMethodReference(
                            vmType, "morpheTraceBrowserCreateCall",
                            listOf("Lf/a;", browserReqType), browserResType,
                        ),
                    ),
                )


            // OkHttp 4.11 EventListener default (NONE) methods have Call
            // parameters. Instrument their ENTRY without changing request,
            // response, socket selection, retries, exceptions, or threading.
            // Filter by the user/init encodedPath; never log actual URL, host,
            // IP addresses, body, headers, account identifiers or tokens.
            //
            // Pair with the ConnectInterceptor / CallServerInterceptor probes:
            // DNS_START..END, TCP_START..END/FAILED, TLS_START..END
            // (and connection reuse via CONNECTION_ACQUIRED).
            val eventListenerType = "Lokhttp3/EventListener;"
            val eventListenerCls = mutableClassDefBy(classDefBy(eventListenerType))
            val eventProbeSignatures = listOf(
                Triple("callStart", listOf("Lokhttp3/Call;"),
                    "USER_INIT_CALL_START"),
                Triple("callEnd", listOf("Lokhttp3/Call;"),
                    "USER_INIT_CALL_END"),
                Triple("proxySelectStart", listOf("Lokhttp3/Call;", "Lokhttp3/HttpUrl;"),
                    "USER_INIT_PROXY_SELECT_START"),
                Triple("proxySelectEnd", listOf("Lokhttp3/Call;", "Lokhttp3/HttpUrl;", "Ljava/util/List;"),
                    "USER_INIT_PROXY_SELECT_END"),
                Triple("dnsStart", listOf("Lokhttp3/Call;", "Ljava/lang/String;"),
                    "USER_INIT_DNS_START"),
                Triple("dnsEnd", listOf("Lokhttp3/Call;", "Ljava/lang/String;", "Ljava/util/List;"),
                    "USER_INIT_DNS_END"),
                Triple("connectStart", listOf("Lokhttp3/Call;", "Ljava/net/InetSocketAddress;", "Ljava/net/Proxy;"),
                    "USER_INIT_TCP_START"),
                Triple("connectEnd", listOf("Lokhttp3/Call;", "Ljava/net/InetSocketAddress;", "Ljava/net/Proxy;", "Lokhttp3/Protocol;"),
                    "USER_INIT_TCP_END"),
                Triple("connectFailed", listOf("Lokhttp3/Call;", "Ljava/net/InetSocketAddress;", "Ljava/net/Proxy;", "Lokhttp3/Protocol;", "Ljava/io/IOException;"),
                    "USER_INIT_TCP_FAILED"),
                Triple("secureConnectStart", listOf("Lokhttp3/Call;"),
                    "USER_INIT_TLS_START"),
                Triple("secureConnectEnd", listOf("Lokhttp3/Call;", "Lokhttp3/Handshake;"),
                    "USER_INIT_TLS_END"),
                Triple("connectionAcquired", listOf("Lokhttp3/Call;", "Lokhttp3/Connection;"),
                    "USER_INIT_CONNECTION_ACQUIRED"),
            )
            eventProbeSignatures.forEachIndexed { idx, (methodName, parameterTypes, event) ->
                val methods = eventListenerCls.methods.filter { method ->
                    method.name == methodName &&
                        method.parameterTypes.map { it.toString() } == parameterTypes &&
                        method.returnType == "V"
                }
                check(methods.size == 1) {
                    "Expected OkHttp EventListener.$methodName$parameterTypes, found " +
                        methods.size
                }
                val helperName = "morpheUserInitNetworkEvent" + idx
                check(vmCls.methods.none { it.name == helperName })
                vmCls.methods.add(ImmutableMethod(
                    vmType, helperName,
                    listOf(ImmutableMethodParameter("Lokhttp3/Call;", null, null)),
                    "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(3),
                ).toMutable().apply { addInstructions(0, """
                    invoke-interface {p0}, Lokhttp3/Call;->request()Lokhttp3/Request;
                    move-result-object v0
                    invoke-virtual {v0}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;
                    move-result-object v0
                    invoke-virtual {v0}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;
                    move-result-object v0
                    const-string v1, "user/api/v1/init"
                    invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                    move-result v2
                    if-eqz v2, :event_trace_done
                    const-string v0, "$event"
                    invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    :event_trace_done
                    return-void
                """.trimIndent()) })
                val method = methods.single()
                check(requireNotNull(method.implementation).instructions.isNotEmpty()) {
                    "No OkHttp EventListener implementation for $methodName"
                }
                method.addInstructions(
                    0, "invoke-static {p1}, $vmType->$helperName(Lokhttp3/Call;)V",
                )
            }

            // Terminal IOException callback from OkHttp (including DNS failures).
            // The exception type is useful for distinguishing UnknownHostException,
            // SocketTimeoutException, ConnectException, cancellation, etc.
            // Never log message/stack trace: those may contain URLs and identifiers.
            val eventFailedSignatures = listOf("Lokhttp3/Call;", "Ljava/io/IOException;")
            val eventFailedMethods = eventListenerCls.methods.filter { method ->
                method.name == "callFailed" &&
                    method.parameterTypes.map { it.toString() } == eventFailedSignatures &&
                    method.returnType == "V"
            }
            check(eventFailedMethods.size == 1) {
                "Expected OkHttp EventListener.callFailed, got " + eventFailedMethods.size
            }
            val eventFailedHelperName = "morpheUserInitCallFailed"
            check(vmCls.methods.none { it.name == eventFailedHelperName })
            vmCls.methods.add(ImmutableMethod(
                vmType, eventFailedHelperName,
                listOf(
                    ImmutableMethodParameter("Lokhttp3/Call;", null, null),
                    ImmutableMethodParameter("Ljava/io/IOException;", null, null),
                ),
                "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(4),
            ).toMutable().apply { addInstructions(0, """
                invoke-interface {p0}, Lokhttp3/Call;->request()Lokhttp3/Request;
                move-result-object v0
                invoke-virtual {v0}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;
                move-result-object v0
                invoke-virtual {v0}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;
                move-result-object v0
                const-string v1, "user/api/v1/init"
                invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :event_failed_done
                const-string v0, "USER_INIT_CALL_FAILED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-virtual {p1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
                move-result-object v0
                invoke-virtual {v0}, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;
                move-result-object v0
                const-string v1, "USER_INIT_ERROR_"
                invoke-virtual {v1, v0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :event_failed_done
                return-void
            """.trimIndent()) })
            val eventFailedMethod = eventFailedMethods.single()
            check(requireNotNull(eventFailedMethod.implementation).instructions.isNotEmpty()) {
                "No OkHttp EventListener.callFailed implementation"
            }
            eventFailedMethod.addInstructions(
                0, "invoke-static {p1, p2}, $vmType->$eventFailedHelperName(Lokhttp3/Call;Ljava/io/IOException;)V",
            )

            // Diagnose *where* user/init stalls inside OkHttp's networking.
            // RetryInterceptor.proceed() includes DNS, TCP, TLS, server headers.
            // ConnectInterceptor entry is before DNS/connection selection;
            // CallServerInterceptor entry is after connection establishment,
            // just before request/response I/O. Neither changes transport.
            val userInitTransportChainType = "Lokhttp3/Interceptor" + "$" + "Chain;"
            val userInitTransportStageProbes = listOf(
                Triple(
                    "Lokhttp3/internal/connection/ConnectInterceptor;",
                    "morpheUserInitConnectPhase",
                    "USER_INIT_CONNECT_PHASE_ENTER",
                ),
                Triple(
                    "Lokhttp3/internal/http/CallServerInterceptor;",
                    "morpheUserInitServerPhase",
                    "USER_INIT_SERVER_PHASE_ENTER",
                ),
            )
            userInitTransportStageProbes.forEach { (classType, helperName, event) ->
                val transportClass = mutableClassDefBy(classDefBy(classType))
                val interceptMethods = transportClass.methods.filter { method ->
                    method.name == "intercept" &&
                        method.parameterTypes.map { it.toString() } ==
                        listOf(userInitTransportChainType) &&
                        method.returnType == "Lokhttp3/Response;"
                }
                check(interceptMethods.size == 1) {
                    "Missing/ambiguous OkHttp network interceptor $classType: " +
                        interceptMethods.size
                }
                check(vmCls.methods.none { it.name == helperName })
                vmCls.methods.add(ImmutableMethod(
                    vmType, helperName,
                    listOf(ImmutableMethodParameter(userInitTransportChainType, null, null)),
                    "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(3),
                ).toMutable().apply { addInstructions(0, """
                    invoke-interface {p0}, $userInitTransportChainType->request()Lokhttp3/Request;
                    move-result-object v0
                    invoke-virtual {v0}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;
                    move-result-object v0
                    invoke-virtual {v0}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;
                    move-result-object v0
                    const-string v1, "user/api/v1/init"
                    invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :transport_probe_done
                    const-string v0, "$event"
                    invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    :transport_probe_done
                    return-void
                """.trimIndent()) })
                val intercept = interceptMethods.single()
                check(requireNotNull(intercept.implementation).instructions.isNotEmpty()) {
                    "OkHttp network interceptor has no bytecode: $classType"
                }
                // No original registers or exception handlers are modified.
                intercept.addInstructions(
                    0, "invoke-static {p1}, $vmType->$helperName($userInitTransportChainType)V",
                )
            }

            // SDK RetryInterceptor retries ONLY HTTP 503 responses; the first
            // two attempts each sleep 1000ms. Preserve original sleep and
            // interruption behavior, instrument the exact sleep call only.
            // Logs are intentionally payload/URL/status-body free. This
            // interceptor is also used by other SDK API clients.
            val retryType = "Ljp/tver/appsdk/core/RetryInterceptor;"
            val retryCls = mutableClassDefBy(classDefBy(retryType))
            val retryMethod = retryCls.methods.single {
                it.name == "intercept" &&
                    it.parameterTypes.map { type -> type.toString() } ==
                        listOf("Lokhttp3/Interceptor" + "$" + "Chain;") &&
                    it.returnType == "Lokhttp3/Response;"
            }
            val retryInsns = requireNotNull(retryMethod.implementation).instructions.toList()
            val sleepMatches = retryInsns.indices.filter { i ->
                val op = retryInsns[i]
                val ref = (op as? ReferenceInstruction)?.reference as? MethodReference
                op.opcode == Opcode.INVOKE_STATIC &&
                    ref?.definingClass == "Ljava/lang/Thread;" &&
                    ref.name == "sleep" && ref.parameterTypes.map { it.toString() } == listOf("J") &&
                    ref.returnType == "V"
            }
            check(sleepMatches.size == 1) {
                "Expected exactly one RetryInterceptor Thread.sleep(long); found " +
                    sleepMatches.size
            }
            check(vmCls.methods.none { it.name == "morpheRecordSdk503RetrySleep" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheRecordSdk503RetrySleep",
                listOf(ImmutableMethodParameter("J", null, null)), "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(3),
            ).toMutable().apply { addInstructions(0, """
                const-string v0, "SDK_HTTP_503_RETRY_SLEEP_BEGIN"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-static {p0, p1}, Ljava/lang/Thread;->sleep(J)V
                const-string v0, "SDK_HTTP_503_RETRY_SLEEP_END"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                return-void
            """.trimIndent()) })

            // Measure *each* OkHttp network-chain attempt on the deprecated
            // user/api/v1/init endpoint, with no URL/headers/body/token output.
            // This also records status codes, not just HTTP 503 sleep intervals.
            // If no start is observed until late: investigate Retrofit dispatch.
            // If start-to-end spans the delay: investigate network/transport.
            // If end occurs early: investigate body reading/JSON/coroutines.
            check(vmCls.methods.none { it.name == "morpheTraceUserInitHttpProceed" })
            vmCls.methods.add(ImmutableMethod(
                vmType, "morpheTraceUserInitHttpProceed",
                listOf(
                    ImmutableMethodParameter("Lokhttp3/Interceptor" + "$" + "Chain;", null, null),
                    ImmutableMethodParameter("Lokhttp3/Request;", null, null),
                ),
                "Lokhttp3/Response;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null, null, MutableMethodImplementation(9),
            ).toMutable().apply { addInstructions(0, """
                invoke-virtual {p1}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;
                move-result-object v0
                invoke-virtual {v0}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;
                move-result-object v0
                const-string v1, "user/api/v1/init"
                invoke-virtual {v0, v1}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :attempt_start_done
                const-string v3, "USER_INIT_HTTP_ATTEMPT_START"
                invoke-static {v3}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :attempt_start_done
                invoke-interface {p0, p1}, Lokhttp3/Interceptor${'$'}Chain;->proceed(Lokhttp3/Request;)Lokhttp3/Response;
                move-result-object v4
                if-eqz v2, :attempt_done
                const-string v3, "USER_INIT_HTTP_ATTEMPT_END"
                invoke-static {v3}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-virtual {v4}, Lokhttp3/Response;->code()I
                move-result v5
                invoke-static {v5}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;
                move-result-object v5
                const-string v3, "USER_INIT_HTTP_STATUS_"
                invoke-virtual {v3, v5}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v3
                invoke-static {v3}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :attempt_done
                return-object v4
            """.trimIndent()) })
            val retryProceedInsns =
                requireNotNull(retryMethod.implementation).instructions.toList()
            val retryProceedMatches = retryProceedInsns.indices.filter { i ->
                val op = retryProceedInsns[i]
                val ref = (op as? ReferenceInstruction)?.reference as? MethodReference
                op.opcode == Opcode.INVOKE_INTERFACE &&
                    ref?.definingClass == "Lokhttp3/Interceptor" + "$" + "Chain;" &&
                    ref.name == "proceed" &&
                    ref.parameterTypes.map { it.toString() } == listOf("Lokhttp3/Request;") &&
                    ref.returnType == "Lokhttp3/Response;"
            }
            check(retryProceedMatches.size == 2) {
                "Expected two RetryInterceptor chain.proceed calls, got " +
                    retryProceedMatches.size
            }
            val retryImpl = requireNotNull(
                retryMethod.implementation as? MutableMethodImplementation
            )
            retryProceedMatches.forEach { i ->
                val original = retryProceedInsns[i] as Instruction35c
                check(original.registerCount == 2)
                retryImpl.replaceInstruction(
                    i,
                    BuilderInstruction35c(
                        Opcode.INVOKE_STATIC, 2, original.registerC, original.registerD,
                        0, 0, 0,
                        ImmutableMethodReference(
                            vmType, "morpheTraceUserInitHttpProceed",
                            listOf("Lokhttp3/Interceptor" + "$" + "Chain;", "Lokhttp3/Request;"),
                            "Lokhttp3/Response;",
                        ),
                    ),
                )
            }

            val oldSleep = retryInsns[sleepMatches.single()] as Instruction35c
            check(oldSleep.registerCount == 2) {
                "RetryInterceptor sleep expects one long (two registers)"
            }
            requireNotNull(retryMethod.implementation as? MutableMethodImplementation)
                .replaceInstruction(
                    sleepMatches.single(),
                    BuilderInstruction35c(
                        Opcode.INVOKE_STATIC, 2,
                        oldSleep.registerC, oldSleep.registerD, 0, 0, 0,
                        ImmutableMethodReference(
                            vmType, "morpheRecordSdk503RetrySleep",
                            listOf("J"), "V",
                        ),
                    ),
                )


            // Network/suspension diagnostics: no behavior changes, no request
            // payloads, credentials, or result values are logged.
            // UserInitRepositoryImpl dispatches getUserInit() to Dispatchers.IO.
            // The worker SuspendLambda has label == 0 on initial invocation and
            // label == 1 when the suspended HTTP request resumes.
            val userInitRepositoryType =
                "Ljp/hamitv/hamiand1/feature/home/repository/impl/UserInitRepositoryImpl;"
            val userInitWorkerType = userInitRepositoryType.dropLast(1) +
                "$" + "getUserInit" + "$" + "2;"
            val transitionAwaitType = fragmentType.dropLast(1) +
                "$" + "transitionToHome" + "$" + "1;"
            fun installSuspendPhaseProbe(
                lambdaType: String,
                helperName: String,
                startingEvent: String,
                resumedEvent: String,
            ) {
                val lambdaCls = mutableClassDefBy(classDefBy(lambdaType))
                check(lambdaCls.methods.none { it.name == helperName })
                lambdaCls.methods.add(ImmutableMethod(
                    lambdaType, helperName,
                    listOf(ImmutableMethodParameter(lambdaType, null, null)),
                    "V", AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                    null, null, MutableMethodImplementation(3),
                ).toMutable().apply { addInstructions(0, """
                    iget v0, p0, $lambdaType->label:I
                    if-nez v0, :resumed_phase
                    const-string v1, "$startingEvent"
                    invoke-static {v1}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    return-void
                    :resumed_phase
                    const-string v1, "$resumedEvent"
                    invoke-static {v1}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                    return-void
                """.trimIndent()) })
                val invokeSuspend = lambdaCls.methods.single {
                    it.name == "invokeSuspend" && it.parameterTypes.size == 1
                }
                invokeSuspend.addInstructions(
                    0, "invoke-static {p0}, $lambdaType->$helperName($lambdaType)V"
                )
            }
            installSuspendPhaseProbe(
                userInitWorkerType, "morpheTraceUserInitCoroutine",
                "USER_INIT_API_COROUTINE_STARTED",
                "USER_INIT_API_COROUTINE_RESUMED",
            )
            installSuspendPhaseProbe(
                transitionAwaitType, "morpheTraceHomeWaitCoroutine",
                "HOME_SDK_COMPLETION_WAIT_STARTED",
                "HOME_SDK_COMPLETION_WAIT_RESUMED",
            )
            traceEntry(userInitRepositoryType, "getUserInit-IoAF18A", "USER_INIT_REPOSITORY_STARTED")
            traceEntry(fragmentType, "afterSplashVideo", "HOME_AFTER_SPLASH_WAIT_ENTER")
            traceEntry(fragmentType, "armHandOverFallback", "HOME_HANDOVER_FALLBACK_ARMED")
            traceEntry(fragmentType, "transitionByBootCompleted", "HOME_DESTINATION_DECISION_STARTED")
            traceEntry(fragmentType, "toHome", "HOME_TO_HOME_ENTER")

            traceEntry(vmType, "callUserInit", "USER_INIT_CALL")
            traceEntry(vmType, "onSessionReady", "SESSION_READY_SIGNAL")
            traceEntry(vmType, "onPrivacyPolicyGatePassed", "PRIVACY_GATE_PASSED")
            traceEntry(vmType, "awaitHomeLandingWithinGrace", "HOME_LANDING_GRACE_WAIT")
            // IMPORTANT: appliesHomeLoadGrace() already has a label-based conditional
            // inserted above. Prepending another instruction with addInstructions(0)
            // after that insertion breaks its encoded branch offset in Morphe
            // (Android VerifyError: target dex pc is not at instruction start).
            // Never call traceEntry on this method after the conditional injection.
            // HOME_GRACE_SKIPPED is still recorded by the bypass helper.
            traceEntry(
                fragmentType,
                "onSdkInitResponse" + "$" + "lambda" + "$" + "0",
                "USER_INIT_RESULT_PRIVACY_FETCH_START",
            )
            val privacyCallback = fragmentType.dropLast(1) + "$" + "onSdkInitResponse" + "$" + "1" + "$" + "1;"
            traceEntry(privacyCallback, "onSuccess", "PRIVACY_SETTINGS_SUCCESS")
            traceEntry(privacyCallback, "onError", "PRIVACY_SETTINGS_ERROR")
            val mainType = "Ljp/hamitv/hamiand1/tver/ui/activities/MainActivity;"
            traceEntry(mainType, "onCreate", "MAIN_ON_CREATE")
            traceEntry(mainType, "onResume", "MAIN_ON_RESUME")
            traceEntry(mainType, "initTabs", "MAIN_INIT_TABS")

            // Optional startup parallelism with a mandatory two-task join.
            // Only the transport of setting.json is started early. Original
            // SDK, profile and user initialization wait for Remote Config AND
            // TVerApp success, regardless of completion order.
            val parallelFrag = mutableClassDefBy(classDefBy(fragmentType))
            val parallelNames = listOf(
                "morpheParallelEnabled", "morpheRemoteReady",
                "morpheTverAppReady", "morpheTverAppContinued",
            )
            check(parallelNames.none { name -> parallelFrag.fields.any { it.name == name } })
            parallelNames.forEach { name ->
                parallelFrag.fields.add(ImmutableField(
                    fragmentType, name, "Z", AccessFlags.PRIVATE.value,
                    null, null, null,
                ).toMutableField())
            }
            fun addParallelMethod(name: String, regs: Int, body: String) {
                check(parallelFrag.methods.none { it.name == name })
                parallelFrag.methods.add(ImmutableMethod(
                    fragmentType, name, emptyList(), "V",
                    AccessFlags.PRIVATE.value, null, null,
                    MutableMethodImplementation(regs),
                ).toMutable().apply { addInstructions(0, body.trimIndent()) })
            }
            addParallelMethod("morpheStartTverAppEarly", 5, """
                invoke-virtual {p0}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
                move-result-object v0
                const-string v1, "morphe.tver.settings"
                const/4 v2, 0x0
                invoke-virtual {v0, v1, v2}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                move-result-object v0
                const-string v1, "parallel_remote_tverapp"
                invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
                move-result v1
                if-eqz v1, :parallel_start_done
                iput-boolean v1, p0, $fragmentType->morpheParallelEnabled:Z
                const-string v2, "TVER_APP_PARALLEL_INIT_START"
                invoke-static {v2}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                invoke-direct {p0}, $fragmentType->initializeSdkPlugins()V
                :parallel_start_done
                return-void
            """)
            addParallelMethod("morpheRemoteConfigReleased", 3, """
                iget-boolean v0, p0, $fragmentType->morpheParallelEnabled:Z
                if-nez v0, :parallel_gate
                invoke-direct {p0}, $fragmentType->initializeSdkPlugins()V
                return-void
                :parallel_gate
                const/4 v1, 0x1
                iput-boolean v1, p0, $fragmentType->morpheRemoteReady:Z
                const-string v0, "TVER_APP_PARALLEL_REMOTE_READY"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                iget-boolean v0, p0, $fragmentType->morpheTverAppReady:Z
                if-eqz v0, :parallel_release_done
                invoke-direct {p0}, $fragmentType->tverAppInitSuccessProcess()V
                :parallel_release_done
                return-void
            """)
            check(parallelFrag.methods.none { it.name == "morpheCanContinueTverApp" })
            parallelFrag.methods.add(ImmutableMethod(
                fragmentType, "morpheCanContinueTverApp", emptyList(), "Z",
                AccessFlags.PRIVATE.value, null, null,
                MutableMethodImplementation(3),
            ).toMutable().apply { addInstructions(0, """
                iget-boolean v0, p0, $fragmentType->morpheParallelEnabled:Z
                if-eqz v0, :parallel_continue_yes
                const/4 v1, 0x1
                iput-boolean v1, p0, $fragmentType->morpheTverAppReady:Z
                iget-boolean v0, p0, $fragmentType->morpheRemoteReady:Z
                if-eqz v0, :parallel_continue_no
                iget-boolean v0, p0, $fragmentType->morpheTverAppContinued:Z
                if-nez v0, :parallel_continue_no
                iput-boolean v1, p0, $fragmentType->morpheTverAppContinued:Z
                const-string v0, "TVER_APP_PARALLEL_JOINED"
                invoke-static {v0}, $vmType->morpheLogStartup(Ljava/lang/String;)V
                :parallel_continue_yes
                const/4 v0, 0x1
                return v0
                :parallel_continue_no
                const/4 v0, 0x0
                return v0
            """.trimIndent()) })
            val createViewMethod = parallelFrag.methods.single {
                it.name == "onCreateView" && it.parameterTypes.size == 3
            }
            val createOps = requireNotNull(createViewMethod.implementation).instructions.toList()
            val updateCalls = createOps.indices.filter { i ->
                val ref = (createOps[i] as? ReferenceInstruction)?.reference as? MethodReference
                ref?.name == "updateAppRemoteConfig" &&
                    ref.definingClass == vmType &&
                    createOps[i].opcode == Opcode.INVOKE_VIRTUAL
            }
            check(updateCalls.size == 1) {
                "Expected exactly one updateAppRemoteConfig call: ${updateCalls.size}"
            }
            // Start first, before updateAppRemoteConfig may complete inline
            // on a warm cache. Otherwise a synchronous completion could cause
            // original startup and parallel startup to run twice.
            createViewMethod.addInstructions(
                updateCalls.single(),
                "invoke-direct {p0}, $fragmentType->morpheStartTverAppEarly()V",
            )
            val remoteReadyMethod = parallelFrag.methods.single {
                it.name == "onCreateView" + "$" + "lambda" + "$" + "0"
            }
            val gateOps = requireNotNull(remoteReadyMethod.implementation).instructions.toList()
            val initCalls = gateOps.indices.filter { i ->
                val inst = gateOps[i]
                val ref = (inst as? ReferenceInstruction)?.reference as? MethodReference
                inst.opcode == Opcode.INVOKE_DIRECT && ref?.definingClass == fragmentType &&
                    ref.name == "initializeSdkPlugins"
            }
            check(initCalls.size == 1) {
                "Expected one initializeSdkPlugins in remote callback: ${initCalls.size}"
            }
            val oldInit = gateOps[initCalls.single()] as Instruction35c
            requireNotNull(remoteReadyMethod.implementation as? MutableMethodImplementation)
                .replaceInstruction(
                    initCalls.single(),
                    BuilderInstruction35c(
                        Opcode.INVOKE_DIRECT, 1, oldInit.registerC, 0, 0, 0, 0,
                        ImmutableMethodReference(
                            fragmentType, "morpheRemoteConfigReleased", emptyList(), "V",
                        ),
                    ),
                )
            val successMethod = parallelFrag.methods.single {
                it.name == "tverAppInitSuccessProcess" && it.parameterTypes.isEmpty()
            }
            check(requireNotNull(successMethod.implementation).registerCount >= 2)
            successMethod.addInstructions(0, """
                invoke-direct {p0}, $fragmentType->morpheCanContinueTverApp()Z
                move-result v0
                if-nez v0, :morphe_app_success_original
                return-void
                :morphe_app_success_original
                nop
            """.trimIndent())


        }

    }
}
