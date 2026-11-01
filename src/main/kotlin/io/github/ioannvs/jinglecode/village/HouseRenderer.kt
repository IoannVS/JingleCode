package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The mockup's house renderer (`drawHouse` and what it calls), pixel for pixel. Its globals `frame` and
 * `evening` come from the painter. One addition: a house registers its box for clicks, and a click
 * (see [VillagePainter.isStoked]) makes its chimney smoke thick.
 */

/** Where a roof ends up: its top row and its eave row. */
private class Roof(val peakY: Int, val eaveY: Int)

/** The mockup's `drawHouse`: [s] standing on row [gy] (its bottom row is `gy - 1`), walls from column [x]. */
internal fun VillagePainter.drawHouse(x: Int, gy: Int, s: HouseSpec) {
    val index = houses.size
    if (s.type == HouseType.MILL) {
        drawMill(x, gy, s)
        houses += HouseBox(index, x - 1, gy - s.h - 12, x + s.w, gy, hasChimney = false)
        return
    }
    val wc = s.wall.light
    val wd = s.wall.dark
    val shed = s.type == HouseType.BANYA
    val aframe = s.type == HouseType.AFRAME
    val top = gy - s.h
    // Walls
    if (!aframe) {
        rect(x, top, s.w, s.h, wc)
        when (s.wall) {
            Wall.LOGS -> {
                for (y in top + 1 until gy step 2) rect(x, y, s.w, 1, wd)
                for (y in top until gy step 2) {
                    px(x - 1, y, wd)
                    px(x + s.w, y, wd)
                }
            }
            Wall.PLANKS, Wall.RED -> for (xx in x + 2 until x + s.w step 3) rect(xx, top, 1, s.h, wd)
            Wall.STONE -> for (y in top + 2 until gy step 3) rect(x, y, s.w, 1, wd)
            else -> {
                rect(x, top, 1, s.h, wd)
                rect(x + s.w - 1, top, 1, s.h, wd)
            }
        }
    }
    // Roof
    val roof = if (shed) drawRoofShed(x, top - 1, s.w, s.roof, s) else drawRoofGable(x, top - 1, s.w, if (aframe) 2.6 else s.pitch, s.roof, s)
    if (s.carved && !shed) for (xx in x - 1 until x + s.w + 1 step 2) px(xx, top, JinglePalette.BIRCH)
    if (s.storeys == 2) rect(x - 1, top + s.h / 2, s.w + 2, 1, wd)
    // Chimney
    val stoked = isStoked(index)
    if (s.chimney) {
        val cx = if (s.wind < 0) x + s.w - 6 else x + 3
        val ch = if (shed) 6 else 4
        val cy = if (shed) roof.peakY - 1 else roof.peakY + jsRound((roof.eaveY - roof.peakY) * 0.35)
        rect(cx, cy - ch, 3, ch + 2, JinglePalette.STONE)
        rect(cx, cy - ch, 3, 1, JinglePalette.SNOW)
        if (s.lit > 0.25 || s.smokeHeavy || stoked) {
            val n = if (s.smokeHeavy || stoked) 7 else 4
            for (i in 0 until n) {
                val t = (frame * 0.3 + i * 2.2) % (n * 2.2)
                if (t < 1) continue
                px(cx + 1 + jsRound(sin(t * 0.9 + x) * 1.2) - jsRound(t * 0.4) * s.wind * -1, cy - ch - 1 - jsRound(t * 1.4), JinglePalette.SMOKE)
            }
        }
    }
    // Windows and doors
    val litBase = if (evening) 0.15 else 0.7
    when (s.type) {
        HouseType.CHURCH -> {
            drawWindow(x + 4, top + 4, s.lit > litBase, false)
            rect(x + 4, gy - 6, 4, 6, JinglePalette.DOOR)
            drawDome(x + s.w / 2, roof.peakY, s)
        }
        HouseType.BARN -> {
            val dx = x + s.w / 2 - 4
            rect(dx, gy - 8, 8, 8, JinglePalette.BARN_GATE)
            for (i in 0 until 8) {
                px(dx + i, gy - 8 + i, JinglePalette.PALE_WOOD)
                px(dx + 7 - i, gy - 8 + i, JinglePalette.PALE_WOOD)
            }
            rect(dx - 1, gy - 9, 10, 1, JinglePalette.PALE_WOOD)
        }
        HouseType.AFRAME -> {
            drawWindow(x + 4, gy - 9, s.lit > litBase, false)
            rect(x + 4, gy - 4, 4, 4, JinglePalette.DOOR)
        }
        else -> {
            val sh = s.h.toDouble() / s.storeys
            for (st in 0 until s.storeys) {
                val wy = top + jsRound(st * sh) + 3
                val slots = if (s.wins == 1) listOf(x + s.w / 2 - 2) else listOf(x + 3, x + s.w - 7)
                slots.forEachIndexed { i, wx ->
                    if (st == s.storeys - 1 && s.wins > 1 && i == 1 && s.w < 18) return@forEachIndexed
                    drawWindow(wx, wy, hash((x + i).toDouble(), st + s.lit * 10) < (if (evening) 0.85 else 0.3), s.carved)
                }
            }
            val dx = if (s.wins > 1 && s.w < 18) x + s.w - 7 else x + s.w / 2 - 2
            rect(dx, gy - 6, 4, 6, JinglePalette.DOOR)
            px(dx + 3, gy - 3, JinglePalette.GOLD)
            if (s.deco == Deco.WREATH) {
                rect(dx + 1, gy - 5, 2, 2, JinglePalette.FIR)
                px(dx + 1, gy - 4, JinglePalette.SANTA_RED)
            }
        }
    }
    // Lights under the eave
    if (s.deco == Deco.GARLAND && s.type != HouseType.CHURCH && s.type != HouseType.BARN) {
        var xx = x - 1
        var i = 0
        while (xx < x + s.w + 1) {
            px(xx, top, JinglePalette.FIR)
            if (i % 2 == 0) px(xx, top + 1, if (evening) JinglePalette.BULBS[(i / 2 + frame / 3) % 4] else JinglePalette.BULB_OFF)
            xx += 2
            i++
        }
    }
    // Icicles
    if (s.icicles && !aframe) {
        val rr = MockupRandom(x * 31 + s.w)
        for (xx in x - 1 until x + s.w + 1) {
            if (rr() < 0.55) continue
            val len = 1 + floor(rr() * 3).toInt()
            rect(xx, top + if (s.deco == Deco.GARLAND) 2 else 0, 1, len, JinglePalette.ROOF_ICE)
        }
    }
    // A drift against the windward wall
    if (s.drift) {
        val wx = if (s.wind < 0) x - 1 else x + s.w
        for (i in 0 until 4) rect(if (s.wind < 0) wx - i else wx + i - 2, gy - (3 - i), 3, 3 - i + 1, JinglePalette.SNOW)
    }
    houses += HouseBox(index, x - 2, min(roof.peakY, top) - 6, x + s.w + 1, gy, hasChimney = s.chimney)
}

