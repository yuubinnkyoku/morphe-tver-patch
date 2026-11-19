package org.example.tverpatches

import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21s
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val LIVE_RESOLVER =
    "Ljp/hamitv/hamiand1/tver/legacy/player/live/LiveVideoQualityResolver;"
private const val LIVE_VIDEO_QUALITY =
    "Ljp/hamitv/hamiand1/tver/legacy/player/widgets/player/VideoQuality;"
private const val LIVE_VIDEO_SIZE =
    "Ljp/hamitv/hamiand1/tver/legacy/player/live/LiveVideoSize;"

// 1280x720 -> 1920x1080. MIDDLE (960x540) and LOW (640x360) stay stock.
private const val STOCK_CEILING_WIDTH = 1280
private const val STOCK_CEILING_HEIGHT = 720
private const val FULL_HD_WIDTH = 1920
private const val FULL_HD_HEIGHT = 1080

/**
 * Raises the realtime-live / DVR quality ceiling from 720p to 1080p.
 *
 * TVer's legacy live controller (LivePlaybackControllerView) resolves the
 * menu quality (AUTO/HIGH/MIDDLE/LOW) to a fixed max video size via
 * LiveVideoQualityResolver.resolve and forwards it to the Streaks player as
 * setMaxVideoSize. AUTO, NONE and HIGH all resolve to 1280x720, so live and
 * DVR streams can never use a rendition above 720p even when the CDN
 * publishes one.
 *
 * This patch rewrites only the 1280x720 pair in resolve() to 1920x1080,
 * mirroring the VOD ceiling lift in TVerQualityPickerPatch. MIDDLE and LOW
 * keep their original sizes, no registers change and no branches move. If a
 * broadcaster does not publish a 1080p rendition, the player simply selects
 * the best available one, as before: nothing is spoofed to the server.
 */
@Suppress("unused")
val tverLiveQualityCeilingPatch = bytecodePatch(
    name = "TVer: live stream 1080p ceiling (7.2.1/7.4.1)",
    description = "Raise the realtime-live and DVR AUTO/HIGH max video size from 720p (1280x720) to 1080p (1920x1080). MEDIUM/LOW stay unchanged, and playback falls back to the best rendition the broadcaster actually publishes.",
    default = true,
) {
    compatibleWith("jp.hamitv.hamiand1"("7.2.1", "7.4.1"))

    execute {
        val resolverClass = mutableClassDefBy(classDefBy(LIVE_RESOLVER))
        val resolveMethod = resolverClass.methods.single {
            it.name == "resolve" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == LIVE_VIDEO_QUALITY &&
                it.returnType.toString() == LIVE_VIDEO_SIZE
        }
        val implementation =
            requireNotNull(resolveMethod.implementation) as MutableMethodImplementation

        // Locate the single 1280x720 constant pair of the AUTO/NONE/HIGH
        // return branch. Accept both const/16 (7.4.1) and const (31i) forms
        // so a different compiler output still patches, but fail loudly if
        // the method does not contain exactly one adjacent pair.
        fun registerWithLiteral(literal: Int): List<Int> =
            implementation.instructions.withIndex()
                .filter { (_, instruction) ->
                    (instruction.opcode == Opcode.CONST_16 ||
                        instruction.opcode == Opcode.CONST) &&
                        (instruction as NarrowLiteralInstruction).narrowLiteral == literal
                }
                .map { it.index }

        val widthIndexes = registerWithLiteral(STOCK_CEILING_WIDTH)
        val heightIndexes = registerWithLiteral(STOCK_CEILING_HEIGHT)
        check(widthIndexes.size == 1 && heightIndexes.size == 1) {
            "Expected exactly one $STOCK_CEILING_WIDTH and one $STOCK_CEILING_HEIGHT " +
                "literal in LiveVideoQualityResolver.resolve; got " +
                "${widthIndexes.size} and ${heightIndexes.size}"
        }
        val widthIndex = widthIndexes.single()
        val heightIndex = heightIndexes.single()
        check(heightIndex == widthIndex + 1) {
            "Expected the 720p height literal right after the 1280 width literal; " +
                "got offsets $widthIndex and $heightIndex"
        }

        fun replaceLiteral(index: Int, literal: Int) {
            val instruction = implementation.instructions.elementAt(index)
            val register = (instruction as OneRegisterInstruction).registerA
            implementation.replaceInstruction(
                index,
                if (instruction.opcode == Opcode.CONST_16) {
                    BuilderInstruction21s(Opcode.CONST_16, register, literal)
                } else {
                    BuilderInstruction31i(Opcode.CONST, register, literal)
                },
            )
        }

        replaceLiteral(widthIndex, FULL_HD_WIDTH)
        replaceLiteral(heightIndex, FULL_HD_HEIGHT)
    }
}
