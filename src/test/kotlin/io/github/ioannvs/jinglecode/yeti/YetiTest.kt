package io.github.ioannvs.jinglecode.yeti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

class YetiTest {

    @Test
    fun `the word is found where it was just typed, in either language`() {
        assertTrue(YetiPhrase.matches("    // yeti"))
        assertTrue(YetiPhrase.matches("# where is the YETI"))
        assertTrue(YetiPhrase.matches("// йети"))
        assertTrue(YetiPhrase.matches("//Йети"))
    }

    @Test
    fun `not before it is typed out, not after something else, not at the end of another word`() {
        assertFalse(YetiPhrase.matches("// yet"))
        assertFalse(YetiPhrase.matches("// yeti!"))
        assertFalse(YetiPhrase.matches("// myyeti"))
        assertFalse(YetiPhrase.matches("// кйети"))
    }

    @Test
    fun `it calls him only if it stays a word of its own`() {
        assertTrue(YetiPhrase.standsAlone("// yeti", 3))
        assertTrue(YetiPhrase.standsAlone("// йети, ау", 3))
        assertFalse(YetiPhrase.standsAlone("// yetis", 3))
        assertFalse(YetiPhrase.standsAlone("// йетиный", 3))
        assertFalse(YetiPhrase.standsAlone("// yak", 3))
    }

    @Test
    fun `cold words count, also inside identifiers and with Russian endings`() {
        assertTrue(YetiLines.isCold("val coldStart = true"))
        assertTrue(YetiLines.isCold("cache.invalidate()"))
        assertTrue(YetiLines.isCold("ICE_AGE"))
        assertTrue(YetiLines.isCold("// снега много"))
        assertTrue(YetiLines.isCold("frozenset(items)"))
        assertFalse(YetiLines.isCold("val service = device.price"))
        assertFalse(YetiLines.isCold("// следующий шаг"))
    }

    @Test
    fun `TODO and FIXME are liked most, then cold words, then comments`() {
        assertEquals(YetiLines.TODO_WEIGHT, YetiLines.weight("// TODO: later", inComment = true))
        assertEquals(YetiLines.TODO_WEIGHT, YetiLines.weight("x() // fixme", inComment = true))
        assertEquals(YetiLines.PLAIN_WEIGHT, YetiLines.weight("val todos = listOf()", inComment = false))
        assertEquals(YetiLines.COLD_WEIGHT, YetiLines.weight("freeze()", inComment = false))
        assertEquals(YetiLines.COMMENT_WEIGHT, YetiLines.weight("// just a comment", inComment = true))
        assertEquals(YetiLines.PLAIN_WEIGHT, YetiLines.weight("return x", inComment = false))
    }

    @Test
    fun `the pick is random but leans to the heavier lines, and any line may come`() {
        val lines = listOf(YetiLine(1, 6), YetiLine(2, 1), YetiLine(3, 1))
        val random = Random(7)
        val picks = List(8000) { YetiLines.pick(lines, random)!!.line }.groupingBy { it }.eachCount()
        assertEquals(6000.0, picks.getValue(1).toDouble(), 300.0)
        assertTrue(picks.getValue(2) > 0 && picks.getValue(3) > 0)
        assertNull(YetiLines.pick(emptyList(), random))
    }

    @Test
    fun `about one day in three is a visit day`() {
        val start = LocalDate.of(2026, 12, 1)
        val days = (0 until 3000L).count { YetiSchedule.isVisitDay(start.plusDays(it)) }
        assertEquals(1000.0, days.toDouble(), 100.0)
    }

    @Test
    fun `up to three visits on a visit day, an hour apart, none after he was caught`() {
        val date = (0 until 100L).map { LocalDate.of(2026, 12, 1).plusDays(it) }.first(YetiSchedule::isVisitDay)
        val random = Random(1)
        val morning = date.atTime(9, 0)
        var day = YetiSchedule.today(null, morning, random)
        assertFalse(YetiSchedule.mayCome(day, morning))
        var now = day.notBefore!!
        assertTrue(YetiSchedule.mayCome(day, now))
        repeat(YetiSchedule.MAX_VISITS) {
            assertTrue(YetiSchedule.mayCome(day, now))
            day = YetiSchedule.afterVisit(day, now, random)
            assertFalse(YetiSchedule.mayCome(day, now.plusMinutes(59)))
            now = day.notBefore!!
        }
        assertFalse(YetiSchedule.mayCome(day, now))
        val caught = YetiSchedule.afterCatch(YetiSchedule.today(null, morning, random))
        assertFalse(YetiSchedule.mayCome(caught, morning.plusHours(5)))
        // A new day starts over.
        assertEquals(0, YetiSchedule.today(day, date.plusDays(1).atTime(9, 0), random).visits)
    }

    @Test
    fun `no visits on other days`() {
        val date = (0 until 100L).map { LocalDate.of(2026, 12, 1).plusDays(it) }.first { !YetiSchedule.isVisitDay(it) }
        val day = YetiDay(date)
        assertFalse(YetiSchedule.mayCome(day, LocalDateTime.of(date, java.time.LocalTime.NOON)))
    }

