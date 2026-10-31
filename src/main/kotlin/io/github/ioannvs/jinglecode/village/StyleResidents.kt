package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The mockup's `SCENES`, one list per style: its own scenes, then the common ones at its places.
 * Positions are mockup columns; [VillagePainter.m] takes them to the picture.
 */

/** `SCENES.v1`, the farmstead on the hill. */
internal val HILL_FARM_RESIDENTS: List<Resident> = listOf(
    Resident("sled", 150) { t, k ->
        for (i in 0 until 2) {
            val tt = t - i * 25
            if (tt < 0) continue
            val x: Int
            val sit: Boolean
            if (tt < 40) {
                val p = tt / 40.0
                x = jsRound(lerp(286, 196 - i * 12, p * p))
                sit = true
            } else {
                val w = walk(196 - i * 12, 286, tt - 46, 0.9)
                if (w.done) continue
                x = jsRound(w.x)
                sit = false
            }
            val gy = k.g(x + 1)
            val sx = m(x)
            val sled = if (i == 1) JinglePalette.WALL_BLUE else JinglePalette.SANTA_RED
            val hat = if (i == 1) JinglePalette.LAMP else JinglePalette.SANTA_RED
            if (sit) {
                sledAt(sx, gy, sled)
                person(sx, gy - 1, kid = true, sit = true, coat = COATS[i + 1], hat = hat, dir = -1)
                if (tt > 15) puff(sx + 6, gy - 1, tt)
            } else {
                person(sx, gy, kid = true, coat = COATS[i + 1], hat = hat, dir = 1, f = tt / 3, arm = Arm.FWD)
                for (q in 1 until 4) px(sx - q, gy - 2 + q / 2, JinglePalette.TRUNK)
                sledAt(sx - 9, k.g(x - 6), sled)
            }
        }
    },
    Resident("snowball", 180) { t, k ->
        val a = 158
        val b = 192
        val ga = k.g(a + 1)
        val gb = k.g(b + 1)
        val ax = m(a)
        val bx = m(b)
        rect(ax + 4, ga - 3, 5, 2, JinglePalette.WHITE)
        rect(ax + 5, ga - 4, 3, 1, JinglePalette.WHITE)
        rect(bx - 7, gb - 3, 5, 2, JinglePalette.WHITE)
        rect(bx - 6, gb - 4, 3, 1, JinglePalette.WHITE)
        val ph = t % 40
        val aThrows = ph < 20
        val q = ph % 20
        val duckB = aThrows && q in 13..17
        val duckA = !aThrows && q in 13..17
        person(ax, ga + (if (duckA) 2 else 0), kid = true, coat = COATS[0], hat = JinglePalette.WALL_BLUE, dir = 1, arm = if (aThrows && q < 4) Arm.UP else null)
        person(bx, gb + (if (duckB) 2 else 0), kid = true, coat = COATS[3], hat = JinglePalette.LAMP, dir = -1, arm = if (!aThrows && q < 4) Arm.UP else null)
        person(m(a - 6), k.g(a - 5), kid = true, coat = COATS[4], hat = JinglePalette.SANTA_RED, dir = 1, arm = if (t % 20 < 10) Arm.FWD else null)
        if (q in 3..14) {
            val p = (q - 3) / 12.0
            val x0 = if (aThrows) ax + 3 else bx - 1
            val x1 = if (aThrows) bx else ax + 2
            val ballX = lerp(x0, x1, p)
            val ballY = ga - 7 - sin(p * PI) * 8
            px(ballX, ballY, JinglePalette.WHITE)
            px(ballX, ballY + 1, JinglePalette.SNOW_SHADE)
        }
        if (q in 15..18) puff(if (aThrows) bx - 3 else ax + 6, (if (aThrows) gb else ga) - 4, q)
    },
    Resident("horse", 150) { t, k ->
        val hx = 120
        val gyh = k.g(hx + 3)
        val head = horse(m(hx), gyh, 0, -1)
        val eating = t in 96..139
        if (eating && t % 8 < 4) px(head, gyh - 6, JinglePalette.HORSE)
        val x: Double
        val d: Int
        var hay = false
        when {
            t < 45 -> {
                x = walk(66, 108, t, 1.0).x
                d = 1
            }
            t < 55 -> {
                x = 108.0
                d = -1
                hay = t > 50
            }
            else -> {
                val w = walk(108, 114, t - 55, 0.3)
                x = w.x
                d = 1
                hay = !w.done || t < 95
            }
        }
        val xr = jsRound(x)
        val h = person(m(xr), k.g(xr + 1), coat = COATS[5], hat = JinglePalette.SANTA_RED, dir = d, f = t / 3, arm = if (hay) Arm.UP else null)
        if (hay) rect(h.hx - 1, h.hy - 2, 3, 2, JinglePalette.GOLD)
        if (eating) rect(head - 2, gyh - 2, 3, 1, JinglePalette.GOLD)
    },
    Resident("church", 190) { t, k ->
        for (i in 0 until 3) {
            val w = walk(262 - i * 6, 376, t - i * 14, 0.75)
            if (t - i * 14 < 0 || w.done) continue
            val x = jsRound(w.x)
            val h = person(
                m(x), k.g(x + 1), coat = COATS[intArrayOf(2, 1, 6)[i]],
                hat = if (i == 1) JinglePalette.SANTA_RED else JinglePalette.HAT_WHITE,
                kid = i == 2, dir = 1, f = w.f, arm = if (i == 0) Arm.FWD else null,
            )
            if (i == 0) px(h.hx, h.hy + 1, if (evening) JinglePalette.LAMP else JinglePalette.STEEL)
        }
        if (t > 150) {
            val sw = jsRound(sin(t * 0.6) * 1)
            px(m(378) + sw, k.g(378) - 30, JinglePalette.GOLD)
            px(m(377) + sw, k.g(378) - 30, JinglePalette.GOLD)
        }
    },
    catScene(74) { it.g(76) - 5 },
    birdsScene(84) { it.g(88) - 6 },
    dogScene(228, 262),
    shovelScene(296, 308),
    carolScene(322, 352),
)

