package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * The mockup's `common` scenes, in every style at its own places (mockup columns), and `woodchop`.
 */

/** A cat on a fence or by a porch at column [x], its seat row from [seat]; eyes glow in the evening. */
internal fun catScene(x: Int, seat: (Spots) -> Int) = Resident("cat", 110) { t, k ->
    val by = seat(k)
    val jump = if (t < 6) 6 - t else if (t > 100) t - 100 else 0
    cat(m(x), by + jump, t, evening && t % 30 < 26)
    if (t in 41..59 && t % 6 < 3) px(m(x) + 2, by - 3, JinglePalette.CAT)
}

/** Three bullfinches fly in to column [x0], hop on their perch from [perch], fly off. */
internal fun birdsScene(x0: Int, perch: (Spots) -> Int) = Resident("birds", 120) { t, k ->
    val y = perch(k)
    val x = m(x0)
    intArrayOf(0, 3, 7).forEachIndexed { i, o ->
        when {
            t < 12 -> bird(x + o - (12 - t) * 2, y - (12 - t), t)
            t > 100 -> {
                val q = t - 100 + i * 2
                bird(x + o + q * 2, y - q * 1.5, t)
            }
            else -> bird(x + o, y - if ((t / 7 + i) % 5 == 0) 1 else 0, 0)
        }
    }
}

/** A dog runs between columns [x0] and [x1] and barks now and then. */
internal fun dogScene(x0: Int, x1: Int) = Resident("dog", 120) { t, k ->
    val span = x1 - x0
    val ph = (t * 1.2) % (span * 2)
    val fwd = ph < span
    val x = if (fwd) x0 + ph else x1 - (ph - span)
    val bark = t % 40 > 32
    val dx = m(x)
    dog(dx, k.g(x + 2), if (bark) 0 else t / 2, if (fwd) 1 else -1)
    if (bark && t % 4 < 2) {
        val hx = if (fwd) dx + 6 else dx - 3
        px(hx, k.g(x) - 6, JinglePalette.WHITE)
        px(hx + if (fwd) 1 else -1, k.g(x) - 7, JinglePalette.WHITE)
    }
}

/** Someone shovels snow from column [x0] to [x1], leaving a cleared strip. */
internal fun shovelScene(x0: Int, x1: Int) = Resident("shovel", 140) { t, k ->
    val w = walk(x0, x1, t, abs(x1 - x0) / 120.0)
    val x = jsRound(w.x)
    val gy = k.g(x + 1)
    for (xx in min(x0, x)..max(x0, x)) px(m(xx), k.g(xx) - 1, JinglePalette.SNOW_SHADE)
    val swing = t % 10 < 5
    val sx0 = m(x)
    person(sx0, gy, coat = COATS[5], hat = JinglePalette.WALL_BLUE, dir = w.d, f = t / 5, arm = Arm.FWD)
    val sx = sx0 + if (w.d > 0) 4 else -2
    px(sx, gy - if (swing) 3 else 1, JinglePalette.STEEL)
    px(sx + w.d, gy - if (swing) 4 else 1, JinglePalette.STEEL)
    if (!swing) {
        val q = t % 5
        px(sx0 - w.d * (2 + q), gy - 3 - q, JinglePalette.WHITE)
        px(sx0 - w.d * (3 + q), gy - 2 - q, JinglePalette.WHITE)
    }
}

/** Carol singers with a star on a pole walk from column [x0] to the door at [xDoor], sing in its light, leave. */
internal fun carolScene(x0: Int, xDoor: Int) = Resident("carol", 170) { t, k ->
    val d = if (xDoor > x0) 1 else -1
    val sp = abs(xDoor - x0) / 90.0
    val at = t in 91..129
    if (at) rect(m(xDoor) - 1, k.g(xDoor) - 7, 2, 6, JinglePalette.WINDOW_LIT)
    for (i in 0 until 3) {
        val w = if (t < 130) walk(x0 - d * i * 5, xDoor - d * (i * 5 + 3), t, sp)
        else walk(xDoor - d * (i * 5 + 3), xDoor - d * (i * 5 + 3) + d * 40, t - 130, 0.8)
        val x = jsRound(w.x)
        val gy = k.g(x + 1)
        val px0 = m(x)
        val hand = person(
            px0, gy, kid = true, coat = COATS[(i * 2 + 1) % COATS.size],
            hat = if (i == 0) JinglePalette.SANTA_RED else JinglePalette.HAT_WHITE,
            dir = d, f = if (w.done) 0 else w.f, arm = if (i == 0) Arm.UP else null,
        )
        if (i == 0) {
            rect(hand.hx, hand.hy - 3, 1, 3, JinglePalette.TRUNK)
            val star = JinglePalette.BULBS[t / 4 % 4]
            px(hand.hx, hand.hy - 5, star)
            px(hand.hx - 1, hand.hy - 4, star)
            px(hand.hx + 1, hand.hy - 4, star)
            px(hand.hx, hand.hy - 4, JinglePalette.GOLD)
        }
        if (i == 2 && evening) px(px0 + 3 * d + (if (d < 0) 2 else 0), gy - 3, JinglePalette.LAMP)
        if (at && t % 12 < 6) px(px0 + 1, gy - 9, JinglePalette.STAR)
    }
}

/** The mockup's `woodchop`: someone splits logs at column [x], the block on side [d]. */
internal fun woodchopScene(x: Int, d: Int) = Resident("chop", 130) { t, k ->
    val gy = k.g(x + 1)
    val ph = t % 12
    val up = ph < 6
    val cx = m(x)
    val sx = cx + if (d > 0) 4 else -3
    rect(sx, gy - 2, 2, 2, JinglePalette.LOG)
    if (ph in 6..8) px(sx, gy - 3, JinglePalette.LOG_CUT) else rect(sx, gy - 4, 2, 2, JinglePalette.LOG_CUT)
    val h = person(cx, gy, coat = COATS[0], hat = JinglePalette.ROOF_BROWN, dir = d, arm = if (up) Arm.UP else Arm.FWD)
    if (up) {
        px(h.hx, h.hy - 1, JinglePalette.TRUNK)
        rect(h.hx - d, h.hy - 2, 2, 1, JinglePalette.STEEL)
    } else {
        px(h.hx + d, h.hy + 1, JinglePalette.STEEL)
        px(h.hx + d, h.hy, JinglePalette.TRUNK)
    }
    if (ph in 6..9) {
        val q = ph - 6
        px(sx - q, gy - 4 - q, JinglePalette.LOG_CUT)
        px(sx + 2 + q, gy - 4 - q, JinglePalette.LOG_CUT)
    }
}
