package io.github.ioannvs.jinglecode.overlay

import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.PathIterator

/**
 * Cuts a clip made of rectangles (the overlay's strips minus popups) into rectangles that don't overlap.
 *
 * Under a clip of any other shape `PixelCanvas` draws through a buffer as big as the clip's bounds: for an
 * L of two garland strips that is most of the window, cleared and copied once per garland line. Clipped to
 * one rectangle at a time it draws straight away.
 */
internal object ClipPieces {

    /** [area] as rectangles that don't overlap, top to bottom; `null` if it has curves or slanted edges. */
    fun of(area: Area): List<Rectangle>? {
        if (area.isEmpty) return emptyList()
        if (area.isRectangular) return listOf(area.bounds)
        val xs = sortedSetOf<Int>()
        val ys = sortedSetOf<Int>()
        val iterator = area.getPathIterator(null)
        val coords = DoubleArray(6)
        var lastX = 0.0
        var lastY = 0.0
        while (!iterator.isDone) {
            when (iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> Unit
                PathIterator.SEG_LINETO -> if (coords[0] != lastX && coords[1] != lastY) return null
                PathIterator.SEG_CLOSE -> Unit
                else -> return null
            }
            if (coords[0] != coords[0].toInt().toDouble() || coords[1] != coords[1].toInt().toDouble()) return null
            lastX = coords[0]
            lastY = coords[1]
            xs += coords[0].toInt()
            ys += coords[1].toInt()
            iterator.next()
        }
        val columns = xs.toList()
        val rows = ys.toList()
        val pieces = ArrayList<Rectangle>()
        // The pieces of the band above, by their left and right edges, to grow down while a band has the same run.
        var open = HashMap<Pair<Int, Int>, Rectangle>()
        for (j in 0 until rows.size - 1) {
            val top = rows[j]
            val bottom = rows[j + 1]
            val middle = (top + bottom) / 2.0
            val next = HashMap<Pair<Int, Int>, Rectangle>()
            var start: Int? = null
            for (i in 0 until columns.size) {
                val inside = i < columns.size - 1 && area.contains((columns[i] + columns[i + 1]) / 2.0, middle)
                if (inside && start == null) start = columns[i]
                if (!inside && start != null) {
                    val run = start to columns[i]
                    val piece = open[run]?.also { it.height = bottom - it.y }
                        ?: Rectangle(run.first, top, run.second - run.first, bottom - top).also(pieces::add)
                    next[run] = piece
                    start = null
                }
            }
            open = next
        }
        return pieces
    }
}
