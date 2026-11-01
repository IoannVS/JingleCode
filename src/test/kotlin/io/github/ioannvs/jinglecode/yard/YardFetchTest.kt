package io.github.ioannvs.jinglecode.yard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YardFetchTest {

    @Test
    fun `on a push the sleigh leaves to the right, stays away, and comes back from the left`() {
        val scene = YardScene(seed = 1)
        scene.step(0.02, 214)
        val park = scene.sleighX!!
        assertTrue(scene.fetch())

        var departed = false
        repeat(1000) { if (!departed) { scene.step(0.02, 214); departed = scene.takeDeparted() } }
        assertTrue("it left the yard", departed)
        assertTrue(scene.away)
        repeat(100) { scene.step(0.02, 214) }
        assertTrue("it waits until the pile's visit is over", scene.away)
        assertFalse("nobody else leaves meanwhile", scene.fetch())

        scene.comeBack(cargo = 3)
        assertFalse(scene.away)
        assertTrue("from the left", scene.sleighX!! < park)
        repeat(1000) { scene.step(0.02, 214) }
        assertTrue("parked again", kotlin.math.abs(scene.sleighX!! - park) < 1e-9)
    }
}
