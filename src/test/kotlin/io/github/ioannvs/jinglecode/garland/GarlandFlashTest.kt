package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GarlandFlashTest {

    @Test
    fun `alarm blinks in several reds and nothing else`() {
        val alarm = GarlandFlash.Alarm(salt = 0)
        val colors = (0 until 20).flatMap { t -> (0 until 40).map { alarm.bulbColor(it, 40, t) } }
        assertTrue(colors.all { it in JinglePalette.ALARM_REDS || it == JinglePalette.BULB_OFF })
        assertEquals(JinglePalette.ALARM_REDS.toSet(), colors.filter { it != JinglePalette.BULB_OFF }.toSet())
        assertTrue(JinglePalette.BULB_OFF in colors)
        // It blinks: a bulb doesn't keep one color.
        assertTrue((0 until 20).map { alarm.bulbColor(5, 40, it) }.toSet().size > 1)
    }

    @Test
    fun `lines blink differently`() {
        val a = (0 until 40).map { GarlandFlash.Alarm(0).bulbColor(it, 40, 3) }
        val b = (0 until 40).map { GarlandFlash.Alarm(1).bulbColor(it, 40, 3) }
        assertTrue(a != b)
    }

    @Test
    fun `wave fills the garland with gold from the start, sparkles and gives the colors back`() {
        val count = 30
        fun colors(progress: Double, t: Int = 0) = (0 until count).map { GarlandFlash.Wave(progress).bulbColor(it, count, t) }
        val gold = setOf(JinglePalette.GOLD_WAVE, JinglePalette.GOLD_CREST)

        // Filling: gold behind the crest, the bulbs' own colors ahead of it, and the gold grows.
        val filling = listOf(0.1, 0.2, 0.3, 0.4).map { p -> colors(p).count { it != null } }
        assertEquals(filling.sorted(), filling)
        val half = colors(0.25)
        val ahead = half.indexOfFirst { it == null }
        assertTrue(ahead in 5 until count)
        assertTrue(half.take(ahead).all { it in gold })
        assertTrue(half.drop(ahead).all { it == null })
        assertTrue(JinglePalette.GOLD_CREST in half && JinglePalette.GOLD_WAVE in half)

        // All gold, sparkling.
        val sparkle = (0 until 10).flatMap { colors(0.65, it) }
        assertTrue(sparkle.all { it in gold })
        assertEquals(gold, sparkle.toSet())

        // Unwinding from the start, and all back at the end.
        val unwinding = colors(0.9)
        assertNull(unwinding.first())
        assertTrue(unwinding.last() in gold)
        assertTrue(colors(1.0).all { it == null })
    }
}
