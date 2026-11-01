package io.github.ioannvs.jinglecode.hohoho

import io.github.ioannvs.jinglecode.icicles.IcicleFall
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Which flight `// ho ho ho` sends; picked at random each time. */
internal enum class FlightKind { CLASSIC, HEIST }

/** How the snowball fight ends. */
internal enum class HeistEnd {
    /** Santa hits the snowman: the hat flies off, the snowman falls out, Santa jumps into his sleigh. */
    SANTA_WINS,

    /** The snowman hits Santa: Santa stops, shakes his fist and turns back. */
    SNOWMAN_WINS,

    /** Santa hits the snowman, and he falls apart: hat, head and body drop, the empty sleigh flies on. */
    CRUMBLE,
}

/** Who sits in the sleigh. */
internal enum class Driver { SNOWMAN, SANTA, NOBODY }

/** What Santa's free arm does. */
internal enum class Arm { REST, THROW, FIST }

/** What falls or flies: a snowball, or a piece knocked off the snowman. */
internal enum class Piece { SNOWBALL, HAT, SNOWMAN, HEAD, BODY }

/** Santa on his reindeer at [x], [y] (`drawReindeer`'s), or the reindeer alone once Santa is back in the sleigh. */
internal data class RiderPose(val x: Int, val y: Int, val santa: Boolean, val arm: Arm)

/** A [piece] in the air at [x], [y]; once it has hit something, [splashMs] ago, it is snow flying apart there. */
internal data class Flying(val piece: Piece, val x: Int, val y: Int, val splashMs: Long? = null)

/** The chase now; everything `null` or empty once it is over. */
internal class HeistFrame(
    val sleigh: SleighPose?,
    val driver: Driver,
    val snowmanThrows: Boolean,
    val rider: RiderPose?,
    val flying: List<Flying>,
) {
    val isOver: Boolean get() = sleigh == null && rider == null && flying.isEmpty()
}

/**
 * The snowman steals Santa's sleigh: he flies across the window in it, Santa on a reindeer right behind
 * him, and they throw snowballs at each other. The misses fall down the window and burst at its bottom.
 * Then the last snowball hits, and the [HeistEnd] picked by the seed plays out.
 *
 * Pure: everything is a function of the call, the seed, the time and the window's size, in the flight's
 * logical pixels, like [Flyby].
 */
internal object Heist {
    const val DURATION_MS = 8400L

    /** The turned-back Santa and the last pieces falling may take this much longer than the sleigh. */
    const val LASTS_MS = DURATION_MS + 3400L

    /** Santa catches up until this share of the time; the fight goes on until the last throw at [LAST_THROW]. */
    const val FIGHT_START = 0.28
    const val LAST_THROW = 0.70

    const val THROW_MS = 450L

    /** How long the thrower's arm stays up. */
    const val ARM_MS = 220L

    /** How far behind the sleigh Santa rides: coming in, during the fight, and when he jumps over. */
    const val START_GAP = 110
    const val GAP = 46
    const val JUMP_GAP = 14

    /** After the hit: Santa catches up to the sleigh in this long. */
    const val CATCH_MS = 900L

    /** The beaten Santa slows down, shakes his fist until [FIST_END], and is gone after [TURNED_BACK]. */
    const val STOP_MS = 600L
    const val FIST_END = 1800L
    const val TURNED_BACK = 3200L

    /** How high a snowball's arc is. */
    const val ARC = 6

    /** Logical pixels per millisecond squared: what falls, falls like the gift. */
    private const val G = Flyby.GRAVITY / 1_000_000

    val SPLASH_MS = IcicleFall.SHATTER_MS

    fun end(seed: Int): HeistEnd = HeistEnd.entries[floor(hash(seed, 3) * HeistEnd.entries.size).toInt().coerceIn(0, HeistEnd.entries.size - 1)]

    /** When the last snowball arrives. */
    val hitMs: Long = (LAST_THROW * DURATION_MS).toLong() + THROW_MS

    private class Throw(val at: Long, val bySanta: Boolean, val hits: Boolean, val high: Boolean)

