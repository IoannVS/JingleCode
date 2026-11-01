package io.github.ioannvs.jinglecode.core

import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

enum class SeasonMode { AUTO, ALWAYS, OFF }

/** The holiday season: from December 1 to the Old New Year, January 14, both included. */
object Season {
    private val START = MonthDay.of(Month.DECEMBER, 1)
    private val END = MonthDay.of(Month.JANUARY, 14)

    fun isOn(mode: SeasonMode, date: LocalDate): Boolean = when (mode) {
        SeasonMode.AUTO -> MonthDay.from(date).let { it >= START || it <= END }
        SeasonMode.ALWAYS -> true
        SeasonMode.OFF -> false
    }
}
