package io.github.ioannvs.jinglecode.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D

class ClipPiecesTest {

    private fun area(vararg rects: Rectangle, minus: List<Rectangle> = emptyList()) = Area().apply {
        rects.forEach { add(Area(it)) }
        minus.forEach { subtract(Area(it)) }
    }

    /** The pieces cover exactly [area] and don't overlap. */
    private fun assertCovers(area: Area, pieces: List<Rectangle>) {
        val union = Area()
        for ((i, a) in pieces.withIndex()) {
            for (b in pieces.drop(i + 1)) assertTrue("$a overlaps $b", a.intersection(b).isEmpty)
            union.add(Area(a))
        }
        assertTrue(union.equals(area))
    }

    @Test
    fun `a rectangle is one piece`() {
        assertEquals(listOf(Rectangle(10, 20, 30, 40)), ClipPieces.of(area(Rectangle(10, 20, 30, 40))))
    }

    @Test
    fun `an L of two garland strips is two pieces`() {
        val l = area(Rectangle(100, 0, 10, 300), Rectangle(0, 290, 400, 10))
        val pieces = ClipPieces.of(l)!!
        assertEquals(2, pieces.size)
        assertCovers(l, pieces)
    }

    @Test
    fun `a strip with a popup over its middle is cut around it`() {
        val strip = area(Rectangle(0, 0, 300, 20), minus = listOf(Rectangle(100, 5, 50, 10)))
        val pieces = ClipPieces.of(strip)!!
        assertEquals(4, pieces.size)
        assertCovers(strip, pieces)
    }

    @Test
    fun `strips apart stay apart`() {
        val two = area(Rectangle(0, 0, 10, 10), Rectangle(50, 50, 10, 10))
        val pieces = ClipPieces.of(two)!!
        assertEquals(listOf(Rectangle(0, 0, 10, 10), Rectangle(50, 50, 10, 10)), pieces)
    }

    @Test
    fun `rounded corners are not rectangles`() {
        assertNull(ClipPieces.of(Area(RoundRectangle2D.Float(0f, 0f, 100f, 100f, 20f, 20f))))
    }

    @Test
    fun `nothing is no pieces`() {
        assertEquals(emptyList<Rectangle>(), ClipPieces.of(Area()))
    }
}