    /** The snowman throws first; they take turns every 380 to 580 ms, and the last one hits. */
    private fun throws(seed: Int, end: HeistEnd): List<Throw> = buildList {
        val last = (LAST_THROW * DURATION_MS).toLong()
        var at = (FIGHT_START * DURATION_MS).toLong() + 300
        var bySanta = false
        var k = 0
        while (at <= last - THROW_MS - 100) {
            add(Throw(at, bySanta, hits = false, high = hash(seed + k, 5) < 0.5))
            at += 380 + (hash(seed, k + 11) * 200).toLong()
            bySanta = !bySanta
            k++
        }
        add(Throw(last, bySanta = end != HeistEnd.SNOWMAN_WINS, hits = true, high = false))
    }

    /** The flight's size and speed for one window. */
    private class Course(call: HoHoHoCall, width: Int, height: Int) {
        val front = Flyby.front(call)
        val bottom = height - 1
        val base = (height * 0.35).toInt()
        val lift = maxOf(5, (height * 0.1).toInt())

        /** Logical pixels per millisecond: the sleigh's x goes from just off the left to past the right with Santa behind. */
        val speed = (width + front + START_GAP + 10 + Flyby.back(call)).toDouble() / DURATION_MS

        fun sleighX(t: Long): Double = -front + t * speed

        fun sleighY(t: Long): Double = base - sin((t.toDouble() / DURATION_MS).coerceIn(0.0, 1.0) * PI) * lift
    }

    private fun gap(t: Long, end: HeistEnd): Double {
        val fightStart = (FIGHT_START * DURATION_MS).toLong()
        if (t < fightStart) {
            val q = t.toDouble() / fightStart
            return START_GAP + (GAP - START_GAP) * (1 - (1 - q) * (1 - q))
        }
        val fighting = GAP + 5 * sin((t - fightStart) / 300.0)
        if (t < hitMs || end != HeistEnd.SANTA_WINS) return fighting
        val atHit = GAP + 5 * sin((hitMs - fightStart) / 300.0)
        val since = t - hitMs
        return if (since < CATCH_MS) atHit + (JUMP_GAP - atHit) * since / CATCH_MS else JUMP_GAP.toDouble()
    }

    /** Where Santa's reindeer is; `null` once the beaten Santa has turned back out of the window. */
    private fun riderX(course: Course, t: Long, end: HeistEnd): Double? {
        if (end != HeistEnd.SNOWMAN_WINS || t <= hitMs) return course.sleighX(t) - gap(t, end)
        val atHit = course.sleighX(hitMs) - gap(hitMs, end)
        val since = (t - hitMs).toDouble()
        val stop = atHit + course.speed * STOP_MS / 2
        return when {
            since < STOP_MS -> atHit + course.speed * (since - since * since / (2 * STOP_MS))
            since < FIST_END -> stop
            since < TURNED_BACK -> {
                val q = (since - FIST_END) / (TURNED_BACK - FIST_END)
                stop - q * q * (stop + 12)
            }
            else -> null
        }
    }

    private fun riderY(course: Course, t: Long, end: HeistEnd): Double {
        val at = if (end == HeistEnd.SNOWMAN_WINS && t > hitMs) hitMs else t
        return course.sleighY(at) + if (t > hitMs && end == HeistEnd.SNOWMAN_WINS) 0.0 else sin(t / 170.0)
    }

    fun frame(call: HoHoHoCall, seed: Int, ageMs: Long, width: Int, height: Int): HeistFrame {
        val course = Course(call, width, height)
        val end = end(seed)
        val t = ageMs
        val hit = t >= hitMs
        val since = t - hitMs
        val sleigh = if (t in 0 until DURATION_MS) SleighPose(PixelCanvas.jsRound(course.sleighX(t)), PixelCanvas.jsRound(course.sleighY(t))) else null
        val driver = when {
            !hit -> Driver.SNOWMAN
            end == HeistEnd.SNOWMAN_WINS -> Driver.SNOWMAN
            end == HeistEnd.SANTA_WINS && since >= CATCH_MS -> Driver.SANTA
            else -> Driver.NOBODY
        }
        val all = throws(seed, end)
        val riderX = if (t in 0 until DURATION_MS || end == HeistEnd.SNOWMAN_WINS) riderX(course, t, end) else null
        val rider = riderX?.let { x ->
            val santaThrows = all.any { it.bySanta && t - it.at in 0 until ARM_MS }
            val arm = when {
                end == HeistEnd.SNOWMAN_WINS && hit && since in STOP_MS until FIST_END -> Arm.FIST
                santaThrows -> Arm.THROW
                else -> Arm.REST
            }
            RiderPose(PixelCanvas.jsRound(x), PixelCanvas.jsRound(riderY(course, t, end)), santa = driver != Driver.SANTA, arm = arm)
        }
        val flying = buildList {
            for (it in all) ball(course, it, t, end)?.let(::add)
            if (hit && end != HeistEnd.SNOWMAN_WINS) addAll(knockedOff(course, end, since))
        }
        return HeistFrame(
            sleigh,
            driver,
            snowmanThrows = all.any { !it.bySanta && t - it.at in 0 until ARM_MS },
            rider,
            flying,
        )
    }