    @Test
    fun `he rises out of the line, looks around and sinks back`() {
        val lasts = 10_000L
        assertEquals(YETI_H - YETI_H / YetiTimeline.RISE_STEPS, YetiTimeline.standing(0, lasts)!!.sunk)
        assertEquals(0, YetiTimeline.standing(YetiTimeline.RISE_STEPS * YetiTimeline.STEP_MS, lasts)!!.sunk)
        assertEquals(YETI_H, YetiTimeline.standing(lasts - 1, lasts)!!.sunk)
        assertNull(YetiTimeline.standing(lasts, lasts))
        val eyes = (0 until lasts step YetiTimeline.STEP_MS).mapNotNull { YetiTimeline.standing(it, lasts)?.eyes }.toSet()
        assertEquals(YetiEyes.entries.toSet(), eyes)
    }

    @Test
    fun `caught, he waves and vanishes in a puff, with no footprints yet`() {
        for (kind in TrailKind.entries) {
            val first = YetiTimeline.caught(0, kind)!!
            assertEquals(YetiMove.WAVE, first.yeti!!.move)
            assertNull(first.puff)
            assertTrue(first.prints.isEmpty())
            val gone = YetiTimeline.caught(YetiTimeline.GONE_MS, kind)!!
            assertNull(gone.yeti)
            assertEquals(0, gone.puff)
            assertNull(YetiTimeline.caught(YetiTimeline.GONE_MS + YetiTimeline.PUFF_STEPS * YetiTimeline.STEP_MS, kind)!!.puff)
        }
    }

    @Test
    fun `then his footprints run off to the right along the line and melt`() {
        for (kind in TrailKind.entries) {
            val lasts = YetiTimeline.caughtMs(kind)
            val scenes = (0 until lasts step 10).mapNotNull { YetiTimeline.caught(it, kind) }
            val prints = scenes.flatMap { it.prints }.distinct()
            // Away from him: none on the spot he stood on, none to the left of it.
            assertTrue(prints.all { it.x > 0 })
            assertEquals(YetiTimeline.reach(kind), prints.maxOf { it.x } + PRINT_W)
            assertEquals(setOf(true, false), prints.map { it.upper }.toSet())
            // The newest print is always the farthest one.
            for (scene in scenes.filter { it.prints.isNotEmpty() }) {
                assertTrue(scene.prints.maxOf { it.x } >= scene.prints.last().x)
            }
            assertTrue(YetiTimeline.caught(lasts - 1, kind)!!.prints.isNotEmpty())
            assertNull(YetiTimeline.caught(lasts, kind))
        }
    }

    @Test
    fun `the notification is a sighting, a meeting or a question, the three as likely`() {
        val random = Random(3)
        val notes = List(9000) { YetiNote.pick(random) }.groupingBy { it }.eachCount()
        assertEquals(3000.0, notes.getValue(YetiNote.SEEN).toDouble(), 200.0)
        assertEquals(3000.0, notes.getValue(YetiNote.MEETING).toDouble(), 200.0)
        assertEquals(YetiNote.entries.toSet(), notes.keys)
    }

    @Test
    fun `meetings are counted per season, December with the January after it`() {
        assertEquals(2026, YetiNote.seasonOf(LocalDate.of(2026, 12, 1)))
        assertEquals(2026, YetiNote.seasonOf(LocalDate.of(2027, 1, 14)))
        assertEquals(2027, YetiNote.seasonOf(LocalDate.of(2027, 12, 1)))
    }

    @Test
    fun `every note is in every language of the plugin`() {
        val bundles = java.io.File("src/main/resources/messages").listFiles { f -> f.name.startsWith("JingleCodeBundle") }!!
        assertEquals(5, bundles.size)
        for (bundle in bundles) {
            val keys = java.util.Properties().apply { bundle.reader(Charsets.UTF_8).use(::load) }.stringPropertyNames()
            for (note in YetiNote.entries) assertTrue("${note.key} in ${bundle.name}", note.key in keys)
        }
    }

    @Test
    fun `each trail goes its own way`() {
        val walk = YetiTimeline.reach(TrailKind.WALK)
        val run = YetiTimeline.reach(TrailKind.RUN)
        assertTrue(run > walk)
        assertTrue(YetiTimeline.caughtMs(TrailKind.RUN) < YetiTimeline.caughtMs(TrailKind.WALK))
        // A leap lands both feet at once, and every leap is longer than the one before.
        val leaps = (0 until YetiTimeline.caughtMs(TrailKind.LEAP) step 10)
            .flatMap { YetiTimeline.caught(it, TrailKind.LEAP)?.prints.orEmpty() }.distinct().map { it.x }.distinct().sorted()
        val gaps = leaps.zipWithNext { a, b -> b - a }
        assertEquals(gaps.sorted(), gaps)
        assertEquals(gaps.size, gaps.toSet().size)
    }
}
