package io.github.ioannvs.jinglecode.icicles

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class IcicleSpriteTest {

    private val empty = Color(0x123456)

    /** Draws the icicles one device pixel per logical pixel and returns the picture. */
    private fun draw(length: Int, t: Int = 0, skip: (Int) -> Boolean = { false }): BufferedImage {
        val image = BufferedImage(length, ICICLE_MAX + 2, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = empty
        g.fillRect(0, 0, image.width, image.height)
        PixelCanvas.paint(g, 0, 0) {
            assertEquals(1, pixelSize)
            drawIcicles(length, t, skip)
        }
        g.dispose()
        return image
    }

    private fun BufferedImage.at(x: Int, y: Int) = Color(getRGB(x, y))

    @Test
    fun `icicles hang from the top, every other column, as in the mockup`() {
        val image = draw(120)
        var hanging = 0
        for (x in 0 until image.width) {
            val length = (0 until image.height).takeWhile { image.at(x, it) != empty }.count()
            if (x % 2 == 0) assertEquals("column $x", 0, length)
            if (length == 0) continue
            hanging++
            assertTrue("column $x is $length long", length <= ICICLE_MAX)
            // A one-pixel icicle is all tip, and the tip may glint, as in the mockup.
            val top = image.at(x, 0)
            assertTrue("snow on top of $x: $top", top == JinglePalette.SNOW || length == 1 && top == JinglePalette.WHITE)
            // Nothing below the tip: an icicle is one piece.
            for (y in length until image.height) assertEquals(empty, image.at(x, y))
        }
        // About two thirds of the 60 columns, never all and never none.
        assertTrue("$hanging icicles", hanging in 25..55)
    }

    @Test
    fun `skipped columns stay empty`() {
        val image = draw(60) { it in 20..30 }
        for (x in 20..30) for (y in 0 until image.height) assertEquals(empty, image.at(x, y))
    }

    @Test
    fun `tips glint white now and then`() {
        val glints = (0 until 300 step 3).sumOf { t ->
            val image = draw(120, t)
            (0 until image.width).count { x -> (0 until image.height).any { image.at(x, it) == JinglePalette.WHITE } }
        }
        assertTrue("$glints glints", glints > 0)
    }
}
