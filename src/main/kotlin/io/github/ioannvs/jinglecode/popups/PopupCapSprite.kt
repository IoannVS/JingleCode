package io.github.ioannvs.jinglecode.popups

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Holly sprig size, logical pixels (`holly`, 13×10 in the mockup). */
internal const val HOLLY_W = 13
internal const val HOLLY_H = 10

/**
 * Mockup `popupCap` (`docs/mockup/jingle-code-windows.html`): a thin snow cap [w] logical pixels wide and up to
 * [h] high, fading out towards both ends; its box starts at ([x0], [y0]), so the snow lies on row `y0 + h - 1`.
 * Now and then a sparkle. [t] is the animator tick, the mockup's `frame`.
 */
internal fun PixelCanvas.drawPopupCap(x0: Int, y0: Int, w: Int, h: Int, t: Long) {
    for (x in 0 until w) {
        val edge = min(x, w - 1 - x)
        val fade = min(1.0, edge / 6.0)
        val hh = max(0, jsRound((h - 1 + sin(x / 8.0) * 0.8 + sin(x / 3.0) * 0.4) * fade))
        if (hh > 0) {
            rect(x0 + x, y0 + h - hh, 1, hh, JinglePalette.SNOW)
            px(x0 + x, y0 + h - hh, JinglePalette.WHITE)
        }
    }
    if (t % 30 < 3) px(x0 + floor(hash(floor(t / 30.0), 2.0) * w).toInt(), y0 + h - 2, JinglePalette.WHITE)
}

/**
 * Mockup `holly`: a holly sprig with berries, [HOLLY_W]×[HOLLY_H] from ([x0], [y0]);
 * a berry glints for 10 frames out of 20.
 */
internal fun PixelCanvas.drawHolly(x0: Int, y0: Int, t: Long) {
    fun px(x: Int, y: Int, color: java.awt.Color) = this.px(x0 + x, y0 + y, color)
    fun rect(x: Int, y: Int, w: Int, h: Int, color: java.awt.Color) = this.rect(x0 + x, y0 + y, w, h, color)
    val lf = JinglePalette.FIR
    val lf2 = JinglePalette.FIR_LIGHT
    rect(1, 4, 5, 2, lf); px(0, 3, lf); px(3, 3, lf); px(6, 4, lf); px(2, 6, lf2); px(5, 6, lf)
    rect(7, 2, 5, 2, lf2); px(6, 1, lf2); px(9, 1, lf2); px(12, 2, lf2); px(8, 4, lf); px(11, 4, lf2)
    rect(5, 5, 2, 2, JinglePalette.HOLLY_BERRY)
    px(7, 6, JinglePalette.HOLLY_BERRY_DARK)
    px(6, 7, JinglePalette.HOLLY_BERRY_DARK)
    px(5, 5, if (t % 20 < 10) JinglePalette.HOLLY_BERRY_GLINT else JinglePalette.HOLLY_BERRY)
}
