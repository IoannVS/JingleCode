package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import kotlin.math.max

/** What the sleigh comes for. */
enum class DeliveryKind {
    /** After a push: the elves load the gifts, and the sleigh takes them away. */
    DELIVER,

    /** After a rejected push: the same, then the sleigh comes back and the elves unload. */
    RETURN,
}

/** The sleigh's visit [ageMs] into it, for [gifts] gifts. */
data class DeliveryView(val kind: DeliveryKind, val ageMs: Long, val gifts: Int)

/** Something at a place in the scene, logical pixels. */
internal data class At(val x: Int, val y: Int)

/** How an elf is doing. */
internal enum class ElfPose { RUN, STAND, TOSS, JUMP }

internal data class ElfState(val at: At, val pose: ElfPose, val step: Int)

/** A gift in the air between the pile and the sleigh: which one and where its top left corner is. */
internal data class FlyingGift(val index: Int, val at: At)

/**
 * The visit, by time. The sleigh flies in and hangs over the pile while two elves run in from the
 * right; the elves toss the gifts up into the sleigh one after the other, from the top of the pile
 * down, jump in after them, and the sleigh flies off. Returning, it comes back the same way, the
 * elves jump out, toss the gifts back down to their places, bottom first, and run off; the empty
 * sleigh leaves.
 *
 * Pure: everything is a function of the time, the number of gifts and the scene's places.
 */
internal object Delivery {
    const val IN_MS = 900L
    const val TOSS_EVERY_MS = 180L
    const val ARC_MS = 300L
    const val JUMP_MS = 300L
    const val OUT_MS = 800L
    const val AWAY_MS = 400L
    const val RUN_OFF_MS = 700L

    /** The sleigh appears from here and leaves to the top right. */
    private val START = At(-40, -14)

    fun loadedAt(gifts: Int): Long = IN_MS + max(0, gifts - 1) * TOSS_EVERY_MS + if (gifts > 0) ARC_MS else 0
    fun departAt(gifts: Int): Long = loadedAt(gifts) + JUMP_MS
    fun goneAt(gifts: Int): Long = departAt(gifts) + OUT_MS
    fun backAt(gifts: Int): Long = goneAt(gifts) + AWAY_MS + IN_MS
    fun outAt(gifts: Int): Long = backAt(gifts) + JUMP_MS
    fun unloadedAt(gifts: Int): Long = outAt(gifts) + max(0, gifts - 1) * TOSS_EVERY_MS + if (gifts > 0) ARC_MS else 0

    fun durationMs(kind: DeliveryKind, gifts: Int): Long = when (kind) {
        DeliveryKind.DELIVER -> goneAt(gifts)
        DeliveryKind.RETURN -> unloadedAt(gifts) + max(RUN_OFF_MS, OUT_MS)
    }

    private fun smooth(p: Double): Double {
        val c = p.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }

    private fun lerp(a: At, b: At, p: Double): At {
        val s = smooth(p)
        return At(PixelCanvas.jsRound(a.x + (b.x - a.x) * s), PixelCanvas.jsRound(a.y + (b.y - a.y) * s))
    }

    /** A hop from [a] to [b] that rises [height] pixels in the middle. */
    private fun arc(a: At, b: At, p: Double, height: Int): At {
        val c = p.coerceIn(0.0, 1.0)
        val x = a.x + (b.x - a.x) * c
        val y = a.y + (b.y - a.y) * c - height * 4 * c * (1 - c)
        return At(PixelCanvas.jsRound(x), PixelCanvas.jsRound(y))
    }

    private fun end(width: Int) = At(width + 8, -16)

    /** Where the sleigh is (as `drawSleigh` takes it), or `null` while it is off the scene. [hover] is over the pile. */
    fun sleigh(view: DeliveryView, width: Int, hover: At): At? {
        val t = view.ageMs
        val n = view.gifts
        return when {
            t < IN_MS -> lerp(START, hover, t / IN_MS.toDouble())
            t < departAt(n) -> hover
            t < goneAt(n) -> lerp(hover, end(width), (t - departAt(n)) / OUT_MS.toDouble())
            view.kind == DeliveryKind.DELIVER -> null
            t < goneAt(n) + AWAY_MS -> null
            t < backAt(n) -> lerp(START, hover, (t - goneAt(n) - AWAY_MS) / IN_MS.toDouble())
            t < unloadedAt(n) -> hover
            t < unloadedAt(n) + OUT_MS -> lerp(hover, end(width), (t - unloadedAt(n)) / OUT_MS.toDouble())
            else -> null
        }
    }

