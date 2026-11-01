package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

class GarlandModeTest {

    @Test
    fun `twinkle matches the mockup`() {
        // Mockup: on = (i + floor(t / 2)) % 4 !== 0, color BULBS[i % 4]
        for (t in 0 until 16) for (i in 0 until 12) {
            val on = (i + t / 2) % 4 != 0
            val expected = if (on) JinglePalette.BULBS[i % 4] else JinglePalette.BULB_OFF
            assertEquals(expected, GarlandMode.TWINKLE.bulbColor(i, t))
        }
    }

    @Test
    fun `chase keeps half of the bulbs lit and moves`() {
        for (t in 0 until 8) {
            val lit = (0 until 40).count { GarlandMode.CHASE.bulbColor(it, t) != JinglePalette.BULB_OFF }
            assertEquals(20, lit)
        }
        assertTrue(GarlandMode.CHASE.bulbColor(0, 0) != GarlandMode.CHASE.bulbColor(0, 2))
    }

    @Test
    fun `glint is the mockup white overlay made opaque`() {
        // #ffffffaa over #5ab8ff
        val glint = JinglePalette.glint(Color(0x5ab8ff))
        assertEquals(255, glint.alpha)
        assertEquals(Color(0xc8e7ff), glint)
    }
}