    /** Where the thrower's hand is, and where the one aimed at is when the ball gets there. */
    private fun ball(course: Course, thrown: Throw, t: Long, end: HeistEnd): Flying? {
        val since = t - thrown.at
        if (since < 0) return null
        val arrive = thrown.at + THROW_MS
        val (fromX, fromY) =
            if (thrown.bySanta) (riderX(course, thrown.at, end) ?: return null) + 5 to riderY(course, thrown.at, end) - 1
            else course.sleighX(thrown.at) + 1 to course.sleighY(thrown.at) - 4
        val (toX, aimY) =
            if (thrown.bySanta) course.sleighX(arrive) + 3 to course.sleighY(arrive) - 3
            else (riderX(course, arrive, end) ?: return null) + 3 to riderY(course, arrive, end) + 1
        val toY = aimY + if (thrown.hits) 0 else if (thrown.high) -7 else 6
        if (since < THROW_MS) {
            val q = since.toDouble() / THROW_MS
            return Flying(Piece.SNOWBALL, PixelCanvas.jsRound(fromX + (toX - fromX) * q), PixelCanvas.jsRound(fromY + (toY - fromY) * q - ARC * 4 * q * (1 - q)))
        }
        val after = since - THROW_MS
        if (thrown.hits) return if (after < SPLASH_MS) Flying(Piece.SNOWBALL, PixelCanvas.jsRound(toX), PixelCanvas.jsRound(toY), after) else null
        // A miss flies on the way it went and falls down the window.
        val vx = (toX - fromX) / THROW_MS
        val vy = (toY - fromY + ARC * 4) / THROW_MS
        return fall(Piece.SNOWBALL, toX, toY, vx, vy, after, course.bottom)
    }

    /** The hat and the snowman, or his pieces, knocked off the sleigh [since] the hit. */
    private fun knockedOff(course: Course, end: HeistEnd, since: Long): List<Flying> {
        val x = course.sleighX(hitMs)
        val y = course.sleighY(hitMs)
        val v = course.speed
        return listOfNotNull(
            fall(Piece.HAT, x + 1, y - 7, v * 0.4, -0.06, since, course.bottom),
            if (end == HeistEnd.SANTA_WINS) fall(Piece.SNOWMAN, x, y - 4, v * 0.7, -0.02, since - 120, course.bottom)
            else fall(Piece.HEAD, x + 2, y - 4, v * 0.9, -0.05, since, course.bottom),
            if (end == HeistEnd.CRUMBLE) fall(Piece.BODY, x + 1, y, v * 0.5, -0.01, since, course.bottom) else null,
        )
    }

    /** [piece] thrown from [x0], [y0] at [vx], [vy] per ms, [t] ms ago; it bursts at the [bottom] of the window. */
    private fun fall(piece: Piece, x0: Double, y0: Double, vx: Double, vy: Double, t: Long, bottom: Int): Flying? {
        if (t < 0) return null
        val y = y0 + vy * t + G * t * t / 2
        if (y < bottom) return Flying(piece, PixelCanvas.jsRound(x0 + vx * t), PixelCanvas.jsRound(y))
        val landed = (-vy + sqrt(vy * vy + 2 * G * (bottom - y0))) / G
        val splash = (t - landed).toLong()
        return if (splash < SPLASH_MS) Flying(piece, PixelCanvas.jsRound(x0 + vx * landed), bottom, splash) else null
    }
}
