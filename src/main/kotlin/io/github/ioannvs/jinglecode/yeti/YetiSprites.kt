package io.github.ioannvs.jinglecode.yeti

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color

/** The yeti, logical pixels: hunched, long arms hanging down to the ground, big feet. */
internal const val YETI_W = 9
internal const val YETI_H = 9

/** How far right his waving hand reaches. */
internal const val YETI_REACH = 11

/** A footprint on the line, and the footprint of the notification's icon. */
internal const val PRINT_W = 3
internal const val PAW = 5

/**
 * The yeti in [pose], his top left at ([x0], [y0]) when he stands fully out of the line, `W` fur, `F` his blue face
 * (the mockup's colours), `K` coal eyes, `w` claws (Ivan's pick of the variants):
 * ```
 * ...WWW...
 * ..WFFFW..
 * ..WKFKW..
 * .WWFFFWW.
 * W.WWWWW.W
 * W.WWWWW.W
 * w.WW.WW.w
 * ..WW.WW..
 * .WWW.WWW.
 * ```
 * Waving, his right arm goes up instead of hanging.
 */
internal fun PixelCanvas.drawYeti(x0: Int, y0: Int, pose: YetiPose) {
    val x = x0
    val y = y0 + pose.sunk
    val fur = JinglePalette.YETI_FUR
    val claw = JinglePalette.YETI_CLAW
    rect(x + 3, y, 3, 1, fur)
    rect(x + 2, y + 1, 1, 2, fur)
    rect(x + 6, y + 1, 1, 2, fur)
    rect(x + 3, y + 1, 3, 3, JinglePalette.YETI_FACE)
    val eyes = when (pose.eyes) {
        YetiEyes.AHEAD -> listOf(3, 5)
        YetiEyes.LEFT -> listOf(3, 4)
        YetiEyes.RIGHT -> listOf(4, 5)
        YetiEyes.SHUT -> emptyList()
    }
    eyes.forEach { px(x + it, y + 2, JinglePalette.COAL) }
    rect(x + 1, y + 3, 2, 1, fur)
    rect(x + 6, y + 3, 2, 1, fur)
    rect(x + 2, y + 4, 5, 2, fur)
    for (leg in listOf(2, 5)) rect(x + leg, y + 6, 2, 2, fur)
    rect(x + 1, y + 8, 3, 1, fur)
    rect(x + 5, y + 8, 3, 1, fur)
    // The left arm hangs to the ground; the right one too, unless he waves.
    rect(x, y + 4, 1, 2, fur)
    px(x, y + 6, claw)
    when {
        pose.move == YetiMove.STAND -> { rect(x + 8, y + 4, 1, 2, fur); px(x + 8, y + 6, claw) }
        pose.step % 2 == 0 -> { rect(x + 8, y + 1, 1, 3, fur); px(x + 8, y, claw) }
        else -> { px(x + 8, y + 3, fur); px(x + 9, y + 2, fur); px(x + 10, y + 1, claw) }
    }
}

/** The puff of snow the yeti vanishes in, frame [frame] of [YetiTimeline.PUFF_STEPS], where he stood at ([x0], [y0]). */
internal fun PixelCanvas.drawPuff(x0: Int, y0: Int, frame: Int) {
    // It flies apart: a dense cloud on his body first, then fewer flakes farther out.
    val spread = 1 + frame * 2
    val keep = 0.7 - frame * 0.25
    for (dy in -spread until YETI_H + 1) {
        for (dx in -spread until YETI_W + spread) {
            if (hash(dx * 7 + frame, dy * 13 + 5) < keep * if (dy in 0 until YETI_H && dx in 1 until YETI_W - 1) 1.0 else 0.35) {
                px(x0 + dx, y0 + dy, if (hash(dx, dy + frame) < 0.5) JinglePalette.SNOW else JinglePalette.WHITE)
            }
        }
    }
}

/** A footprint on a line [rows] logical pixels tall, [x] from the left: the lower foot near its bottom, the upper a little above. */
internal fun PixelCanvas.drawLinePrint(x: Int, rows: Int, upper: Boolean, color: Color = JinglePalette.ICE_TIP) {
    val y = rows - 4 - if (upper) 2 else 0
    px(x, y, color)
    px(x + 2, y, color)
    rect(x, y + 1, PRINT_W, 2, color)
}

/** A footprint, [PAW]×[PAW] from ([x], [y]): three toes over a pad. */
internal fun PixelCanvas.drawPaw(x: Int, y: Int, color: Color = JinglePalette.ICE_TIP) {
    px(x, y, color)
    px(x + 2, y, color)
    px(x + 4, y, color)
    rect(x + 1, y + 2, 3, 1, color)
    rect(x, y + 3, 5, 1, color)
    rect(x + 1, y + 4, 3, 1, color)
}
