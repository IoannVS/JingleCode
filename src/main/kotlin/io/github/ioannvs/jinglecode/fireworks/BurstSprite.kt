package io.github.ioannvs.jinglecode.fireworks

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How long one burst lasts, ms, as in the mockup. */
internal const val BURST_MS = 1400L

/** The rocket flies up for this long before it bursts, ms. */
private const val RISE_MS = 300.0

/** The ring of the largest burst reaches its full size this long after the rocket bursts, ms (8 pixels by 70 ms in the mockup). */
private const val SPREAD_MS = 560.0

/** After this the sparks flicker out, ms. */
private const val FADE_MS = 1050L

/** The mockup's burst: up to 8 pixels across from the center, rising 10 rows. */
internal const val MOCKUP_RADIUS = 8
private const val MOCKUP_RISE = 10

/** The smallest ring worth showing. */
internal const val MIN_RADIUS = 3

/** How much the sparks droop at the edge of the ring, per radius squared, as in the mockup. */
private const val DROOP = 0.04

/** The willow's sparks droop more. */
private const val WILLOW_DROOP = 0.09

/** Rows from a ring's top to the bottom of its drooping lower half. */
internal fun burstHeight(radius: Int, kind: BurstKind = BurstKind.RING): Int =
    2 * radius + ceil(radius * radius * kind.droop).toInt() + 2

/**
 * How bursts fit into a sky of [rows] logical pixels: the largest ring, up to the mockup's; kinds that droop more get less.
 */
internal data class BurstFit(val rows: Int, val radius: Int) {
    companion object {
        /** `null` if even the smallest burst doesn't fit. */
        fun of(rows: Int): BurstFit? {
            val radius = (MOCKUP_RADIUS downTo MIN_RADIUS).firstOrNull { burstHeight(it) <= rows } ?: return null
            return BurstFit(rows, radius)
        }
    }
}

/** The kinds of bursts. [RING] is the mockup's. */
internal enum class BurstKind(val droop: Double) {
    /** Sparks in a ring (the mockup's `drawBurst`). */
    RING(DROOP),

    /** A ring and, if it is large, a smaller one inside it, in other colors. */
    DOUBLE_RING(DROOP),

    /** Golden sparks drooping low; large ones trail a spark behind each. */
    WILLOW(WILLOW_DROOP),

    /** Few sparks whose tips blink white at full size; large ones cross them out. */
    STAR(DROOP),

    /** Sparks scattered at different distances, a filled ball. */
    PEONY(DROOP),
}

/** Spark colors of the bursts; the first is the mockup's, which mixes in the burst's own bulb color. */
internal val BURST_COLORS: List<List<Color>> = listOf(
    emptyList(), // the mockup's: see sparkColor
    listOf(JinglePalette.BULBS[0], JinglePalette.FIRE[0]),
    listOf(JinglePalette.BULBS[2], JinglePalette.WHITE),
    listOf(JinglePalette.BULBS[3], JinglePalette.BULBS[0]),
    listOf(JinglePalette.FIRE[1], JinglePalette.FIRE[0], JinglePalette.GOLD_WAVE),
    listOf(JinglePalette.GIFT_PLUM, JinglePalette.BULBS[1], JinglePalette.FIRE[0]),
)

/** One burst of a show, where it goes and what it is. */
internal data class Burst(
    /** Center, root pane x in user pixels. */
    val x: Int,
    /** Center row, logical pixels from the top of the window. */
    val centerY: Int,
    val radius: Int,
    /** When its rocket starts, ms into the show. */
    val delayMs: Long,
    val kind: BurstKind,
    /** Index into [BURST_COLORS]. */
    val colors: Int,
    val seed: Int,
)

/** Bursts this big and bigger get their extras: an inner ring, tails, crossed tips. */
private const val LARGE = 6

/** Sparks on a ring of [radius]: 12 at the mockup's 8, 8 on middle rings, 6 on the smallest. */
private fun sparks(radius: Int): Int = when {
    radius >= LARGE -> 12
    radius >= 4 -> 8
    else -> 6
}

