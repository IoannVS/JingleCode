package io.github.ioannvs.jinglecode.yard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YardSceneTest {

    @Test
    fun `a wide window has every group, in the mockup's order`() {
        val layout = yardLayout(0.0, 274.0)
        assertEquals(listOf(FIRS_L, STABLE, GRAZE, SHOP, PARK, TREE, MAIL, FIRS_R), layout.keys.toList())
    }

    @Test
    fun `a narrower window drops the grazing reindeer, then the stable and the big fir`() {
        assertEquals(listOf(FIRS_L, STABLE, SHOP, PARK, TREE, MAIL, FIRS_R), yardLayout(0.0, 200.0).keys.toList())
        assertEquals(listOf(FIRS_L, SHOP, PARK, MAIL, FIRS_R), yardLayout(0.0, 150.0).keys.toList())
    }

    @Test
    fun `the sleigh rides by time, not by ticks`() {
        // Santa walks from the bench to the sleigh in about 3.5 s, then the sleigh speeds up.
        fun sleighAfter(dt: Double): Double {
            val scene = YardScene(seed = 1)
            scene.step(dt, 214)
            scene.taskFinished(succeeded = true)
            repeat(Math.round(4.5 / dt).toInt()) { scene.step(dt, 214) }
            return scene.sleighX!!
        }
        val park = YardScene(seed = 1).apply { step(0.01, 214) }.sleighX!!
        val coarse = sleighAfter(0.02)
        val fine = sleighAfter(0.005)
        assertTrue("the sleigh left", fine > park + 10)
        assertEquals(coarse, fine, 2.0)
    }
}
