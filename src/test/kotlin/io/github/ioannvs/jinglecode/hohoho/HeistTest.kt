package io.github.ioannvs.jinglecode.hohoho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeistTest {
    private val call = HoHoHoCall(3)
    private val width = 900
    private val height = 400

    private fun seedFor(end: HeistEnd): Int = (1 until 2000).first { Heist.end(it) == end }

    private fun frames(seed: Int): List<Pair<Long, HeistFrame>> =
        (0 until Heist.LASTS_MS step 20).map { it to Heist.frame(call, seed, it, width, height) }

    @Test
    fun `every end comes up`() {
        val ends = (1 until 200).map(Heist::end).toSet()
        assertEquals(HeistEnd.entries.toSet(), ends)
    }

    @Test
    fun `the snowman drives off with the sleigh, Santa chases him, and they throw snowballs`() {
        for (end in HeistEnd.entries) {
            val frames = frames(seedFor(end))
            val start = frames.first().second
            assertEquals(Driver.SNOWMAN, start.driver)
            assertTrue(start.sleigh!!.x + Flyby.front(call) <= 0)
            // Santa rides behind the sleigh while they fight.
            val fight = frames.first { it.first > (Heist.FIGHT_START * Heist.DURATION_MS).toLong() + 400 }.second
            assertTrue(fight.rider!!.x < fight.sleigh!!.x)
            assertTrue(frames.any { (t, f) -> t < Heist.hitMs && f.flying.any { it.piece == Piece.SNOWBALL } })
            // A miss falls down to the bottom of the window and bursts there.
            assertTrue(frames.any { (_, f) -> f.flying.any { it.piece == Piece.SNOWBALL && it.y == height - 1 && it.splashMs != null } })
            assertTrue("$end is over in time", frames.last().second.isOver)
        }
    }

    @Test
    fun `when Santa wins the snowman falls out and Santa takes his sleigh back`() {
        val frames = frames(seedFor(HeistEnd.SANTA_WINS))
        val after = frames.filter { it.first > Heist.hitMs + Heist.CATCH_MS && it.second.sleigh != null }
        assertTrue(after.isNotEmpty())
        assertTrue(after.all { it.second.driver == Driver.SANTA && it.second.rider?.santa == false })
        assertTrue(frames.any { (_, f) -> f.flying.any { it.piece == Piece.HAT } })
        assertTrue(frames.any { (_, f) -> f.flying.any { it.piece == Piece.SNOWMAN } })
    }

    @Test
    fun `when the snowman wins Santa stops, shakes his fist and turns back`() {
        val frames = frames(seedFor(HeistEnd.SNOWMAN_WINS))
        assertTrue(frames.filter { it.second.sleigh != null }.all { it.second.driver == Driver.SNOWMAN })
        val fist = frames.filter { it.second.rider?.arm == Arm.FIST }
        assertTrue(fist.isNotEmpty())
        assertEquals(1, fist.map { it.second.rider!!.x }.toSet().size)
        assertNull(frames.first { it.first > Heist.hitMs + Heist.TURNED_BACK }.second.rider)
        assertTrue(frames.none { (_, f) -> f.flying.any { it.piece != Piece.SNOWBALL } })
    }

    @Test
    fun `when he crumbles his hat, head and body fall and the sleigh flies on empty`() {
        val frames = frames(seedFor(HeistEnd.CRUMBLE))
        val after = frames.filter { it.first > Heist.hitMs && it.second.sleigh != null }
        assertTrue(after.all { it.second.driver == Driver.NOBODY && it.second.rider?.santa == true })
        for (piece in listOf(Piece.HAT, Piece.HEAD, Piece.BODY)) {
            assertNotNull(piece.name, frames.firstOrNull { (_, f) -> f.flying.any { it.piece == piece } })
        }
    }
}
