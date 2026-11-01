package io.github.ioannvs.jinglecode.yard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YardSceneTest {

    @Test
    fun `a wide window has every group, in the mockup's order`() {
        val layout = yardLayout(0.0, 274.0)
        assertEquals(listOf(FIRS_L, MAIL, STABLE, GRAZE, FAMILY, PARK, SHOP), layout.keys.toList())
    }

    @Test
    fun `a narrower window drops the grazing reindeer, then the stable and the snowmen`() {
        assertEquals(listOf(FIRS_L, MAIL, STABLE, FAMILY, PARK, SHOP), yardLayout(0.0, 196.0).keys.toList())
        assertEquals(listOf(FIRS_L, MAIL, PARK, SHOP), yardLayout(0.0, 130.0).keys.toList())
    }

    @Test
    fun `the workshop stands last, with room before the right edge, the sleigh just left of it`() {
        for (w in doubleArrayOf(130.0, 196.0, 274.0)) {
            val layout = yardLayout(0.0, w)
            val wall = layout.getValue(SHOP) + 16 + 27
            assertTrue("width $w", wall + 6 <= w + 0.001)
            assertTrue("width $w", wall > w - 25)
            assertTrue("width $w", layout.getValue(PARK) + 36 <= layout.getValue(SHOP))
        }
    }

    @Test
    fun `the snowmen stand right of the middle in a wide window`() {
        val layout = yardLayout(0.0, 274.0)
        assertTrue(layout.getValue(FAMILY) + 12 > 274.0 / 2 + 8)
    }

    @Test
    fun `the sleigh rides by time, not by ticks`() {
        // Santa walks from the bench past the reindeer to the sleigh in about 4.5 s, then the sleigh speeds up
        // and leaves the strip at about 6.4 s.
        fun sleighAfter(dt: Double): Double {
            val scene = YardScene(seed = 1)
            scene.step(dt, 214)
            scene.taskFinished(succeeded = true)
            repeat(Math.round(5.5 / dt).toInt()) { scene.step(dt, 214) }
            return scene.sleighX!!
        }
        val park = YardScene(seed = 1).apply { step(0.01, 214) }.sleighX!!
        val coarse = sleighAfter(0.02)
        val fine = sleighAfter(0.005)
        assertTrue("the sleigh left", fine > park + 10)
        assertEquals(coarse, fine, 2.0)
    }
}
