package io.github.ioannvs.jinglecode.icicles

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color
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

/** Length of the icicle in [column] (an odd one) of [drawRootedIcicles], or `null` if it has none. */
internal fun icicleLength(column: Int): Int? =
    if (hash(column, 7) >= 0.35) 1 + floor(hash(column, 8) * ICICLE_MAX).toInt() else null

/** How deep the roots of [drawRootedIcicles] go above row 0, logical pixels. */
internal const val ICICLE_ROOT = 2

/**
 * The same icicles as [drawIcicles], but grown into the edge they hang from, as under a roof: right above
 * row 0 their roots spread into an uneven [ledge] where neighbours merge, touching the edge with no gap,
 * and the longer ones go one more row up as a thin [root]. Below row 0 they hang as before.
 * The roots take their own colors, so they can melt into what they grow from. Columns where [skip] is true
 * stay empty, ledge included. [hanging] says how many rows of an icicle `(column, length)` hang now:
 * fewer while it has fallen and grows back (see [IcicleFall]); the roots and the ledge stay.
 */
internal fun PixelCanvas.drawRootedIcicles(
    length: Int,
    t: Int,
    root: Color = JinglePalette.SNOW,
    ledge: Color = JinglePalette.ICE,
    skip: (Int) -> Boolean = { false },
    hanging: (Int, Int) -> Int = { _, len -> len },
) {
    var x = 1
    while (x < length) {
        val len = if (skip(x)) null else icicleLength(x)
        if (len != null) {
            val spread = if (hash(x, 9) < 0.5) 1 else 2
            for (column in maxOf(0, x - spread)..minOf(length - 1, x + spread)) {
                if (!skip(column)) px(column, -1, ledge)
            }
            if (len >= 3) for (y in -ICICLE_ROOT until -1) px(x, y, root)
            val rows = hanging(x, len).coerceIn(0, len)
            for (y in 0 until rows) px(x, y, if (y == rows - 1) JinglePalette.ICE_TIP else JinglePalette.ICE)
            if (rows == len && hash(x, t / 3) > 0.97) px(x, len - 1, JinglePalette.WHITE)
        }
        x += 2
    }
}
