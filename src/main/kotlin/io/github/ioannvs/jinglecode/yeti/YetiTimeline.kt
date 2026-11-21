package io.github.ioannvs.jinglecode.yeti

/** What the yeti does: stands at the end of the line, or waves when caught. */
internal enum class YetiMove { STAND, WAVE }

/** Where he looks. */
internal enum class YetiEyes { AHEAD, LEFT, RIGHT, SHUT }

/**
 * The yeti at one moment: what he does, where he looks, how many rows of him are still [sunk] below the line
 * (rising at the start, sinking at the end) and the [step] he is at.
 */
internal data class YetiPose(val move: YetiMove, val eyes: YetiEyes, val sunk: Int, val step: Int)

/**
 * How his footprints run off along the line once he is gone, picked at random on every catch:
 * at a walk, at a run, or in leaps that get longer and longer.
 */
internal enum class TrailKind { WALK, RUN, LEAP }

/**
 * A footprint on the line: [x] logical pixels right of where the yeti stood, the [upper] or the lower foot.
 * Prints of a leap come in pairs, side by side.
 */
internal data class LinePrint(val x: Int, val upper: Boolean)

/** After the click: the yeti (until he vanishes), the snow puff he vanishes in, and his footprints. */
internal data class CaughtScene(val yeti: YetiPose?, val puff: Int?, val prints: List<LinePrint>)

/** The yeti's visit as a function of time, steps of [STEP_MS] like the sprites' frames. */
internal object YetiTimeline {
    const val STEP_MS = 140L

    /** He rises out of the line in this many steps, and sinks back in as many. */
    const val RISE_STEPS = 4

    const val WAVE_STEPS = 4

    /** The snow puff he vanishes in, frames. */
    const val PUFF_STEPS = 3

    /** When he is gone after the click, and his footprints begin to run. */
    const val GONE_MS = WAVE_STEPS * STEP_MS

    /** How long a footprint stays before it melts. */
    const val PRINT_LIFE_MS = 1_200L

    /** How long he stands if nobody catches him or hides him, milliseconds. */
    val STAY_MS = 60_000L..120_000L

    /** He looks around now and then, and blinks. */
    private val LOOK = listOf(
        YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.LEFT,
        YetiEyes.LEFT, YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.RIGHT, YetiEyes.RIGHT, YetiEyes.AHEAD,
        YetiEyes.AHEAD, YetiEyes.AHEAD, YetiEyes.SHUT, YetiEyes.AHEAD,
    )

    /** Standing, [ageMs] into a visit of [lastsMs]; null once it is over. */
    fun standing(ageMs: Long, lastsMs: Long): YetiPose? {
        if (ageMs < 0 || ageMs >= lastsMs) return null
        val step = (ageMs / STEP_MS).toInt()
        val left = ((lastsMs - 1 - ageMs) / STEP_MS).toInt()
        val sunk = when {
            step < RISE_STEPS -> YETI_H - (step + 1) * YETI_H / RISE_STEPS
            left < RISE_STEPS -> YETI_H - left * YETI_H / RISE_STEPS
            else -> 0
        }
        val eyes = if (sunk > 0) YetiEyes.AHEAD else LOOK[step % LOOK.size]
        return YetiPose(YetiMove.STAND, eyes, sunk, step)
    }

    /** Footprint [i] of a [kind] trail: when it appears after he is gone, and where. */
    private class Planned(val atMs: Long, val print: LinePrint)

    private fun plan(kind: TrailKind): List<Planned> = when (kind) {
        // Step by step, left and right foot in turn.
        TrailKind.WALK -> List(14) { Planned(it * 160L, LinePrint(2 + it * 5, it % 2 == 0)) }
        // Quicker and farther apart.
        TrailKind.RUN -> List(16) { Planned(it * 90L, LinePrint(2 + it * 8, it % 2 == 0)) }
        // Both feet together, each leap longer than the last.
        TrailKind.LEAP -> {
            var x = 2
            (0 until 7).flatMap { i ->
                if (i > 0) x += 6 + 3 * i
                listOf(Planned(i * 200L, LinePrint(x, true)), Planned(i * 200L, LinePrint(x, false)))
            }
        }
    }

    /** From the click until the last footprint has melted. */
    fun caughtMs(kind: TrailKind): Long = GONE_MS + plan(kind).maxOf { it.atMs } + PRINT_LIFE_MS

    /** How far right of where he stood his footprints go, logical pixels. */
    fun reach(kind: TrailKind): Int = plan(kind).maxOf { it.print.x } + PRINT_W

    /** [sinceMs] after the click: he waves and vanishes in a puff of snow, his footprints run off; null once over. */
    fun caught(sinceMs: Long, kind: TrailKind): CaughtScene? {
        if (sinceMs < 0 || sinceMs >= caughtMs(kind)) return null
        val step = (sinceMs / STEP_MS).toInt()
        val yeti = if (step < WAVE_STEPS) YetiPose(YetiMove.WAVE, YetiEyes.AHEAD, 0, step) else null
        val puff = (step - WAVE_STEPS).takeIf { it in 0 until PUFF_STEPS }
        val trailMs = sinceMs - GONE_MS
        val prints = plan(kind).filter { trailMs >= it.atMs && trailMs < it.atMs + PRINT_LIFE_MS }.map { it.print }
        return CaughtScene(yeti, puff, prints)
    }
}
