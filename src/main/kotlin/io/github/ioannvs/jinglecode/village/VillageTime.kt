package io.github.ioannvs.jinglecode.village

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the village looks like at a moment: the [date] picks the details (the seed) and the random
 * style, so they change once a day; in the [evening] windows and lamps are lit and the moon is out.
 */
data class VillageTime(val date: LocalDate, val evening: Boolean) {

    /** The mockup's `seed`: details differ from day to day, the composition stays. */
    val seed: Int get() = (date.toEpochDay() % 100_000).toInt()

    companion object {
        /** Evening from 17:00 until morning (6:00), as in the spec. */
        fun at(now: LocalDateTime): VillageTime = VillageTime(now.toLocalDate(), now.hour !in 6..16)
    }
}
