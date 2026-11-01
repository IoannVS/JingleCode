package io.github.ioannvs.jinglecode.icicles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcicleFallTest {

    private val seed = 20
    private val bottom = 400

    private fun falling(share: Double) = (0 until 1000).filter { IcicleFall.drop(it, 3, bottom, 0, seed, share) != null }

    @Test
    fun `a fall takes about its share of the icicles, and a new fall others`() {
        val toolbar = falling(IcicleFall.TOOLBAR_SHARE).size
        assertTrue(toolbar in 480..620)
        assertTrue(falling(IcicleFall.TAB_SHARE).size in 730..870)
        val next = (0 until 1000).filter { IcicleFall.drop(it, 3, bottom, 0, seed + 5, IcicleFall.TOOLBAR_SHARE) != null }
        assertTrue(next != falling(IcicleFall.TOOLBAR_SHARE))
    }

    @Test
    fun `an icicle hangs, lets go, falls faster and faster to the bottom, breaks and grows back`() {
        val key = falling(1.0).first()
        val length = 4
        val states = (0..IcicleFall.LONGEST_MS step 10).map { IcicleFall.drop(key, length, bottom, it, seed, 1.0)!! }
        assertEquals(length, states.first().hanging)

        val fallen = states.mapNotNull { it.fallen }
        assertTrue(fallen.isNotEmpty())
        assertEquals(fallen.sorted(), fallen)
        assertTrue(fallen.last() <= bottom - length)
        assertTrue(fallen.last() > bottom - length - 40)
        // Gravity: the second half of the way takes less time than the first.
        val half = fallen.indexOfFirst { it >= (bottom - length) / 2 }
        assertTrue(fallen.size - half < half)
        // Nothing hangs while it falls.
        assertTrue(states.filter { it.fallen != null }.all { it.hanging == 0 })

        // Breaks right after the fall, grows back meanwhile, and is whole at the end.
        val hit = states.indexOfFirst { it.shattered != null }
        assertTrue(hit > 0 && states[hit - 1].fallen != null)
        assertTrue(states.drop(hit).zipWithNext().all { (a, b) -> b.hanging >= a.hanging })
        assertEquals(IcicleDrop(length, null, null), states.last())
    }

    @Test
    fun `icicles let go at different moments within the spread`() {
        val keys = falling(1.0).take(50)
        val release = keys.map { key -> (0..IcicleFall.SPREAD_MS).first { IcicleFall.drop(key, 2, bottom, it, seed, 1.0)!!.fallen != null } }
        assertTrue(release.toSet().size > 10)
        assertTrue(release.all { it <= IcicleFall.SPREAD_MS })
    }

    @Test
    fun `an icicle that stays has no drop`() {
        val stays = (0 until 1000).first { IcicleFall.drop(it, 3, bottom, 0, seed, 0.5) == null }
        assertNull(IcicleFall.drop(stays, 3, bottom, 300, seed, 0.5))
        assertNotNull(IcicleFall.drop(stays, 3, bottom, 300, seed, 1.0))
    }
}
