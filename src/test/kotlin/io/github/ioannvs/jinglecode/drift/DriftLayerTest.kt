package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.overlay.ProjectPanel
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage

class DriftLayerTest {

    private val scale = OverlayScale(2f)

    private val layout = FrameLayout(
        vertical = emptyList(), horizontal = emptyList(), top = emptyList(), statusBand = null, occluders = emptyList(),
        projectPanel = ProjectPanel(body = Rectangle(6, 46, 288, 588), arc = 20, content = Rectangle(6, 80, 288, 550)),
    )

    @Test
    fun `the scene always sits at the bottom of the panel, edge to edge`() {
        assertEquals(Rectangle(6, 630 - 56, 288, 56), DriftLayer.scene(layout, scale))
    }

    @Test
    fun `in the Commit panel the scene stands at the bottom of the tree of changes, above the commit message`() {
        val commit = ProjectPanel(body = Rectangle(6, 46, 288, 588), arc = 20, content = Rectangle(6, 80, 288, 300))
        assertEquals(Rectangle(6, 380 - 56, 288, 56), DriftLayer.scene(commit, scale))
    }

    @Test
    fun `no Project panel, no scene`() {
        assertNull(DriftLayer.scene(FrameLayout(emptyList(), emptyList(), emptyList(), null, emptyList()), scale))
    }

    private val empty = Color(0x123456)

    private val seam = 9

    private fun draw(): BufferedImage {
        val width = 140
        val image = BufferedImage(width, SCENE_ROWS + 1, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = empty
        g.fillRect(0, 0, image.width, image.height)
        PixelCanvas.paint(g, 0, 0) { drawProjectScene(width, f = 1, t = 0, rightEdge = seam) }
        g.dispose()
        return image
    }

    @Test
    fun `the drift is solid from edge to edge, tall at the window edge, at the seam height on the right`() {
        val image = draw()
        fun height(x: Int) = (SCENE_ROWS downTo 0).takeWhile {
            val c = Color(image.getRGB(x, it))
            c == JinglePalette.SNOW || c == JinglePalette.WHITE || c == JinglePalette.ICE
        }.count()
        val heights = (0 until image.width).map(::height)
        assertTrue("never a hole: $heights", heights.all { it >= 3 })
        assertTrue("higher at the left wall", heights.first() > heights[20] + 2)
        // The drift goes one row past the bottom, hence the + 1.
        assertEquals("exactly the seam height at the right wall: $heights", List(5) { seam + 1 }, heights.takeLast(5))
    }

    @Test
    fun `the snowman is in the middle with his scarf and carrot`() {
        val image = draw()
        val colors = (0 until image.width).flatMap { x -> (0 until image.height).map { Color(image.getRGB(x, it)) } }.toSet()
        for (color in listOf(JinglePalette.SNOWMAN, JinglePalette.SCARF, JinglePalette.CARROT, JinglePalette.COAL, JinglePalette.MAILBOX)) {
            assertTrue("$color in the scene", color in colors)
        }
    }
}
