package io.github.ioannvs.jinglecode.snow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnowFieldTest {

    private fun SnowField.run(frames: Int, width: Int = 200, height: Int = 100) {
        repeat(frames) { step(width, height, dt = 0.14, time = it * 0.14) }
    }

    @Test
    fun `density follows the view like the mockup`() {
        val field = SnowField(Random(1))
        field.run(1)
        assertEquals(200 * 100 / 400, field.flakes.size)

        field.run(1, width = 100, height = 100)
        assertEquals(100 * 100 / 400, field.flakes.size)
    }

    @Test
    fun `light snow has half the flakes and heavy snow twice as many`() {
        val field = SnowField(Random(5))
        field.step(200, 100, dt = 0.0, time = 0.0, areaPerFlake = SnowAmount.FEW.areaPerFlake)
        assertEquals(25, field.flakes.size)
        field.step(200, 100, dt = 0.0, time = 0.0, areaPerFlake = SnowAmount.MANY.areaPerFlake)
        assertEquals(100, field.flakes.size)
    }

    @Test
    fun `flakes stay in the view and keep falling`() {
        val field = SnowField(Random(2))
        val seen = HashSet<Int>()
        repeat(1000) { frame ->
            field.step(200, 100, dt = 0.14, time = frame * 0.14)
            for (flake in field.flakes) {
                assertTrue("x ${flake.x}", flake.x in 0.0..<200.0)
                assertTrue("y ${flake.y}", flake.y in -22.0..100.0)
                seen += (flake.y / 10).toInt()
            }
        }
        // Over time the snow passes every band of the view, top to bottom.
        assertTrue(seen.containsAll((0 until 10).toList()))
    }

    @Test
    fun `no repaint until some flake reaches another pixel`() {
        val field = SnowField(Random(4))
        assertTrue("new flakes", field.step(200, 100, dt = 0.0, time = 0.0))
        assertFalse("nothing moved", field.step(200, 100, dt = 0.0, time = 0.0))
        assertTrue("a whole second of snowfall", field.step(200, 100, dt = 1.0, time = 1.0))
    }

    @Test
    fun `fast snow falls twice as far as normal, slow half as far`() {
        for (speed in SnowSpeed.entries) {
            val field = SnowField(Random(6))
            field.step(200, 100, dt = 0.0, time = 0.0)
            val before = field.flakes.map { it.y }
            field.step(200, 100, dt = 0.5, time = 0.5, speed = speed.factor)
            for ((flake, y) in field.flakes.zip(before)) {
                // Flakes that went past the bottom started over at the top; the rest fell exactly this far.
                if (flake.y < y) continue
                assertEquals("$speed", flake.vy * speed.factor * 0.5, flake.y - y, 1e-9)
            }
        }
    }

    @Test
    fun `an empty view has no snow`() {
        val field = SnowField(Random(3))
        field.run(5)
        field.run(1, width = 0)
        assertTrue(field.flakes.isEmpty())
    }
}
