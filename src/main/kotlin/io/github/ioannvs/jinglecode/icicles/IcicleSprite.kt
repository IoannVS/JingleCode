package io.github.ioannvs.jinglecode.icicles

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.floor

/** The longest icicle, logical pixels, as in the mockup. */
internal const val ICICLE_MAX = 4

/**
 * Mockup icicles from under the toolbar (`drawGaps`, "сосульки из-под тулбара"): one every other column
 * along [length] logical pixels from the origin, about a third of them missing, 1 to [ICICLE_MAX] pixels
 * long, snow on top, ice below, a blue tip that now and then glints white. They hang down from row 0.
 * Columns where [skip] is true stay empty, for the garland's gap lines. [t] is the animator tick.
 */
internal fun PixelCanvas.drawIcicles(length: Int, t: Int, skip: (Int) -> Boolean = { false }) {
    var x = 1
    while (x < length) {
        if (!skip(x) && hash(x, 7) >= 0.35) {
            val len = 1 + floor(hash(x, 8) * ICICLE_MAX).toInt()
            for (y in 0 until len) {
                val color = when (y) {
                    0 -> JinglePalette.SNOW
                    len - 1 -> JinglePalette.ICE_TIP
                    else -> JinglePalette.ICE
                }
                px(x, y, color)
            }
            if (hash(x, t / 3) > 0.97) px(x, len - 1, JinglePalette.WHITE)
        }
        x += 2
    }
}
