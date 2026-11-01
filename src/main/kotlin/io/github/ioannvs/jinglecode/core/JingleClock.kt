package io.github.ioannvs.jinglecode.core

import com.intellij.openapi.diagnostic.logger
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * "Now" for every decoration: never call `LocalDate.now()` directly.
 *
 * The sandbox can pretend it's another day with `-Djinglecode.now=2026-12-31T23:59`, or a date alone
 * to keep the time of day (`./gradlew runIde -PjingleNow=...` passes it). The pretended time keeps running.
 */
object JingleClock {
    const val PROPERTY = "jinglecode.now"

    private val clock: Clock = fromProperty(System.getProperty(PROPERTY))

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    internal fun fromProperty(value: String?, system: Clock = Clock.systemDefaultZone()): Clock {
        if (value.isNullOrBlank()) return system
        val start = parse(value.trim(), system)
        if (start == null) {
            logger<JingleClock>().warn("Jingle Code: can't parse -D$PROPERTY=$value, using the real time")
            return system
        }
        return Clock.offset(system, Duration.between(LocalDateTime.now(system), start))
    }

    private fun parse(value: String, system: Clock): LocalDateTime? =
        try {
            if ('T' in value) LocalDateTime.parse(value) else LocalDate.parse(value).atTime(LocalTime.now(system))
        }
        catch (_: DateTimeParseException) {
            null
        }
}
