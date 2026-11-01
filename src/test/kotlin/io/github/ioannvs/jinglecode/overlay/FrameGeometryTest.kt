package io.github.ioannvs.jinglecode.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Rectangle

class FrameGeometryTest {

    @Test
    fun `the top edge runs along the top row of touching islands`() {
        val project = Rectangle(0, 40, 300, 600)
        val editor = Rectangle(302, 40, 900, 400)
        val terminal = Rectangle(302, 442, 900, 198) // below the editor: not in the top row
        assertEquals(listOf(Rectangle(0, 40, 1202, 0)), FrameGeometry.topEdges(listOf(terminal, editor, project), touch = 12))
    }

    @Test
    fun `islands apart give separate edges`() {
        val left = Rectangle(0, 40, 300, 600)
        val right = Rectangle(900, 42, 300, 600)
        assertEquals(
            listOf(Rectangle(0, 40, 300, 0), Rectangle(900, 40, 300, 0)),
            FrameGeometry.topEdges(listOf(right, left), touch = 12),
        )
    }

    @Test
    fun `scenes stand on the bottom of the island, also under a scroll bar, but a pane ending higher keeps its own`() {
        val body = Rectangle(6, 46, 900, 588)
        assertEquals("the editor above its horizontal scroll bar", 634, FrameGeometry.ground(Rectangle(6, 80, 900, 544), body))
        assertEquals("a pane reaching into the border", 634, FrameGeometry.ground(Rectangle(6, 80, 900, 560), body))
        assertEquals("an editor split above another", 340, FrameGeometry.ground(Rectangle(6, 80, 900, 260), body))
        assertEquals("no Islands UI", 624, FrameGeometry.ground(Rectangle(6, 80, 900, 544), null))
    }

    @Test
    fun `no islands, no edge`() {
        assertEquals(emptyList<Rectangle>(), FrameGeometry.topEdges(emptyList(), touch = 12))
    }
}
