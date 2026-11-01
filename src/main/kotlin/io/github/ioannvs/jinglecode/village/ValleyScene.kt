package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The mockup's `v2`, "Долина", in three planes. Far: mountains with snow caps and sky between the peaks,
 * a little village with lights, smoke and a spire on a slope, a sleigh with a lantern on the serpentine
 * road, and on the big mountain's right slope a ski resort: two runs, a chair lift with moving chairs,
 * skiers, a lodge with a flag, lamps along a run in the evening. Middle: a snowy ridge, forest in clumps,
 * a frozen pond with two skaters. Front: houses at the left, a bathhouse by the fir trees at the right.
 */
internal object ValleyScene : VillageScene {

    override val elastic = listOf(0..34, 118..174, 280..296)

    override val residents = VALLEY_RESIDENTS

    private val skiers = listOf(JinglePalette.SANTA_RED, JinglePalette.LAMP, JinglePalette.BULBS[2], JinglePalette.SKIER_PINK)
    private val chairs = listOf(JinglePalette.SANTA_RED, JinglePalette.WALL_BLUE, JinglePalette.GOLD, JinglePalette.FIR)

    private fun mountains(x: Double): Double =
        VILLAGE_ROWS - 16 - bump(x, 70, 60, 16) - bump(x, 165, 45, 9) - bump(x, 300, 85, 24) - bump(x, 420, 50, 12)

    private fun ridge(x: Double): Double = VILLAGE_ROWS - 12 - bump(x, 60, 70, 4) - bump(x, 420, 70, 5) + sin(x / 23) * 1.2

