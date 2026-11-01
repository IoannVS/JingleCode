package io.github.ioannvs.jinglecode.popups

import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File

/**
 * The cap and the holly match the mockup pixel for pixel. References come from
 * `src/test/resources/popups-mockup/render-references.js`, run on `docs/mockup/jingle-code-windows.html`.
 */
class PopupCapSpriteTest {

    private val empty = Color(0x123456)

    /** Mockup pictures by name ("cap 180 4 0", "holly 10"): rows of `#rrggbb` or `.`. */
    private val references: Map<String, List<List<String>>> by lazy {
        val lines = File("src/test/resources/popups-mockup/references.txt").readLines().filter { it.isNotBlank() }
        val result = linkedMapOf<String, MutableList<List<String>>>()
        var current: MutableList<List<String>>? = null
        for (line in lines) {
            if (line.startsWith("cap ") || line.startsWith("holly ")) {
                current = mutableListOf<List<String>>().also { result[line] = it }
            }
            else current!! += line.split(' ')
        }
        result
    }

    private fun draw(w: Int, h: Int, block: PixelCanvas.() -> Unit): List<List<String>> {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = empty
        g.fillRect(0, 0, w, h)
        PixelCanvas.paint(g, 0, 0) {
            assertEquals(1, pixelSize)
            block()
        }
        g.dispose()
        return (0 until h).map { y ->
            (0 until w).map { x ->
                val c = Color(image.getRGB(x, y))
                if (c == empty) "." else "#%06x".format(c.rgb and 0xffffff)
            }
        }
    }

    @Test
    fun `cap matches the mockup`() {
        val caps = references.keys.filter { it.startsWith("cap ") }
        assertEquals(5, caps.size)
        for (name in caps) {
            val (w, h, frame) = name.removePrefix("cap ").split(' ').map(String::toInt)
            assertEquals(name, references.getValue(name), draw(w, h) { drawPopupCap(0, 0, w, h, frame.toLong()) })
        }
    }

    @Test
    fun `cap can be drawn above its origin`() {
        val shifted = draw(40, 8) { drawPopupCap(3, 4, 30, 4, 5) }
        val plain = draw(30, 4) { drawPopupCap(0, 0, 30, 4, 5) }
        for (y in 0 until 4) for (x in 0 until 30) assertEquals("$x,$y", plain[y][x], shifted[y + 4][x + 3])
        for (y in 0 until 4) assertEquals(List(40) { "." }, shifted[y])
    }

    @Test
    fun `holly matches the mockup, berry glint included`() {
        for (frame in listOf(0, 10)) {
            assertEquals("holly $frame", references.getValue("holly $frame"), draw(HOLLY_W, HOLLY_H) { drawHolly(0, 0, frame.toLong()) })
        }
    }
}
