package io.github.ioannvs.jinglecode.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage

class PixelCanvasTest {

    @Test
    fun `logical pixels become integer squares on a scaled graphics`() {
        val image = BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.scale(2.0, 2.0) // like a 200 % HiDPI screen
        PixelCanvas.paint(g, 1, 1) {
            assertEquals(2, pixelSize)
            px(1, 0, JinglePalette.SNOW)
        }
        g.dispose()

        // Logical (1, 0) from user origin (1, 1) is device (4, 2) to (5, 3).
        for (x in 0 until 20) for (y in 0 until 20) {
            val inside = x in 4..5 && y in 2..3
            assertEquals("pixel $x,$y", inside, image.getRGB(x, y) and 0xffffff == JinglePalette.SNOW.rgb and 0xffffff)
        }
    }

    @Test
    fun `fractional scale rounds to whole pixels`() {
        assertEquals(1, PixelCanvas.pixelSize(1.25f))
        assertEquals(2, PixelCanvas.pixelSize(1.5f))
        assertEquals(6, PixelCanvas.pixelSize(2f, zoom = 3))
        assertEquals(2, PixelCanvas.pixelSize(1.25f, magnify = 1.6f))
        assertEquals(3, PixelCanvas.pixelSize(2f, magnify = 1.6f))
    }

    @Test
    fun `js rounding matches Math round`() {
        assertEquals(3, PixelCanvas.jsRound(2.5))
        assertEquals(-2, PixelCanvas.jsRound(-2.5))
        assertEquals(-3, PixelCanvas.jsRound(-2.51))
    }

    @Test
    fun `hash stays in unit interval`() {
        for (a in 0 until 50) for (b in 0 until 6) {
            val h = hash(a, b)
            assertTrue(h in 0.0..<1.0)
        }
    }
}