    /** How many of [n] tosses, one every [TOSS_EVERY_MS] from [from], have begun / landed by [t]. */
    private fun tossed(t: Long, from: Long, n: Int): Int = (0 until n).count { t >= from + it * TOSS_EVERY_MS }
    private fun landed(t: Long, from: Long, n: Int): Int = (0 until n).count { t >= from + it * TOSS_EVERY_MS + ARC_MS }

    /** How many gifts are in the pile now, out of the visit's [DeliveryView.gifts]; `null` once the visit no longer decides. */
    fun inPile(view: DeliveryView): Int? {
        val t = view.ageMs
        val n = view.gifts
        return when {
            t < goneAt(n) -> n - tossed(t, IN_MS, n)
            view.kind == DeliveryKind.DELIVER -> null
            t < outAt(n) -> 0
            t < unloadedAt(n) -> landed(t, outAt(n), n)
            else -> null
        }
    }

    /** How many gifts ride in the sleigh now. */
    fun inSleigh(view: DeliveryView): Int {
        val t = view.ageMs
        val n = view.gifts
        return when {
            t < outAt(n) || view.kind == DeliveryKind.DELIVER -> landed(t, IN_MS, n)
            else -> n - tossed(t, outAt(n), n)
        }
    }

    /**
     * The gifts in the air: loading takes them from the top of the pile ([places] are their spots
     * in it) to the sleigh's [bed]; unloading puts them back, bottom first.
     */
    fun flying(view: DeliveryView, places: List<At>, bed: At): List<FlyingGift> {
        val t = view.ageMs
        val n = view.gifts
        val result = ArrayList<FlyingGift>()
        for (i in 0 until n) {
            val loadStart = IN_MS + i * TOSS_EVERY_MS
            if (t in loadStart until loadStart + ARC_MS) {
                val index = n - 1 - i
                result += FlyingGift(index, arc(places[index], bed, (t - loadStart) / ARC_MS.toDouble(), 10))
            }
            if (view.kind == DeliveryKind.RETURN) {
                val unloadStart = outAt(n) + i * TOSS_EVERY_MS
                if (t in unloadStart until unloadStart + ARC_MS) {
                    result += FlyingGift(i, arc(bed, places[i], (t - unloadStart) / ARC_MS.toDouble(), 6))
                }
            }
        }
        return result
    }

    /**
     * The two elves: [spots] are where they stand by the pile, [seat] where they sit in the sleigh,
     * [entry] where they run in from (and off to, after unloading). Empty while they are not in the scene.
     */
    fun elves(view: DeliveryView, spots: List<At>, seat: At, entry: At): List<ElfState> {
        val t = view.ageMs
        val n = view.gifts
        val step = (t / 100).toInt()
        return spots.mapIndexedNotNull { e, spot ->
            fun tossing(from: Long) = (0 until n).any { it % 2 == e && t in from + it * TOSS_EVERY_MS until from + it * TOSS_EVERY_MS + 120 }
            when {
                t < IN_MS -> ElfState(lerp(entry, spot, t / IN_MS.toDouble()), ElfPose.RUN, step)
                t < loadedAt(n) -> ElfState(spot, if (tossing(IN_MS)) ElfPose.TOSS else ElfPose.STAND, step)
                t < departAt(n) -> ElfState(arc(spot, seat, (t - loadedAt(n)) / JUMP_MS.toDouble(), 6), ElfPose.JUMP, step)
                view.kind == DeliveryKind.DELIVER -> null
                t < backAt(n) -> null
                t < outAt(n) -> ElfState(arc(seat, spot, (t - backAt(n)) / JUMP_MS.toDouble(), 4), ElfPose.JUMP, step)
                t < unloadedAt(n) -> ElfState(spot, if (tossing(outAt(n))) ElfPose.TOSS else ElfPose.STAND, step)
                t < unloadedAt(n) + RUN_OFF_MS -> ElfState(lerp(spot, entry, (t - unloadedAt(n)) / RUN_OFF_MS.toDouble()), ElfPose.RUN, step)
                else -> null
            }
        }
    }

    /** Whether the elves ride in the sleigh now (drawn as heads in it). */
    fun elvesRiding(view: DeliveryView): Boolean {
        val t = view.ageMs
        val n = view.gifts
        return t >= departAt(n) && (view.kind == DeliveryKind.DELIVER || t < backAt(n))
    }
}