/** `SCENES.v2`, the valley. */
internal val VALLEY_RESIDENTS: List<Resident> = listOf(
    Resident("hockey", 160) { t, k ->
        val pcx = k.pondX
        val py = k.pondY
        val x = m(jsRound(pcx + sin(t * 0.08) * 16))
        val puck = m(jsRound(pcx + sin(t * 0.08 + 0.3) * 16) + 2)
        rect(x, py - 1, 1, 3, JinglePalette.FIR)
        px(x, py - 2, JinglePalette.FACE)
        px(x + 1, py + 1, JinglePalette.TRUNK)
        px(puck, py + 2, JinglePalette.COAL)
        val fx = m(pcx - 8)
        if (t % 60 in 36..51) {
            rect(fx - 1, py + 2, 3, 1, JinglePalette.GOLD)
            px(fx + 2, py + 2, JinglePalette.FACE)
        } else {
            rect(fx, py, 1, 3, JinglePalette.GOLD)
            px(fx, py - 1, JinglePalette.FACE)
        }
    },
    Resident("skifall", 140) { t, k ->
        val p1 = k.runs[1]
        val step = t / 2
        val stop = 26
        val j = when {
            t < stop * 2 -> min(p1.size - 1, step)
            t < (stop + 18) * 2 -> stop
            else -> min(p1.size - 1, step - 18)
        }
        val (x, y) = p1[j]
        if (step in stop until stop + 18) {
            rect(x - 1, y - 1, 3, 1, JinglePalette.BULBS[3])
            puff(x, y - 1, t)
        } else {
            px(x, y - 2, JinglePalette.BULBS[3])
            px(x, y - 1, JinglePalette.LIFT)
            px(x - 1, y, JinglePalette.LIFT)
            px(x + 1, y, JinglePalette.LIFT)
        }
        val p0 = k.runs[0]
        val i0 = step % p0.size
        val (bx, by) = p0[i0]
        val air = if (i0 in 19..25) jsRound(sin((i0 - 18) / 8.0 * PI) * 4) else 0
        px(bx, by - 3 - air, JinglePalette.CARROT)
        px(bx, by - 2 - air, JinglePalette.LIFT)
        rect(bx - 1, by - 1 - air, 3, 1, JinglePalette.SANTA_RED)
    },
    Resident("lanterns", 200) { t, k ->
        for (i in 0 until 3) {
            val n = k.road.size
            val idx = n - 1 - floor(t / 3.0 + i * 4).toInt() % n
            val (x, y) = k.road[max(0, idx)]
            px(x, y - 1, if (evening) JinglePalette.LAMP else JinglePalette.SANTA_RED)
            if (evening && t % 6 < 3) px(x, y - 2, JinglePalette.LAMP_GLOW)
        }
    },
    woodchopScene(118, -1),
    catScene(66) { it.g(66) },
    birdsScene(146) { it.g(150) - 1 },
    dogScene(132, 168),
    shovelScene(186, 204),
    carolScene(150, 94),
)

