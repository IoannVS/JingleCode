package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Test

class GarlandSizeTest {

    @Test
    fun `sizes give 2, 3 and 4 device pixels at 125 percent with the default font`() {
        val font = 13f
        assertEquals(2, PixelCanvas.pixelSize(1.25f, magnify = GarlandSize.SMALL.pixelUser(font)))
        assertEquals(3, PixelCanvas.pixelSize(1.25f, magnify = GarlandSize.NORMAL.pixelUser(font)))
        assertEquals(4, PixelCanvas.pixelSize(1.25f, magnify = GarlandSize.LARGE.pixelUser(font)))
    }
}
