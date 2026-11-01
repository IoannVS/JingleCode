package io.github.ioannvs.jinglecode.hohoho

import io.github.ioannvs.jinglecode.drift.SLEIGH_BED
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the sleigh is: `drawSleigh`'s [x], [y], and how long ago it stalled and started coughing, if it did. */
internal data class SleighPose(val x: Int, val y: Int, val smokeMs: Long? = null)

/** The gift the sleigh dropped: its top left, how many of its rows are in the snow, how long ago it landed. */
internal data class GiftFall(val x: Int, val y: Int, val sunk: Int, val landedMs: Long?)

/**
 * Where the gift goes: the column [x] above the drift in the Project panel and the snow line [ground]
 * there, both in the flight's logical pixels.
 */
internal data class DropTarget(val x: Int, val ground: Int)

/**
 * Santa's flight across the whole window on `// ho ho ho`, by time, in the flight's logical pixels.
 * Mockup: the sleigh crosses the sky left to right in a gentle arc, a sparkling trail and the blinking
 * `HO HO HO` behind it. Here the sky is the whole window, over everything. On `ho ho no` it flies in,
 * stalls, coughs smoke and backs off the way it came. On the way it drops a gift into the drift.
 *
 * Pure: everything is a function of the call, the time and the window's size.
 */
internal object Flyby {
    const val FLIGHT_MS = 5200L
    const val REFUSED_MS = 6400L

    /** On `ho ho no`: flying in until this share of the time, stalled until [STALL_END], then backing off. */
    const val STALL_START = 0.4
    const val STALL_END = 0.62

    /** Where it stalls: this share of the window's width. */
    const val STALL_AT = 0.4

    /** Logical pixels per second squared, for the gift. */
    const val GRAVITY = 700.0
    const val SINK_MS = 450L
    const val PUFF_MS = 500L

    /** Longer than any gift takes to fall and sink, even from the top of a tall window. */
    const val GIFT_MS = 3000L

    const val GIFT_SIZE = 5

    /** How often the sleigh gallops, as the mockup's `gallop`. */
    const val GALLOP_MS = 120L

    /** At night (21:00 to 5:00) every reindeer's nose glows and the lead's lights the way. */
    fun isNight(time: LocalDateTime): Boolean = time.hour >= 21 || time.hour < 5

    fun team(call: HoHoHoCall): Int = if (call.loud) 4 else 2

    /** A loud call flies twice as big. */
    fun magnify(call: HoHoHoCall): Int = if (call.loud) 2 else 1

    /** How far the sleigh, its team and the light of the lead's nose reach ahead of its x. */
    fun front(call: HoHoHoCall): Int = 10 * team(call) + 22

    /** How far the trail and the banner reach behind its x. */
    fun back(call: HoHoHoCall): Int = max(29, 4 * call.banner.length + 3)

    fun durationMs(call: HoHoHoCall): Long = if (call.refused) REFUSED_MS else FLIGHT_MS

    /** How long the whole thing lasts, the gift included. */
    fun lastsMs(call: HoHoHoCall): Long = durationMs(call) + if (call.refused) 0 else GIFT_MS

    /** Where the banner's top left is, for the sleigh at [pose]: behind it and above, as in the mockup. */
    fun bannerX(call: HoHoHoCall, pose: SleighPose): Int = pose.x - 2 - 4 * call.banner.length

    fun bannerY(pose: SleighPose): Int = pose.y - 8

    /** The line it flies along at the start and the end, and how much higher it gets in the middle. */
    private fun base(height: Int): Int = (height * 0.35).toInt()

    private fun lift(height: Int): Int = max(5, (height * 0.1).toInt())

    /** Where the sleigh is [ageMs] into the flight across a window [width] by [height]; `null` before or after. */
    fun sleigh(call: HoHoHoCall, ageMs: Long, width: Int, height: Int): SleighPose? {
        val duration = durationMs(call)
        if (ageMs !in 0 until duration) return null
        val p = ageMs.toDouble() / duration
        val front = front(call)
        val base = base(height)
        val lift = lift(height)
        if (!call.refused) {
            val x = PixelCanvas.jsRound(-front + p * (width + front + back(call)))
            return SleighPose(x, base - PixelCanvas.jsRound(sin(p * PI) * lift))
        }
        val stop = (width * STALL_AT).toInt()
        return when {
            p < STALL_START -> {
                val q = p / STALL_START
                SleighPose(PixelCanvas.jsRound(-front + (1 - (1 - q) * (1 - q)) * (stop + front)), base - PixelCanvas.jsRound(sin(q * PI / 2) * lift))
            }
            p < STALL_END -> {
                val since = ageMs - (STALL_START * duration).toLong()
                // It shakes on the spot, a pixel either way.
                SleighPose(stop + if ((since / 70) % 2 == 0L) 1 else -1, base - lift, since)
            }
            else -> {
                val q = (p - STALL_END) / (1 - STALL_END)
                val since = ageMs - (STALL_START * duration).toLong()
                SleighPose(PixelCanvas.jsRound(stop - q * q * (stop + front)), base - PixelCanvas.jsRound(cos(q * PI / 2) * lift), since)
            }
        }
    }

    /**
     * The gift [ageMs] into the flight: the sleigh lets it go right above [target], it falls into the
     * snow there and sinks into it. `null` before it is dropped and after it is gone; never on `ho ho no`.
     */
    fun gift(call: HoHoHoCall, ageMs: Long, width: Int, height: Int, target: DropTarget): GiftFall? {
        if (call.refused) return null
        val duration = durationMs(call)
        val front = front(call)
        val span = width + front + back(call)
        // The gift sits in the bed; the sleigh is right above the target when the bed is.
        val sleighX = target.x - GIFT_SIZE / 2 - SLEIGH_BED.x
        val releaseP = (sleighX + front).toDouble() / span
        if (releaseP !in 0.0..1.0) return null
        val releaseMs = (releaseP * duration).toLong()
        val t = ageMs - releaseMs
        if (t < 0) return null
        val from = base(height) - PixelCanvas.jsRound(sin(releaseP * PI) * lift(height)) + SLEIGH_BED.y - GIFT_SIZE
        val bottom = target.ground - GIFT_SIZE
        if (bottom <= from) return null
        val x = target.x - GIFT_SIZE / 2
        val fallMs = (sqrt(2.0 * (bottom - from) / GRAVITY) * 1000).toLong()
        if (t < fallMs) {
            val s = t / 1000.0
            return GiftFall(x, from + (GRAVITY * s * s / 2).toInt(), 0, null)
        }
        val landed = t - fallMs
        if (landed >= SINK_MS + PUFF_MS) return null
        return GiftFall(x, bottom, minOf(GIFT_SIZE + 1L, landed * (GIFT_SIZE + 1) / SINK_MS).toInt(), landed)
    }
}