/** The mockup's `drawWindow`: a 4×4 window in a frame, lit with a glint or dark, with carved white frames if [carved]. */
private fun VillagePainter.drawWindow(x: Int, y: Int, lit: Boolean, carved: Boolean) {
    rect(x - 1, y - 1, 6, 6, JinglePalette.WINDOW_FRAME)
    rect(x, y, 4, 4, if (lit) JinglePalette.WINDOW_LIT else JinglePalette.WINDOW_DARK)
    if (lit) px(x, y, JinglePalette.WINDOW_GLOW)
    rect(x + 2, y, 1, 4, JinglePalette.WINDOW_FRAME)
    rect(x, y + 2, 4, 1, JinglePalette.WINDOW_FRAME)
    if (carved) {
        rect(x - 1, y - 2, 6, 1, JinglePalette.BIRCH)
        px(x + 1, y - 3, JinglePalette.BIRCH)
        px(x + 2, y - 3, JinglePalette.BIRCH)
        rect(x - 1, y + 5, 6, 1, JinglePalette.BIRCH)
    }
}

/** The mockup's `drawRoofGable`: overhang of two, as high as the [pitch] makes it, snow by the wind and the pitch. */
private fun VillagePainter.drawRoofGable(x: Int, top: Int, w: Int, pitch: Double, color: Color, s: HouseSpec): Roof {
    val width = w + 4
    val rows = max(3, jsRound((width / 2.0) * pitch / 1.6))
    val edges = ArrayList<IntArray>()
    for (i in 0 until rows) {
        val inset = jsRound((i + 1) * (width / 2.0) / (rows + 0.5))
        val step = jsRound(width / 2.0 / rows)
        val l = x - 2 + inset - step
        val rw = width - 2 * (inset - step)
        rect(l, top - i, rw, 1, color)
        edges += intArrayOf(l, l + rw - 1, top - i)
    }
    // Snow: more on the windward side; it slides off a steep roof.
    val slide = if (pitch > 1.2) 0.5 else 1.0
    edges.forEachIndexed { i, (l, r, y) ->
        val big = max(1, jsRound(s.snow * slide * (if (i % 3 == 0) 1.0 else 0.8)))
        val small = max(0, jsRound(s.snow * slide * 0.4))
        val left = if (s.wind < 0) big else small
        val right = if (s.wind < 0) small else big
        rect(l, y - 1, min(left, r - l + 1), 1, JinglePalette.SNOW)
        rect(r - min(right, r - l + 1) + 1, y - 1, min(right, r - l + 1), 1, JinglePalette.SNOW)
    }
    val (tl, tr, ty) = edges.last()
    rect(tl, ty - 1, tr - tl + 1, 1, JinglePalette.SNOW)
    if (s.snow >= 3 && pitch <= 1.2) rect(tl + 1, ty - 2, max(1, tr - tl - 1), 1, JinglePalette.WHITE)
    // A cornice of snow on the windward side.
    val eave = edges.first()
    if (s.snow >= 2) px(if (s.wind < 0) eave[0] - 1 else eave[1] + 1, eave[2], JinglePalette.SNOW)
    return Roof(ty - 1, top)
}

