package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class GiftPileTest {

    @Test
    fun `the newest gift falls from 22 pixels up in half a second, as in the mockup`() {
        assertEquals(-22, giftDrop(0))
        assertTrue(giftDrop(250) in -21..-1)
        assertEquals(0, giftDrop(500))
        assertEquals(0, giftDrop(Long.MAX_VALUE))
    }

    @Test
    fun `the pile follows the unpushed commits and pushes send the sleigh`() {
        val pile = GiftPile()
        assertEquals("no Git plugin in a unit test", GiftPile.WITHOUT_GIT, pile.count)
        assertNull(pile.delivery())

        pile.onEvent(JingleEvent.UnpushedCommits(4))
        pile.onEvent(JingleEvent.Commit)
        assertEquals(4, pile.count)
        assertTrue("a gift is falling", pile.dropAgeMs() < 500)

        pile.onEvent(JingleEvent.UnpushedCommits(0))
        pile.onEvent(JingleEvent.Push(carried = 4))
        val visit = pile.delivery()
        assertNotNull(visit)
        assertEquals(DeliveryKind.DELIVER, visit!!.kind)
        assertEquals("the sleigh comes for what was there", 4, visit.gifts)

        pile.onEvent(JingleEvent.PushRejected)
        assertEquals("one visit at a time", DeliveryKind.DELIVER, pile.delivery()!!.kind)

        val other = GiftPile()
        other.onEvent(JingleEvent.UnpushedCommits(20))
        other.onEvent(JingleEvent.PushRejected)
        assertEquals(DeliveryKind.RETURN, other.delivery()!!.kind)
        assertEquals("the pile shows nine at most", MAX_GIFTS, other.delivery()!!.gifts)
    }

    @Test
    fun `elves load the gifts one by one, top first, and ride off with them`() {
        val n = 4
        fun at(ms: Long) = DeliveryView(DeliveryKind.DELIVER, ms, n)
        val hover = At(20, 0)

        assertTrue("flying in", Delivery.sleigh(at(0), 140, hover)!!.x < 0)
        assertEquals(hover, Delivery.sleigh(at(Delivery.IN_MS), 140, hover))
        assertEquals(n, Delivery.inPile(at(Delivery.IN_MS - 1)))
        assertEquals("the first toss took the top gift", n - 1, Delivery.inPile(at(Delivery.IN_MS + 10)))
        assertEquals(0, Delivery.inPile(at(Delivery.loadedAt(n))))
        assertEquals(n, Delivery.inSleigh(at(Delivery.loadedAt(n))))
        assertTrue(Delivery.elvesRiding(at(Delivery.departAt(n))))
        assertNull("gone", Delivery.sleigh(at(Delivery.goneAt(n)), 140, hover))
        assertNull("the pile is whatever is left now", Delivery.inPile(at(Delivery.goneAt(n))))

        val places = List(MAX_GIFTS) { At(it * 5, 20) }
        val first = Delivery.flying(at(Delivery.IN_MS + 50), places, At(26, -1))
        assertEquals("the top gift is the first in the air", listOf(n - 1), first.map { it.index })
    }

    @Test
    fun `after a rejected push the sleigh comes back and the elves unload, bottom first`() {
        val n = 3
        fun at(ms: Long) = DeliveryView(DeliveryKind.RETURN, ms, n)
        val hover = At(20, 0)
        assertEquals("away", 0, Delivery.inPile(at(Delivery.goneAt(n) + 100)))
        assertEquals("back", hover, Delivery.sleigh(at(Delivery.backAt(n)), 140, hover))
        val places = List(MAX_GIFTS) { At(it * 5, 20) }
        assertEquals("the bottom gift goes back first", listOf(0), Delivery.flying(at(Delivery.outAt(n) + 50), places, At(26, -1)).map { it.index })
        assertEquals("the last one is still in the air", n - 1, Delivery.inPile(at(Delivery.unloadedAt(n) - 1)))
        assertEquals(0, Delivery.inSleigh(at(Delivery.unloadedAt(n))))
        assertFalse(Delivery.elvesRiding(at(Delivery.unloadedAt(n))))
        assertTrue("the elves run off", Delivery.elves(at(Delivery.unloadedAt(n) + 100), listOf(At(10, 20), At(0, 20)), At(26, 1), At(142, 20)).all { it.pose == ElfPose.RUN })
    }

    private fun colors(pile: PileView): Set<Color> {
        val image = BufferedImage(140, SCENE_ROWS + 1, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        PixelCanvas.paint(g, 0, 0) { drawProjectScene(140, f = 1, t = 0, pile = pile) }
        g.dispose()
        return (0 until image.width).flatMap { x -> (0 until image.height).map { Color(image.getRGB(x, it)) } }.toSet()
    }

    @Test
    fun `gifts appear one by one in the mockup's order`() {
        assertFalse("no gifts", JinglePalette.GIFT_TEAL in colors(PileView(0)))
        assertFalse("one gift is the red one", JinglePalette.GIFT_TEAL in colors(PileView(1)))
        assertTrue("the second is teal", JinglePalette.GIFT_TEAL in colors(PileView(2)))
        assertTrue("more than nine still draws nine", JinglePalette.GIFT_PLUM in colors(PileView(100)))
    }
}
