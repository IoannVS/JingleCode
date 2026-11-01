package io.github.ioannvs.jinglecode.hohoho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HoHoHoTest {

    @Test
    fun `the phrase is found where it was just typed`() {
        assertEquals(HoHoHoCall(3), HoHoHoPhrase.match("    // ho ho ho"))
        assertEquals(HoHoHoCall(3), HoHoHoPhrase.match("# Ho, ho, ho"))
        assertEquals(HoHoHoCall(3), HoHoHoPhrase.match("/* ho-ho-ho"))
        assertEquals(HoHoHoCall(3), HoHoHoPhrase.match("//hohoho"))
        assertEquals(HoHoHoCall(5), HoHoHoPhrase.match("// ho ho ho ho ho"))
    }

    @Test
    fun `not before the last ho is typed, not after something else, not two of them`() {
        assertNull(HoHoHoPhrase.match("// ho ho h"))
        assertNull(HoHoHoPhrase.match("// ho ho ho!"))
        assertNull(HoHoHoPhrase.match("// ho ho"))
        assertNull(HoHoHoPhrase.match("// echo ho ho"))
        assertNull(HoHoHoPhrase.match("// ho no"))
    }

    @Test
    fun `capitals are loud, Cyrillic is Russian, a no refuses`() {
        assertEquals(HoHoHoCall(3, loud = true), HoHoHoPhrase.match("// HO HO HO"))
        assertEquals(HoHoHoCall(3, cyrillic = true), HoHoHoPhrase.match("// хо-хо-хо"))
        assertEquals(HoHoHoCall(2, refused = true), HoHoHoPhrase.match("// ho ho no"))
        assertEquals(HoHoHoCall(2, loud = true, cyrillic = true, refused = true), HoHoHoPhrase.match("// ХО ХО НО"))
    }

    @Test
    fun `the banner says what was called, up to eight of them`() {
        assertEquals("HO HO HO", HoHoHoCall(3).banner)
        assertEquals("ХО ХО ХО", HoHoHoCall(3, cyrillic = true).banner)
        assertEquals("HO HO NO", HoHoHoCall(2, refused = true).banner)
        assertEquals(List(8) { "HO" }.joinToString(" "), HoHoHoCall(20).banner)
    }

    @Test
    fun `night is from nine in the evening to five in the morning`() {
        assertTrue(Flyby.isNight(LocalDateTime.of(2026, 12, 24, 21, 0)))
        assertTrue(Flyby.isNight(LocalDateTime.of(2026, 12, 25, 4, 59)))
        assertFalse(Flyby.isNight(LocalDateTime.of(2026, 12, 25, 5, 0)))
        assertFalse(Flyby.isNight(LocalDateTime.of(2026, 12, 24, 20, 59)))
    }

    @Test
    fun `the sleigh crosses the whole window, banner and all`() {
        val call = HoHoHoCall(3)
        val start = Flyby.sleigh(call, 0, 800, 400)!!
        assertTrue(start.x + Flyby.front(call) <= 0)
        val end = Flyby.sleigh(call, Flyby.FLIGHT_MS - 1, 800, 400)!!
        assertTrue(Flyby.bannerX(call, end) >= 799)
        assertNull(Flyby.sleigh(call, Flyby.FLIGHT_MS, 800, 400))
        // Higher in the middle than at the ends.
        assertTrue(Flyby.sleigh(call, Flyby.FLIGHT_MS / 2, 800, 400)!!.y < start.y)
    }

    @Test
    fun `on ho ho no it stalls, coughs and backs off to where it came from`() {
        val call = HoHoHoCall(2, refused = true)
        val stalled = Flyby.sleigh(call, (Flyby.REFUSED_MS * 0.5).toLong(), 800, 400)!!
        assertEquals(320.0, stalled.x.toDouble(), 1.0)
        assertNotNull(stalled.smokeMs)
        val gone = Flyby.sleigh(call, Flyby.REFUSED_MS - 1, 800, 400)!!
        assertTrue(gone.x + Flyby.front(call) <= 1)
        assertNull(Flyby.gift(call, Flyby.REFUSED_MS / 2, 800, 400, DropTarget(100, 380)))
    }

    @Test
    fun `the gift falls from the sleigh into the drift and sinks there`() {
        val call = HoHoHoCall(3)
        val target = DropTarget(100, 380)
        val ages = (0 until Flyby.lastsMs(call) step 10).map { it to Flyby.gift(call, it, 800, 400, target) }
        val shown = ages.filter { it.second != null }
        assertTrue(shown.isNotEmpty())
        val first = shown.first().second!!
        val sleigh = Flyby.sleigh(call, shown.first().first, 800, 400)!!
        assertTrue(first.y < sleigh.y)
        // It lands on the snow line and sinks in all the way.
        val landed = shown.mapNotNull { it.second }.filter { it.landedMs != null }
        assertTrue(landed.all { it.y + Flyby.GIFT_SIZE == target.ground })
        assertEquals(Flyby.GIFT_SIZE + 1, landed.last().sunk)
        assertTrue(shown.last().first < Flyby.lastsMs(call))
    }
}
