package io.github.ioannvs.jinglecode.yeti

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/** The yeti's day: how many times he came, whether he was caught, and when he may come next. */
internal data class YetiDay(
    val date: LocalDate,
    val visits: Int = 0,
    val caught: Boolean = false,
    val notBefore: LocalDateTime? = null,
)

/**
 * When the yeti comes: on about one day in three (the same days for everyone, picked by the date), up to
 * [MAX_VISITS] times on such a day, at least an hour apart, the first a while after the day began.
 * Once caught he doesn't come again that day. A call from a comment doesn't ask this.
 */
internal object YetiSchedule {
    const val VISIT_DAY_CHANCE = 1.0 / 3
    const val MAX_VISITS = 3

    /** The first visit of a day comes this many minutes after the yeti first looked, at random. */
    val FIRST_DELAY_MIN = 5..40

    /** And each next one this many minutes after the last. */
    val NEXT_DELAY_MIN = 60..90

    private const val SALT = 0x7e71L

    fun isVisitDay(date: LocalDate): Boolean = Random(date.toEpochDay() * 31 + SALT).nextDouble() < VISIT_DAY_CHANCE

    /** [day] if it is still today, otherwise a new day, its first visit a while from [now]. */
    fun today(day: YetiDay?, now: LocalDateTime, random: Random): YetiDay =
        if (day != null && day.date == now.toLocalDate()) day
        else YetiDay(now.toLocalDate(), notBefore = now.plusMinutes(FIRST_DELAY_MIN.random(random).toLong()))

    fun mayCome(day: YetiDay, now: LocalDateTime): Boolean =
        isVisitDay(day.date) && !day.caught && day.visits < MAX_VISITS && (day.notBefore == null || !now.isBefore(day.notBefore))

    fun afterVisit(day: YetiDay, now: LocalDateTime, random: Random): YetiDay =
        day.copy(visits = day.visits + 1, notBefore = now.plusMinutes(NEXT_DELAY_MIN.random(random).toLong()))

    fun afterCatch(day: YetiDay): YetiDay = day.copy(caught = true)
}
