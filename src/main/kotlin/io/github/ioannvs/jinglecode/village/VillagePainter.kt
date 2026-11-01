package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.sprites.towardsSeam
import java.awt.Color
import kotlin.math.max

/** Width of the mockup's village canvas: the scenes are composed for it. */
internal const val MOCKUP_WIDTH = 460

/** Height of the village, logical pixels: the mockup's canvas, `H`. */
internal const val VILLAGE_ROWS = 64

/** Where a house is in the picture, columns [x0]..[x1] and rows [y0]..[y1]; [index] counts houses in drawing order. */
internal data class HouseBox(val index: Int, val x0: Int, val y0: Int, val x1: Int, val y1: Int, val hasChimney: Boolean) {
    fun contains(x: Int, y: Int): Boolean = x in x0..x1 && y in y0..y1
}

/**
 * One frame of the village being drawn: the mockup's globals (`frame`, `evening`, `seed`) and its
 * `px`/`rect`, which round fractional coordinates the JavaScript way, so the scenes port line by line.
 *
 * The mockup is [MOCKUP_WIDTH] wide, an editor is not. [m] takes a mockup column to the picture: the
 * scene's [elastic] stretches of empty snow grow or shrink, the groups of houses keep their spacing, and
 * what still does not fit is cut off at the left. Between elastic stretches columns only shift by a
 * whole number, so the groups are drawn pixel for pixel as in the mockup.
 *
 * [stars] says whether stars may hang in the sky; [leftEdge] is the snow height where the Project
 * panel's drift ends (see `SnowSeam`); the houses in [stoked] had their stove lit by a click.
 */
internal class VillagePainter(
    private val canvas: PixelCanvas,
    val width: Int,
    val seed: Int,
    val frame: Int,
    val evening: Boolean,
    val stars: Boolean,
    private val leftEdge: Int?,
    elastic: List<IntRange>,
    private val stoked: Set<Int>,
) {
    private val map = ColumnMap(elastic, width)

    /** The houses drawn so far, for clicks. */
    val houses = ArrayList<HouseBox>()

    /** Mockup column [x] in the picture. */
    fun m(x: Int): Int = jsRound(map.at(x.toDouble()))

    /** Mockup column [x], also a fraction of one, in the picture. */
    fun m(x: Double): Int = jsRound(map.at(x))

    /** Picture column [x] in the mockup, for things laid along the whole width: ground, mountains, forest. */
    fun back(x: Int): Double = map.back(x.toDouble())

    fun px(x: Number, y: Number, color: Color) = canvas.px(jsRound(x.toDouble()), jsRound(y.toDouble()), color)

    fun rect(x: Number, y: Number, w: Number, h: Number, color: Color) {
        if (w.toDouble() <= 0 || h.toDouble() <= 0) return
        canvas.rect(jsRound(x.toDouble()), jsRound(y.toDouble()), jsRound(w.toDouble()), jsRound(h.toDouble()), color)
    }

    /** A line of the mockup, [line] of a mockup column, as a function of the picture's columns. */
    fun along(line: (Double) -> Double): (Int) -> Double = { x -> line(back(x)) }

    /** The foreground snow line [line], as [along], but laid to the seam height at the left edge. */
    fun ground(line: (Double) -> Double): Ground = Ground(line)

    /** A snow line: called with a picture column like [along]; [atMockup] takes a mockup column, for the residents. */
    inner class Ground(private val line: (Double) -> Double) : (Int) -> Double {
        override fun invoke(x: Int): Double = seam(line(back(x)), x)

        fun atMockup(x: Double): Double = seam(line(x), jsRound(map.at(x)))

        private fun seam(y: Double, column: Int): Double =
            if (leftEdge == null) y else VILLAGE_ROWS - towardsSeam(VILLAGE_ROWS - y, leftEdge, column)
    }

    /** Whether house [index] got a click: then its chimney smokes, and thick. */
    fun isStoked(index: Int): Boolean = index in stoked
}

/**
 * Mockup columns to picture columns: piecewise linear, the [elastic] stretches scaled alike, the rest
 * kept. Breakpoints land on whole columns, so fixed stretches only shift. If even squeezed the scene
 * is wider than [width], its left part is cut off: every scene has its main point on the right.
 */
internal class ColumnMap(elastic: List<IntRange>, width: Int) {
    private val from: DoubleArray
    private val to: DoubleArray

    init {
        val cuts = buildList {
            add(0)
            elastic.forEach { add(it.first); add(it.last) }
            add(MOCKUP_WIDTH)
        }
        val stretch = elastic.sumOf { it.last - it.first }
        val fixed = MOCKUP_WIDTH - stretch
        val factor = if (stretch == 0) 1.0 else max(MIN_SQUEEZE, (width - fixed).toDouble() / stretch)
        from = DoubleArray(cuts.size) { cuts[it].toDouble() }
        to = DoubleArray(cuts.size)
        var grown = 0.0
        for (i in 1 until cuts.size) {
            val length = cuts[i] - cuts[i - 1]
            // Segments alternate: fixed, elastic, fixed, ...
            val elasticSegment = i % 2 == 0
            if (elasticSegment) grown += length * factor
            val fixedSoFar = (1..i).filter { it % 2 == 1 }.sumOf { cuts[it] - cuts[it - 1] }
            to[i] = (fixedSoFar + jsRound(grown)).toDouble()
        }
        val cut = max(0.0, to.last() - width)
        for (i in to.indices) to[i] -= cut
    }

    fun at(x: Double): Double = convert(x, from, to)

    fun back(x: Double): Double = convert(x, to, from)

    private fun convert(x: Double, a: DoubleArray, b: DoubleArray): Double {
        if (x <= a.first()) return b.first() + (x - a.first())
        if (x >= a.last()) return b.last() + (x - a.last())
        var i = 1
        while (x > a[i]) i++
        val span = a[i] - a[i - 1]
        return if (span == 0.0) b[i] else b[i - 1] + (x - a[i - 1]) * (b[i] - b[i - 1]) / span
    }

    private companion object {
        /** How narrow an elastic stretch may get before the scene is cut. */
        const val MIN_SQUEEZE = 0.25
    }
}
