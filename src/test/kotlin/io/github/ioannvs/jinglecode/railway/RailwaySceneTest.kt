package io.github.ioannvs.jinglecode.railway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor
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

    private val quiet = RailwayOptions(TrainFrequency.NORMAL, residents = false, poles = true)

    @Test
    fun `the first step and a resize repaint the whole strip with its sky`() {
        val scene = RailwayScene(seed = 1)
        scene.step(0.03, width = 1000, quiet)
        assertCoversAll(scene, 1000)
        scene.step(0.03, width = 1000, quiet)
        scene.step(0.03, width = 800, quiet)
        assertCoversAll(scene, 800)
    }

    private fun assertCoversAll(scene: RailwayScene, width: Int) {
        val d = scene.damage.single()
        assertTrue(d.x0 <= 0 && d.x1 >= width && d.top <= -RailwayScene.SKY_REACH)
    }

    @Test
    fun `nothing moving repaints nothing`() {
        // No walkers, and the first train leaves after 0.5 s; the birds sit at least 3 s before they turn.
        val scene = RailwayScene(seed = 1)
        scene.step(0.03, width = 1000, quiet)
        repeat(10) {
            scene.step(0.03, width = 1000, quiet)
            assertEquals(emptyList<Damage>(), scene.damage)
        }
    }

    @Test
    fun `a moving train repaints where it was and where it is, not the whole strip`() {
        val scene = RailwayScene(seed = 1)
        repeat(40) { scene.step(0.03, width = 1000, quiet) }
        val len = RailSprites.TRAINS[0].sumOf { it.w + 1 }
        val before = scene.trainX!!
        scene.step(0.03, width = 1000, quiet)
        val after = scene.trainX!!
        assertTrue(after > before)
        val covered = scene.damage.filter { it.x0 <= floor(before).toInt() && it.x1 >= ceil(after + len).toInt() }
        assertTrue("train from $before to ${after + len}: ${scene.damage.map { "${it.x0}..${it.x1}" }}", covered.isNotEmpty())
        assertTrue(scene.damage.sumOf { it.x1 - it.x0 } < 1000 / 2)
    }

    @Test
    fun `the seeded generator is the mockup's rng`() {
        // rng(4) in the browser: (4*1664525+1013904223)>>>0 / 2^32, then once more
        val r = MockupLcg(4)
        assertEquals(1020562323 / 4294967296.0, r(), 1e-12)
        assertEquals(((1020562323L * 1664525 + 1013904223) and 0xFFFF_FFFFL) / 4294967296.0, r(), 1e-12)
    }
}
