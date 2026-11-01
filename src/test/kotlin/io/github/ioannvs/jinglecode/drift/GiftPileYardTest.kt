package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.events.JingleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftPileYardTest {

    @Test
    fun `with Santa's yard showing, the visit waits for the yard's sleigh and the gifts stay till then`() {
        val pile = GiftPile()
        pile.onEvent(JingleEvent.UnpushedCommits(3))
        pile.yardIsHere()
        pile.onEvent(JingleEvent.UnpushedCommits(0))
        pile.onEvent(JingleEvent.Push(carried = 3))
        assertNull("the sleigh is still leaving the yard", pile.delivery())
        assertTrue(pile.sleighOut)
        assertEquals("the gifts wait for it", 3, pile.shown)

        pile.releaseFromYard()
        val visit = pile.delivery()
        assertNotNull(visit)
        assertEquals(3, visit!!.gifts)
        assertEquals(0, pile.shown)
    }

    @Test
    fun `without the yard the sleigh comes at once`() {
        val pile = GiftPile()
        pile.onEvent(JingleEvent.Push(carried = 2))
        assertNotNull(pile.delivery())
    }
}
