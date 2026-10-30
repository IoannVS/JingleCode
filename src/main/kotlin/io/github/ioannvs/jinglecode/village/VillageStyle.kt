package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.hash
import java.time.LocalDate

/** How the village is composed, the four variants of `docs/mockup/jingle-code-village.html` (see `docs/VILLAGE.md`). */
enum class VillageStyle {
    /** `v1`: a farmstead on a hill at the right, a lone barn at the left. */
    HILL_FARM,

    /** `v2`: mountains with a ski slope, a frozen pond, houses in front. */
    VALLEY,

    /** `v3`: a street going up to the chapel on the top. */
    UPHILL_STREET,

    /** `v4`: one cabin in a forest clearing. */
    FOREST_CLEARING,

    /** One of the four, a new one each day. */
    RANDOM;

    /** The style shown on [date]: [RANDOM] picks by the date, so it stays the same all day, also over IDE restarts. */
    fun on(date: LocalDate): VillageStyle {
        if (this != RANDOM) return this
        val styles = entries.filter { it != RANDOM }
        return styles[(hash(date.toEpochDay().toInt(), 41) * styles.size).toInt()]
    }

    internal val scene: VillageScene
        get() = when (this) {
            HILL_FARM -> HillFarmScene
            VALLEY -> ValleyScene
            UPHILL_STREET -> UphillStreetScene
            FOREST_CLEARING -> ForestClearingScene
            RANDOM -> error("resolve RANDOM with on(date) first")
        }
}