private fun sparkColor(colors: Int, k: Int, seed: Int): Color {
    if (colors == 0) return when (k % 3) {
        0 -> JinglePalette.BULBS[(k + seed) % 4]
        1 -> JinglePalette.FIRE[0]
        else -> JinglePalette.BULBS[1]
    }
    val scheme = BURST_COLORS[colors]
    return scheme[k % scheme.size]
}

/**
 * A burst [a] ms after its rocket started, with its center at ([cx], [burst.centerY]): the rocket flies
 * up for 300 ms from near the bottom of the sky ([rows] rows), then the sparks spread to [Burst.radius]
 * as [Burst.kind] says and flicker out by [BURST_MS]. A [BurstKind.RING] of radius 8 in the mockup's
 * colors is the mockup's `drawBurst` pixel for pixel.
 */
internal fun PixelCanvas.drawBurst(cx: Int, burst: Burst, a: Long, rows: Int) {
    if (a < 0 || a > BURST_MS) return
    val cy = burst.centerY
    if (a < RISE_MS) {
        val rise = min(MOCKUP_RISE, rows - 2 - cy).coerceAtLeast(1)
        val y = PixelCanvas.jsRound(cy + (1 - a / RISE_MS) * rise)
        px(cx, y, JinglePalette.FIRE[0])
        px(cx, y + 1, JinglePalette.FIRE[2])
        return
    }
    val full = burst.radius
    // Every size spreads as long as the mockup's 8 pixels do, so a small burst doesn't stop halfway and wait.
    val r = min(full.toDouble(), (a - RISE_MS) / SPREAD_MS * full)
    val tick = (a / 60).toInt()
    fun gone(k: Int) = a > FADE_MS && hash(k, tick) > 0.5

    fun spark(k: Int, angle: Double, radius: Double, color: Color = sparkColor(burst.colors, k, burst.seed)) {
        px(cx + PixelCanvas.jsRound(cos(angle) * radius), cy + PixelCanvas.jsRound(sin(angle) * radius + radius * radius * burst.kind.droop), color)
    }

    // Sparks about two pixels apart along the ring: the mockup's 12 at radius 8, fewer on smaller rings.
    val ring = sparks(full)
    when (burst.kind) {
        BurstKind.RING -> for (k in 0 until ring) if (!gone(k)) spark(k, k.toDouble() / ring * PI * 2, r)
        BurstKind.DOUBLE_RING -> {
            for (k in 0 until ring) if (!gone(k)) spark(k, k.toDouble() / ring * PI * 2, r)
            // The inner ring only where there is room between the two.
            if (full >= LARGE) for (k in 0 until 6) if (!gone(k + ring)) spark(k + 1, (k + 0.5) / 6 * PI * 2, r * 0.5)
        }
        BurstKind.WILLOW -> {
            val count = ring - 2
            for (k in 0 until count) {
                if (gone(k)) continue
                val angle = (k + 0.25) / count * PI * 2
                if (full >= LARGE && r > 3) spark(k, angle, r - 2, color = JinglePalette.FIRE[2])
                spark(k, angle, r, color = if (k % 3 == 0) JinglePalette.FIRE[0] else JinglePalette.FIRE[1])
            }
        }
        BurstKind.STAR -> {
            val count = if (full >= LARGE) 8 else 6
            for (k in 0 until count) {
                if (gone(k)) continue
                val angle = k.toDouble() / count * PI * 2
                // At full size the tips blink white; big stars cross them out with a pixel to each side.
                val tip = r >= full && a % 240 < 120
                spark(k, angle, r, color = if (tip) JinglePalette.WHITE else sparkColor(burst.colors, k, burst.seed))
                if (tip && full >= LARGE) {
                    val x = cx + PixelCanvas.jsRound(cos(angle) * r)
                    val y = cy + PixelCanvas.jsRound(sin(angle) * r + r * r * burst.kind.droop)
                    px(x - 1, y, JinglePalette.WHITE)
                    px(x + 1, y, JinglePalette.WHITE)
                }
            }
        }
        BurstKind.PEONY -> {
            val count = 2 * full + 2
            for (k in 0 until count) {
                if (gone(k)) continue
                spark(k, (k + hash(k, burst.seed + 41)) / count * PI * 2, r * (0.4 + 0.6 * hash(k, burst.seed + 43)))
            }
        }
    }
}
