package io.github.ioannvs.jinglecode.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

class JingleClockTest {

    private val system = Clock.fixed(LocalDateTime.parse("2026-10-30T15:20").toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `no property means the real time`() {
        assertSame(system, JingleClock.fromProperty(null, system))
        assertSame(system, JingleClock.fromProperty(" ", system))
    }

    @Test
    fun `date and time are taken as they are`() {
        val clock = JingleClock.fromProperty("2026-12-31T23:59", system)
        assertEquals(LocalDateTime.parse("2026-12-31T23:59"), LocalDateTime.now(clock))
    }

    @Test
    fun `a date alone keeps the time of day`() {
        val clock = JingleClock.fromProperty("2026-12-24", system)
        assertEquals(LocalDateTime.parse("2026-12-24T15:20"), LocalDateTime.now(clock))
    }

    @Test
    fun `garbage falls back to the real time`() {
        assertSame(system, JingleClock.fromProperty("tomorrow", system))
    }
}