/** `SCENES.v3`, the street up the hill. */
internal val UPHILL_STREET_RESIDENTS: List<Resident> = listOf(
    Resident("iceslide", 120) { t, k ->
        for (x in 168 until 300 step 2) px(m(x), k.g(x) - 1, JinglePalette.POND_EDGE)
        for (i in 0 until 2) {
            val tt = t - i * 30
            if (tt !in 0..60) continue
            val p = tt / 60.0
            val x = jsRound(lerp(300, 168, p * p))
            val gy = k.g(x + 1)
            val sx = m(x)
            rect(sx - 1, gy - 1, 5, 1, if (i == 1) JinglePalette.WALL_BLUE else JinglePalette.SANTA_RED)
            person(sx, gy - 1, kid = true, sit = true, coat = COATS[i * 3 + 1], hat = if (i == 1) JinglePalette.LAMP else JinglePalette.HAT_WHITE, dir = -1)
            if (p > 0.4) puff(sx + 5, gy - 1, tt)
        }
    },
    Resident("sleigh", 230) { t, k ->
        val w = walk(150, 318, t, 0.75)
        val x = jsRound(w.x)
        val gy = k.g(x + 4)
        val sg = k.g(x - 9 + 3)
        val hx = m(x)
        val sx = m(x - 9)
        rect(sx, sg - 3, 6, 2, JinglePalette.WALL_RED)
        rect(sx - 1, sg - 1, 8, 1, JinglePalette.TRUNK)
        px(sx + 6, sg - 4, JinglePalette.WALL_RED)
        person(sx + 1, sg - 2, sit = true, coat = COATS[2], dir = 1, arm = Arm.FWD)
        for (q in sx + 6 until hx) {
            px(q, jsRound(lerp(sg - 4, gy - 5, (q - sx - 6).toDouble() / max(1, hx - sx - 6))), JinglePalette.HORSE_DARK)
        }
        horse(hx, gy, w.f, 1)
        if (t % 4 < 2) px(hx + 5, gy - 5, JinglePalette.GOLD)
    },
    Resident("well", 130) { t, k ->
        if (t < 30) {
            val gy = k.g(196)
            person(m(195), gy, coat = COATS[2], hat = JinglePalette.SANTA_RED, dir = -1, arm = Arm.FWD)
            px(m(189), jsRound(lerp(gy - 2, gy - 8, t / 30.0)), JinglePalette.STEEL)
            return@Resident
        }
        val w = walk(195, 228, t - 30, 0.4)
        if (w.done) return@Resident
        val x = jsRound(w.x)
        val gy = k.g(x + 1)
        val wx = m(x)
        person(wx, gy, coat = COATS[2], hat = JinglePalette.SANTA_RED, dir = 1, f = w.f)
        rect(wx - 3, gy - 5, 9, 1, JinglePalette.TRUNK)
        rect(wx - 3, gy - 4, 1, 2, JinglePalette.STEEL)
        rect(wx + 5, gy - 4, 1, 2, JinglePalette.STEEL)
    },
    Resident("lamplighter", 210) { t, k ->
        var wait = false
        val xd = when {
            t < 15 -> 165 + t * 0.8
            t < 35 -> {
                wait = true
                177.0
            }
            t < 160 -> min(285.0, 177 + (t - 35) * 0.87)
            else -> {
                wait = t < 185
                285.0
            }
        }
        val x = jsRound(xd)
        val gy = k.g(x + 1)
        val lx0 = m(x)
        val h = person(lx0, gy, coat = JinglePalette.CLOTH_DARK, hat = JinglePalette.CLOTH_DARK, dir = 1, f = if (wait) 0 else t / 2, arm = if (wait) Arm.UP else Arm.FWD)
        if (wait) {
            rect(h.hx, h.hy - 4, 1, 4, JinglePalette.TRUNK)
            px(h.hx, h.hy - 5, JinglePalette.CARROT)
            if (t % 4 < 2) {
                val lamp = if (x == 177) 178 else 286
                rect(m(lamp) - 2, k.g(lamp) - 13, 5, 3, JinglePalette.WINDOW_GLOW)
            }
        } else {
            for (q in 0 until 6) px(lx0 - 3 + q, gy - 2 - q, JinglePalette.LOG)
        }
    },
    Resident("khorovod", 180) { t, k ->
        val cx = 304
        val gy = k.g(cx)
        val n = 6
        class Dancer(val x: Int, val z: Double, val i: Int)
        val dancers = (0 until n).map { i ->
            val a = t * 0.07 + i * PI * 2 / n
            Dancer(jsRound(cx - 1 + cos(a) * 10), sin(a), i)
        }
        fun dance(p: Dancer) = person(
            m(p.x), gy + (if (p.z > 0) 1 else 0), kid = p.i % 2 == 1, coat = COATS[p.i % COATS.size],
            hat = if (p.i % 3 != 0) JinglePalette.HAT_WHITE else JinglePalette.SANTA_RED,
            dir = if (cos(t * 0.07 + p.i * PI * 2 / n) > 0) -1 else 1, f = t / 3 + p.i, arm = Arm.FWD,
        )
        dancers.filter { it.z <= 0 }.forEach(::dance)
        smallTree(m(cx), gy, 12, true)
        dancers.filter { it.z > 0 }.forEach(::dance)
    },
    catScene(96) { it.g(98) - 5 },
    birdsScene(104) { it.g(108) - 6 },
    dogScene(156, 176),
    shovelScene(122, 134),
    carolScene(258, 230),
)

