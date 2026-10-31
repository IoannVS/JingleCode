package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import java.awt.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/*
 * The village mockup's residents (block "жители и сценки": `person`, `walk`, `puff`, `steam`, `dog`,
 * `cat`, `bird`, `hare`, `fox`, `moose`, `horse`, `sledAt`, `smallTree`), pixel for pixel. Coordinates are
 * the picture's: the scenes map their mockup positions with [VillagePainter.m] before calling these.
 */

/** The mockup's `COATS`. */
internal val COATS: List<Color> = listOf(
    JinglePalette.SANTA_RED, JinglePalette.WALL_BLUE, JinglePalette.GIFT_PLUM, JinglePalette.FIR,
    JinglePalette.COAT_OCHRE, JinglePalette.ROOF_BROWN, JinglePalette.COAT_ORANGE,
)

/** The mockup's `L`: from [a] to [b] as [p] goes from 0 to 1, clamped. */
internal fun lerp(a: Number, b: Number, p: Double): Double = a.toDouble() + (b.toDouble() - a.toDouble()) * p.coerceIn(0.0, 1.0)

/** Where a walker is: the mockup's `walk` result. */
internal class Walk(val x: Double, val d: Int, val done: Boolean, val f: Int)

/** The mockup's `walk`: from [x0] towards [x1] at [speed] pixels a frame, [t] frames after setting off. */
internal fun walk(x0: Number, x1: Number, t: Number, speed: Double): Walk {
    val a = x0.toDouble()
    val b = x1.toDouble()
    val dist = abs(b - a)
    val p = min(dist, t.toDouble() * speed)
    return Walk(a + sign(b - a) * p, if (b >= a) 1 else -1, p >= dist, floor(t.toDouble() / 2).toInt())
}

/** How a person holds an arm: raised or forward. */
internal enum class Arm { UP, FWD }

/** Where a person's hand is, to put something in it. */
internal class Hand(val hx: Int, val hy: Int)

/**
 * The mockup's `person`: 3 pixels wide, feet on row `gy - 1`; a [kid] is a row shorter. [f] is the
 * step frame, [dir] the way they face.
 */
internal fun VillagePainter.person(
    x0: Number,
    gy0: Number,
    coat: Color = COATS[0],
    hat: Color = JinglePalette.HAT_WHITE,
    kid: Boolean = false,
    f: Int = 0,
    dir: Int = 1,
    arm: Arm? = null,
    sit: Boolean = false,
    nohat: Boolean = false,
): Hand {
    val x = jsRound(x0.toDouble())
    var gy = jsRound(gy0.toDouble())
    if (sit) gy += 1
    val bh = if (kid) 2 else 3
    if (sit) {
        px(x + if (dir > 0) 3 else -1, gy - 2, JinglePalette.CLOTH_DARK)
    } else if (f % 2 != 0) {
        px(x, gy - 1, JinglePalette.CLOTH_DARK)
        px(x + 2, gy - 1, JinglePalette.CLOTH_DARK)
    } else {
        px(x + 1, gy - 1, JinglePalette.CLOTH_DARK)
    }
    rect(x, gy - 1 - bh, 3, bh, coat)
    px(x + 1, gy - 2 - bh, JinglePalette.FACE)
    if (nohat) {
        px(x + 1, gy - 3 - bh, JinglePalette.TRUNK)
    } else {
        rect(x, gy - 3 - bh, 3, 1, hat)
        if (kid) px(x + 1, gy - 4 - bh, hat)
    }
    val hx = x + if (dir > 0) 3 else -1
    return when (arm) {
        Arm.UP -> {
            px(hx, gy - 1 - bh, coat)
            px(hx, gy - 2 - bh, coat)
            Hand(hx, gy - 3 - bh)
        }
        Arm.FWD -> {
            px(hx, gy - bh, coat)
            Hand(hx, gy - bh)
        }
        null -> Hand(hx, gy - bh)
    }
}

/** The mockup's `puff`: a little burst of snow. */
internal fun VillagePainter.puff(x: Int, y: Int, t: Int) {
    for (i in 0 until 6) {
        val a = i * 1.05 + t * 0.3
        val r = 1 + (t % 6) * 0.5
        px(x + jsRound(cos(a) * r), y - jsRound(abs(sin(a)) * r), if (i % 2 == 1) JinglePalette.WHITE else JinglePalette.PUFF)
    }
}

