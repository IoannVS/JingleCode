package io.github.ioannvs.jinglecode.railway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.roundToInt

class RailwaySceneTest {

    private val options = RailwayOptions(TrainFrequency.NORMAL, residents = true, poles = true)

    private fun trainAfter(seconds: Double, dt: Double): Double {
        val scene = RailwayScene(seed = 1)
        repeat((seconds / dt).roundToInt()) { scene.step(dt, width = 1000, options) }
        val x = scene.trainX
        assertNotNull(x)
        return x!!
    }

    @Test
    fun `the train moves by time, not by ticks`() {
        // The first train leaves after the mockup's 0.5 s at 26 px a second (the mockup's 52, halved), from just left of the strip.
        val slow = trainAfter(1.5, dt = 0.05)
        val fast = trainAfter(1.5, dt = 0.01)
        assertEquals(slow, fast, 1e-6)
        val len = RailSprites.TRAINS[0].sumOf { it.w + 1 }
        assertEquals(0.0 - len + 26.0, fast, 1e-6)
    }

    @Test
    fun `the seeded generator is the mockup's rng`() {
        // rng(4) in the browser: (4*1664525+1013904223)>>>0 / 2^32, then once more
        val r = MockupLcg(4)
        assertEquals(1020562323 / 4294967296.0, r(), 1e-12)
        assertEquals(((1020562323L * 1664525 + 1013904223) and 0xFFFF_FFFFL) / 4294967296.0, r(), 1e-12)
    }
}
