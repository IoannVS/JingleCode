package io.github.ioannvs.jinglecode.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SeasonTest {

    @Test
    fun `auto season runs from December 1 to January 14`() {
        val expected = mapOf(
            "2026-11-30" to false,
            "2026-12-01" to true,
            "2026-12-31" to true,
            "2027-01-01" to true,
            "2027-01-14" to true,
            "2027-01-15" to false,
            "2027-07-01" to false,
        )
        for ((date, on) in expected) {
            assertEquals(date, on, Season.isOn(SeasonMode.AUTO, LocalDate.parse(date)))
        }
    }

    @Test
    fun `always and off ignore the date`() {
        val summer = LocalDate.parse("2026-07-01")
        val december = LocalDate.parse("2026-12-20")
        assertEquals(true, Season.isOn(SeasonMode.ALWAYS, summer))
        assertEquals(false, Season.isOn(SeasonMode.OFF, december))
    }
}