/** The mockup's `drawRoofShed`: a single slope rising against the wind, snow along it. */
private fun VillagePainter.drawRoofShed(x: Int, top: Int, w: Int, color: Color, s: HouseSpec): Roof {
    val width = w + 4
    for (i in 0 until width) {
        val height = 2 + jsRound(i.toDouble() / width * 4)
        val cx = if (s.wind < 0) x - 2 + i else x + w + 1 - i
        rect(cx, top - height + 1, 1, height, color)
        val snow = max(1, jsRound(s.snow * 0.8))
        rect(cx, top - height + 1 - snow, 1, snow, JinglePalette.SNOW)
    }
    return Roof(top - 6, top)
}

/** The mockup's `drawDome`: a drum with a window, an onion dome gold on top, a cross. */
private fun VillagePainter.drawDome(cx: Int, peakY: Int, s: HouseSpec) {
    val tx = cx - 3
    val ty = peakY - 8 + 2
    rect(tx, ty, 6, 8, if (s.wall == Wall.STONE) JinglePalette.STONE else JinglePalette.WALL_YELLOW)
    drawWindow(tx + 1, ty + 2, evening, false)
    val rows = intArrayOf(2, 4, 6, 6, 4)
    rows.forEachIndexed { i, w -> rect(cx - w / 2, ty - rows.size + i, w, 1, if (i < 2) JinglePalette.GOLD else JinglePalette.WALL_BLUE) }
    px(cx - 1, ty - 3, JinglePalette.LAMP)
    rect(cx - 1, ty - rows.size - 3, 1, 3, JinglePalette.GOLD)
    rect(cx - 2, ty - rows.size - 2, 3, 1, JinglePalette.GOLD)
    rect(cx - 1, ty - rows.size, 2, 1, JinglePalette.SNOW)
}

/** The mockup's `drawMill`: a narrowing plank tower, a roof under snow, sails turning with the frames. */
private fun VillagePainter.drawMill(x: Int, gy: Int, s: HouseSpec) {
    val h = s.h
    for (i in 0 until h) {
        val inset = i / 5
        rect(x + inset, gy - 1 - i, s.w - inset * 2, 1, if (i % 2 == 1) JinglePalette.PLANKS else JinglePalette.PLANKS_DARK)
    }
    val topY = gy - h
    val cx = x + s.w / 2
    for (r in 0 until 4) rect(x + 1 + r, topY - r, s.w - 2 - r * 2, 1, Roofs.BROWN)
    rect(x + 2, topY - 4, s.w - 4, 1, JinglePalette.SNOW)
    drawWindow(cx - 2, gy - 11, evening, false)
    rect(cx - 2, gy - 5, 4, 5, JinglePalette.DOOR)
    val hy = topY + 2
    val angle = frame * 0.03
    for (b in 0 until 4) {
        val a = angle + b * PI / 2
        for (t in 1 until 11) px(cx + jsRound(cos(a) * t), hy + jsRound(sin(a) * t), if (t > 4) JinglePalette.PALE_WOOD else JinglePalette.TRUNK)
    }
    px(cx, hy, JinglePalette.GOLD)
}