/** The mockup's `steam`: wisps rising from a door. */
internal fun VillagePainter.steam(x: Int, y: Int, t: Int) {
    for (i in 0 until 5) {
        val tt = (t * 0.5 + i * 2) % 10
        px(x + jsRound(sin(tt + i) * 1.5), y - jsRound(tt), JinglePalette.STEAM)
    }
}

/** The mockup's `dog`, running way [d]. */
internal fun VillagePainter.dog(x0: Number, gy: Int, f: Int, d: Int) {
    val x = jsRound(x0.toDouble())
    rect(x, gy - 3, 4, 2, JinglePalette.DOG)
    if (f % 2 != 0) {
        px(x, gy - 1, JinglePalette.DOG_DARK)
        px(x + 3, gy - 1, JinglePalette.DOG_DARK)
    } else {
        px(x + 1, gy - 1, JinglePalette.DOG_DARK)
        px(x + 2, gy - 1, JinglePalette.DOG_DARK)
    }
    val hx = if (d > 0) x + 4 else x - 1
    val tx = if (d > 0) x - 1 else x + 4
    rect(hx, gy - 4, 1, 2, JinglePalette.DOG)
    px(hx + d, gy - 3, JinglePalette.DOG_DARK)
    px(hx, gy - 5, JinglePalette.DOG_DARK)
    px(tx, if (f % 2 != 0) gy - 4 else gy - 3, JinglePalette.DOG)
}

/** The mockup's `cat`, sitting on row [baseY], its tail swishing; [eyes] glow in the evening. */
internal fun VillagePainter.cat(x: Int, baseY: Int, f: Int, eyes: Boolean) {
    val col = JinglePalette.CAT
    rect(x, baseY - 3, 2, 3, col)
    px(x, baseY - 4, col)
    px(x + 1, baseY - 4, col)
    if (eyes) {
        px(x, baseY - 3, JinglePalette.LAMP)
        px(x + 1, baseY - 3, JinglePalette.LAMP)
    }
    px(x + 2, baseY - 1, col)
    px(x + 3, if (f % 4 < 2) baseY - 2 else baseY - 1, col)
}

/** The mockup's `bird`, a bullfinch; [fly] other than 0 flaps its wings. */
internal fun VillagePainter.bird(x: Number, y: Number, fly: Int) {
    val bx = x.toDouble()
    val by = y.toDouble()
    px(bx, by, JinglePalette.BULLFINCH)
    px(bx + 1, by - 1, JinglePalette.CLOTH_DARK)
    if (fly != 0) {
        px(bx - 1, by - (if (fly % 2 != 0) 1 else 0), JinglePalette.ROOF_SLATE)
        px(bx + 1, by - (if (fly % 2 != 0) 0 else 2), JinglePalette.ROOF_SLATE)
    } else {
        px(bx - 1, by, JinglePalette.ROOF_SLATE)
    }
}

/** The mockup's `hare`: [up] lifts it in a hop, [twitch] moves an ear. */
internal fun VillagePainter.hare(x0: Number, gy0: Int, d: Int, up: Int, twitch: Boolean) {
    val x = jsRound(x0.toDouble())
    val gy = gy0 - up
    val col = JinglePalette.HARE
    rect(x, gy - 3, 3, 2, col)
    px(x + if (d > 0) 0 else 2, gy - 1, col)
    px(x + if (d > 0) 2 else 0, gy - 1, col)
    val hx = if (d > 0) x + 3 else x - 1
    px(hx, gy - 3, col)
    px(hx, gy - 4, col)
    px(hx, gy - 5 - (if (twitch) 1 else 0), JinglePalette.HARE_DARK)
    px(hx, gy - 3, JinglePalette.HARE_DARK)
    px(if (d > 0) x - 1 else x + 3, gy - 3, JinglePalette.WHITE)
}