    override fun draw(p: VillagePainter) = with(p) {
        val h = VILLAGE_ROWS
        val r = MockupRandom(seed * 202 + 2)
        sky()
        // Far: mountains with snow caps, sky between the peaks
        val mt = along(::mountains)
        for (x in 0 until width) {
            val t = jsRound(mt(x))
            val slopeDown = mt(x + 1) > mt(x)
            val cap = max(1, jsRound((h - 16 - t) * 0.45))
            rect(x, t, 1, h - t, if (slopeDown) JinglePalette.MOUNTAIN_SHADE else JinglePalette.MOUNTAIN)
            rect(x, t, 1, cap, if (slopeDown) JinglePalette.MOUNTAIN_SNOW_SHADE else JinglePalette.MOUNTAIN_SNOW)
            if (hash(x, 9) > 0.7) px(x, t + cap, if (slopeDown) JinglePalette.MOUNTAIN_SNOW_SHADE else JinglePalette.MOUNTAIN_SNOW)
        }
        // A far village on the slope: little houses, lights, smoke, a spire
        val far = listOf(236 to 0, 243 to 1, 251 to 0, 258 to 2, 266 to 1, 229 to 2)
        far.forEachIndexed { i, (fx, k) ->
            val x = m(fx)
            val y = jsRound(mountains(fx + 2.0)) + 3 + k
            rect(x, y - 3, 5, 3, JinglePalette.FAR_HOUSE)
            rect(x, y - 4, 5, 1, JinglePalette.FAR_SNOW)
            px(x + 2, y - 5, JinglePalette.FAR_SNOW)
            if (evening && (i + seed) % 4 != 0) px(x + 1 + (i % 2) * 2, y - 2, JinglePalette.WINDOW_LIT)
            if (i % 2 == 0) {
                val f = (frame + i * 3) % 12
                px(x + 3 + (if (f > 6) 1 else 0), y - 7 - f / 3, JinglePalette.SMOKE)
            }
        }
        run {
            val x = m(274)
            val y = jsRound(mountains(274.0)) + 4
            rect(x, y - 6, 3, 6, JinglePalette.FAR_HOUSE)
            px(x + 1, y - 8, JinglePalette.GOLD)
            px(x + 1, y - 7, JinglePalette.GOLD)
            if (evening) px(x + 1, y - 4, JinglePalette.WINDOW_LIT)
        }
        // The serpentine road and the sleigh with a lantern
        val road = (0..60).map { i ->
            val tt = i / 60.0
            val x = jsRound(200 + 36 * tt + sin(tt * 9) * 9)
            m(x) to jsRound(mountains(x.toDouble())) + 5 + jsRound((1 - tt) * 10)
        }
        road.forEachIndexed { i, (x, y) -> if (i % 2 == 0) px(x, y, JinglePalette.ROAD) }
        run {
            val (sx, sy) = road[frame / 2 % road.size]
            px(sx, sy - 1, JinglePalette.SANTA_RED)
            px(sx + 1, sy - 1, JinglePalette.SANTA_RED)
            px(sx + 2, sy - 2, if (evening) JinglePalette.LAMP else JinglePalette.SANTA_RED)
        }
        // The ski resort on the big mountain's right slope: runs, the lift, skiers, the lodge
        val top = 304 to jsRound(mountains(304.0)) + 3
        val bottom = 370 to jsRound(ridge(370.0)) - 1
        fun piste(amp: Int, ph: Double, depth: Int): List<Pair<Int, Int>> = (0..50).map { i ->
            val t = i / 50.0
            val x = jsRound(top.first + (bottom.first - top.first) * t + sin(t * 8 + ph) * amp * (1 - t * 0.5))
            val y = jsRound(max(mountains(x.toDouble()) + 3, top.second + (bottom.second - top.second) * t + depth * sin(t * PI)))
            m(x) to y
        }
        val runs = listOf(piste(6, 0.0, 4), piste(9, 2.5, 9))
        runs.forEach { trail ->
            trail.forEach { (x, y) ->
                rect(x - 1, y, 3, 1, JinglePalette.PISTE)
                px(x, y, JinglePalette.WHITE)
            }
        }
        if (evening) runs[1].forEachIndexed { i, (x, y) -> if (i % 10 == 5) px(x + 3, y - 1, JinglePalette.LAMP) }
        fun lift(t: Double): Pair<Int, Int> = m(jsRound(bottom.first + 4 + (top.first + 6 - bottom.first - 4) * t)) to
            jsRound(bottom.second - 6 + (top.second - 4 - bottom.second + 6) * t)
        for (i in 0..80 step 2) {
            val (x, y) = lift(i / 80.0)
            px(x, y, JinglePalette.LIFT)
        }
        for (t in listOf(0.08, 0.38, 0.68, 0.96)) {
            val (x, y) = lift(t)
            rect(x, y, 1, 5, JinglePalette.LIFT)
            px(x - 1, y, JinglePalette.LIFT)
            px(x + 1, y, JinglePalette.LIFT)
        }
        for (k in 0 until 4) {
            val (x, y) = lift((frame / 70.0 + k / 4.0) % 1)
            px(x, y + 1, JinglePalette.LIFT)
            rect(x - 1, y + 2, 3, 1, chairs[k])
        }
        runs.forEachIndexed { j, trail ->
            for (k in 0 until 2) {
                val (x, y) = trail[(frame / (if (j == 1) 2 else 3) + k * 25 + j * 9) % trail.size]
                px(x, y - 2, skiers[j * 2 + k])
                px(x, y - 1, JinglePalette.LIFT)
                px(x - 1, y, JinglePalette.LIFT)
                px(x + 1, y, JinglePalette.LIFT)
            }
        }
        run {
            val bx = m(bottom.first + 2)
            val by = bottom.second + 1
            rect(bx, by - 5, 10, 5, Roofs.BROWN)
            rect(bx - 1, by - 6, 12, 1, JinglePalette.FAR_SNOW)
            rect(bx + 1, by - 7, 8, 1, JinglePalette.FAR_SNOW)
            for (wx in listOf(2, 5, 8)) px(bx + wx, by - 3, if (evening) JinglePalette.WINDOW_LIT else JinglePalette.WINDOW_DARK)
            rect(bx + 12, by - 10, 1, 10, JinglePalette.LIFT)
            rect(bx + 13, by - 10, 3, 2, if (frame % 6 < 3) JinglePalette.SANTA_RED else JinglePalette.FLAG_LIGHT)
        }
        // Middle: a snowy ridge, forest in clumps, a frozen pond with skaters
        val mid = along(::ridge)
        for (x in 0 until width) {
            val t = jsRound(mid(x))
            rect(x, t, 1, h - t, JinglePalette.SNOW_FAR)
            px(x, t, JinglePalette.WHITE)
        }
        for ((x0, x1) in listOf(10 to 60, 120 to 165, 330 to 350, 395 to 460)) {
            var x = m(x0)
            val end = m(x1)
            while (x < end) {
                pine(x, mid(x) + 1, 7 + floor(r() * 6).toInt(), r, far = true)
                x += 4 + floor(r() * 4).toInt()
            }
        }
        val pcx = 255
        val pw = 46
        val py = jsRound(ridge(pcx.toDouble())) + 3
        for (row in 0 until 4) {
            val hw = jsRound(pw / 2.0 * sqrt(1 - ((row - 1.5) / 2).pow(2)))
            rect(m(pcx) - hw, py + row, hw * 2, 1, if (row == 0) JinglePalette.POND_EDGE else JinglePalette.ROOF_ICE)
        }
        for (k in 0 until 2) {
            val ph = (frame * (if (k == 1) 0.7 else 1.0) + k * 20) % 60
            val sx = m(jsRound(pcx - 16 + abs(30 - ph)))
            val sy = py + 1 + k
            val coat = if (k == 1) JinglePalette.WALL_BLUE else JinglePalette.WALL_RED
            rect(sx, sy - 3, 1, 3, coat)
            px(sx, sy - 4, JinglePalette.FACE)
            px(sx + (if (frame % 4 < 2) 1 else -1), sy - 2, coat)
        }
        // Front: a group at the left, a bathhouse by the fir trees at the right
        val hf = ground { x -> h - 5 - bump(x, 95, 70, 4) - bump(x, 440, 40, 3) + sin(x / 11) * 0.6 }
        fun g(x: Int) = jsRound(hf(m(x))) + 1
        fun base(x: Int) = hf(m(x)) + 1
        drawHouse(m(70), g(78), HouseSpec.random(HouseType.COTTAGE, r))
        drawHouse(m(84), g(94), HouseSpec.random(HouseType.IZBA, r))
        woodpile(m(106), g(110))
        pine(m(52), base(52), 20, r)
        pine(m(44), base(44), 13, r)
        lampPost(m(180), g(180))
        drawHouse(m(404), g(412), HouseSpec.random(HouseType.BANYA, r))
        pine(m(430), base(430), 24, r)
        pine(m(446), base(446), 15, r)
        ground(hf)
        sled(m(196), g(199))
        Spots(hf, pondX = pcx, pondY = py, runs = runs, road = road)
    }
}
