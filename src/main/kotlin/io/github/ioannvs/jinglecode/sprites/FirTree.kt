package io.github.ioannvs.jinglecode.sprites

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas

/**
 * Mockup fir tree (`drawVillage`'s trees): a cone of light and dark green bands with snow on the left
 * of every fifth row, [scale] of 18 pixels high, its trunk end on row [base] at column [x]. A
 * [decorated] one has a few red baubles and a warm light on top, nothing more. The village and the
 * Project panel's drift draw the same trees, so the snow line reads as one across the window.
 */
internal fun PixelCanvas.drawFirTree(x: Int, base: Int, scale: Double, decorated: Boolean = false) {
    val height = PixelCanvas.jsRound(18 * scale)
    for (r in 0 until height) {
        val half = (r / 2.5).toInt()
        rect(x - half, base - height + r, half * 2 + 1, 1, if (r % 5 < 2) JinglePalette.FIR_LIGHT else JinglePalette.FIR)
    }
    var r = 0
    while (r < height) {
        rect(x - (r / 2.5).toInt(), base - height + r, (r / 2.5).toInt() + 1, 1, JinglePalette.SNOW)
        r += 5
    }
    if (!decorated) return
    for (row in listOf(7, 11, 15)) {
        if (row >= height) break
        val half = (row / 2.5).toInt()
        px(x + if (row % 2 == 1) half - 1 else -half + 1, base - height + row, JinglePalette.SANTA_RED)
    }
    px(x, base - height - 1, JinglePalette.WARM_WHITE)
}