/** The mockup's `fox`, its tail tip white. */
internal fun VillagePainter.fox(x0: Number, gy: Int, f: Int, d: Int) {
    val x = jsRound(x0.toDouble())
    val col = JinglePalette.FOX
    val dk = JinglePalette.FOX_DARK
    rect(x, gy - 3, 5, 2, col)
    if (f % 2 != 0) {
        px(x, gy - 1, dk)
        px(x + 4, gy - 1, dk)
    } else {
        px(x + 1, gy - 1, dk)
        px(x + 3, gy - 1, dk)
    }
    val hx = if (d > 0) x + 5 else x - 1
    rect(hx, gy - 4, 1, 2, col)
    px(hx + d, gy - 3, dk)
    px(hx, gy - 5, dk)
    val tx = if (d > 0) x - 3 else x + 5
    val ty = gy - 3 + if (f % 4 < 2) 0 else 1
    rect(tx, ty, 3, 1, col)
    px(if (d > 0) tx else tx + 2, ty, JinglePalette.WHITE)
}

/** The mockup's `moose`, antlers and all. */
internal fun VillagePainter.moose(x0: Number, gy: Int, d: Int) {
    val x = jsRound(x0.toDouble())
    rect(x, gy - 7, 8, 3, JinglePalette.MOOSE)
    for (o in intArrayOf(0, 2, 5, 7)) rect(x + o, gy - 4, 1, 4, JinglePalette.MOOSE_LEG)
    val hx = if (d > 0) x + 8 else x - 2
    rect(hx, gy - 9, 2, 3, JinglePalette.MOOSE)
    px(hx + if (d > 0) 2 else -1, gy - 7, JinglePalette.MOOSE)
    rect(hx - 1, gy - 10, 4, 1, JinglePalette.MOOSE_ANTLER)
    px(hx - 1, gy - 11, JinglePalette.MOOSE_ANTLER)
    px(hx + 2, gy - 11, JinglePalette.MOOSE_ANTLER)
}

/** The mockup's `horse`; returns the column of its head. */
internal fun VillagePainter.horse(x0: Number, gy: Int, f: Int, d: Int): Int {
    val x = jsRound(x0.toDouble())
    rect(x, gy - 6, 7, 3, JinglePalette.HORSE)
    intArrayOf(0, 1, 5, 6).forEachIndexed { i, o -> rect(x + o, gy - 3, 1, if ((f + i) % 2 != 0) 3 else 2, JinglePalette.HORSE_DARK) }
    val nx = if (d > 0) x + 6 else x
    rect(nx, gy - 8, 1, 2, JinglePalette.HORSE)
    val hx = if (d > 0) nx + 1 else nx - 2
    rect(hx, gy - 8, 2, 2, JinglePalette.HORSE)
    px(if (d > 0) nx - 1 else nx + 1, gy - 8, JinglePalette.HORSE_DARK)
    px(if (d > 0) x - 1 else x + 7, gy - 5, JinglePalette.HORSE_DARK)
    px(if (d > 0) x - 1 else x + 7, gy - 4, JinglePalette.HORSE_DARK)
    return hx
}

/** The mockup's `sledAt`: a small sled of [color]. */
internal fun VillagePainter.sledAt(x: Int, gy: Int, color: Color = JinglePalette.SANTA_RED) {
    rect(x, gy - 2, 5, 1, color)
    rect(x - 1, gy - 1, 7, 1, JinglePalette.TRUNK)
}

/** The mockup's `smallTree`, [h] high, with running [lights]. */
internal fun VillagePainter.smallTree(x: Int, gy: Int, h: Int, lights: Boolean) {
    for (i in 0 until h) {
        val hw = floor(i / 2.5).toInt()
        rect(x - hw, gy - h - 1 + i, hw * 2 + 1, 1, if (i % 3 == 2) JinglePalette.FIR_LIGHT else JinglePalette.FIR)
        if (lights && i % 3 == 1 && i > 1) {
            px(x - hw + (i + frame / 3) % (hw * 2 + 1), gy - h - 1 + i, JinglePalette.BULBS[(i + frame / 4) % 4])
        }
    }
    px(x, gy - h - 2, JinglePalette.GOLD)
    px(x, gy - 1, JinglePalette.TRUNK)
}