/** `SCENES.v4`, the clearing in the forest. */
internal val FOREST_CLEARING_RESIDENTS: List<Resident> = listOf(
    Resident("banya", 95) { t, k ->
        val door = 355
        val gd = k.g(door)
        steam(m(door) + 1, gd - 6, t)
        if (t < 12) return@Resident
        var jumpY = 0
        var hidden = false
        var d = 1
        val xd = when {
            t < 26 -> lerp(door, 368, (t - 12) / 14.0)
            t < 34 -> {
                jumpY = jsRound(sin((t - 26) / 8.0 * PI) * 4)
                368 + (t - 26) * 0.4
            }
            t < 58 -> {
                hidden = true
                371.0
            }
            else -> {
                d = -1
                if (t > 78) return@Resident
                lerp(371, door, (t - 58) / 18.0)
            }
        }
        val x = jsRound(xd)
        val gy = k.g(x + 1)
        val bx = m(x)
        if (hidden) {
            puff(bx + 1, gy - 2, t)
            if (t > 46) px(bx + 1, gy - 3, JinglePalette.BATHER)
            return@Resident
        }
        person(bx, gy - jumpY, coat = JinglePalette.BATHER, nohat = true, dir = d, f = t / 2, arm = if (jumpY != 0) Arm.UP else null)
        rect(bx, gy - jumpY - 2, 3, 1, JinglePalette.WHITE)
    },
    woodchopScene(289, -1),
    Resident("hare", 100) { t, k ->
        var d = 1
        var up = 0
        var twitch = false
        val x = when {
            t < 30 -> {
                up = t / 3 % 2
                236 + t * 0.9
            }
            t < 70 -> {
                twitch = t % 10 < 3
                263.0
            }
            else -> {
                d = -1
                up = t / 3 % 2
                263 - (t - 70) * 1.1
            }
        }
        hare(m(x), k.g(jsRound(x) + 1), d, up, twitch)
    },
    Resident("fox", 180) { t, k ->
        val w = walk(232, 384, t, 0.85)
        val x = jsRound(w.x)
        for (q in 236 until x - 2 step 3) px(m(q), k.g(q) - 1 + q % 2, JinglePalette.SNOW_SHADE)
        if (!w.done) fox(m(x), k.g(x + 2), w.f, 1)
    },
    Resident("moose", 130) { t, k ->
        val x = when {
            t < 30 -> 384 - t * 0.5
            t < 100 -> 369.0
            else -> 369 + (t - 100) * 0.6
        }
        moose(m(x), k.g(jsRound(x) + 4), -1)
    },
    Resident("snowman", 170) { t, k ->
        val bx = jsRound(lerp(308, 318, t / 100.0))
        val gy = k.g(bx + 2)
        val r = jsRound(lerp(1, 3, t / 100.0))
        val sx = m(bx)
        rect(sx, gy - r * 2, r * 2, r * 2, JinglePalette.WHITE)
        rect(sx, gy - r * 2, r * 2, 1, JinglePalette.PUFF)
        if (t < 100) {
            person(sx - 4, gy, kid = true, coat = COATS[6], hat = JinglePalette.WALL_BLUE, dir = 1, f = t / 4, arm = Arm.FWD)
        } else {
            rect(sx + 1, gy - r * 2 - 4, 4, 4, JinglePalette.WHITE)
            px(sx + 3, gy - r * 2 - 3, JinglePalette.CARROT)
            px(sx + 2, gy - r * 2 - 3, JinglePalette.COAL)
            person(sx - 5, gy - (if (t % 10 < 5) 1 else 0), kid = true, coat = COATS[6], hat = JinglePalette.WALL_BLUE, dir = 1, arm = Arm.UP)
        }
        for (q in 300 until bx) px(m(q), gy - 1, JinglePalette.SNOW_SHADE)
    },
    Resident("tree", 140) { t, k ->
        val w = walk(378, 308, t, 0.6)
        if (w.done) return@Resident
        val x = jsRound(w.x)
        val gy = k.g(x + 1)
        val tx = m(x)
        for (i in 0 until 9) {
            val hw = i / 3
            rect(tx + 2 + i, gy - 6 - hw, 1, hw * 2 + 1, if (i % 2 == 1) JinglePalette.FIR_LIGHT else JinglePalette.FIR)
        }
        person(tx, gy, coat = COATS[0], hat = JinglePalette.ROOF_BROWN, dir = -1, f = w.f, arm = Arm.FWD)
    },
    catScene(312) { it.g(312) },
    birdsScene(250) { it.g(250) - 1 },
    dogScene(266, 286),
    shovelScene(338, 348),
    carolScene(372, 310),
)
