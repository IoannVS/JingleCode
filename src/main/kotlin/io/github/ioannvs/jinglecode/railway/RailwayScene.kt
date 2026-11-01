package io.github.ioannvs.jinglecode.railway

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** What the scene shows besides the track, from the settings. */
internal class RailwayOptions(val frequency: TrainFrequency, val residents: Boolean, val poles: Boolean)

/**
 * The railway mockup's `strip()` for one tool window: snow, rails and the path behind them, poles with
 * bullfinches on the wires, the halt with its lamp, the level crossing, the semaphore, the trains and the walkers.
 * Coordinates are logical pixels of a strip `width` wide and [STRIP_HEIGHT] high, with `win` at (0, 0). One logical
 * pixel is a sprite pixel, the pixel of the yard and the snowman's drift, so the mockup's coordinates and speeds
 * are halved (Ivan: one pixel and one style along the line). The snow is the yard's and the village's: heaps,
 * a white crest, shade on the steps down, speckles.
 *
 * The mockup moves things while drawing; here [step] moves them by the seconds passed (so the speed doesn't
 * depend on the frame rate) and [paint] only draws. The mockup's `Math.random()` is a seeded [random].
 */
internal class RailwayScene(seed: Int) {

    private class Train(val cars: List<RailSprite>, val dir: Int, val len: Int, var x: Double, val v: Double)
    private class Walker(val sprite: RailSprite, val v: Double, var x: Double)
    private class Puff(var x: Double, var y: Double, var life: Double, val spark: Boolean = false)
    private class Spray(var x: Double, var y: Double, val vx: Double, var vy: Double, var life: Double)
    /**
     * A bullfinch on a wire. When a train comes it flies off after [delay], its own way: [dir], [vx] and [vy]
     * logical pixels a second, flapping, and is back on the wire after [away] seconds. Sitting, it looks
     * [faceLeft] or right and turns round now and then, in [turnIn] seconds.
     * (The mockup's birds all look one way and fly off the same way at the same speed without flapping;
     * Ivan found that unnatural.)
     */
    private class Bird(val x: Double, val seg: Int) {
        var faceLeft = false
        var turnIn = 0.0
        var fly = 0.0
        var delay = 0.0
        var dir = 1
        var vx = 15.0
        var vy = 14.0
        var phase = 0.0
        var away = 8.0

        /** Sitting on its wire: not flown off yet, or still waiting for its [delay]. */
        val sitting: Boolean get() = fly == 0.0 || fly - delay <= 0
    }

    private val random = Random(seed)
    private var train: Train? = null
    private var next = 0.5
    private var ti = 0
    private val walkers = ArrayList<Walker>()
    private var nextW = 1.0
    private val smoke = ArrayList<Puff>()
    private val spray = ArrayList<Spray>()
    private var birds: List<Bird> = emptyList()
    private var birdPoles: List<Int>? = null
    private var redT = 0.0
    private var arm = 0.0
    private var t = 0.0
    private var sprayDue = 0.0
    private var geometry: Geometry? = null

    /** The left edge of the train on the track, if one is passing. */
    val trainX: Double? get() = train?.x

    /** The mockup's geometry for a strip [width] wide, halved, and the snow's crest. */
    private class Geometry(val width: Int) {
        val x0 = 0
        val x1 = width
        val base = STRIP_HEIGHT
        val gTop = base - 7
        val railY = base - 2
        val pathY = base - 6
        val poles: List<Int> = (x0 + 20 until x1 - 5 step 85).toList()
        val crossing = x0 + (x1 - x0) * 0.66

        /** The top row of the snow in each column from 0 to [width]: low heaps here and there and a gentle wave. */
        val crest = IntArray(width + 1).also { crest ->
            val mounds = (0..width / MOUND_STEP).map { k ->
                Triple(k * MOUND_STEP + hash(k, 11) * MOUND_STEP, 10 + hash(k, 12) * 16, .6 + hash(k, 13) * 2.4)
            }
            for (x in crest.indices) {
                var b = sin(x / 13.0) * .8 + sin(x / 31.0 + 2) * .6
                for ((c, w, h) in mounds) {
                    val d = (x - c) / w
                    if (abs(d) < 1) b += h * (1 - d * d) * (1 - d * d)
                }
                crest[x] = gTop - jsRound(b).coerceIn(0, 4)
            }
        }
    }

    private fun geometry(width: Int): Geometry =
        geometry?.takeIf { it.width == width } ?: Geometry(width).also { geometry = it }

    /** Whether the train is close enough to the level crossing to close it. */
    private fun near(g: Geometry): Boolean {
        val tr = train ?: return false
        return tr.x + tr.len > g.crossing - 55 && tr.x < g.crossing + 55
    }

    /** Moves everything [dt] seconds on. */
    fun step(dt: Double, width: Int, options: RailwayOptions) {
        val g = geometry(width)
        t += dt
        if (birdPoles != g.poles) {
            // The mockup's rng(4), the same birds on the same wires.
            val r = MockupLcg(4)
            birds = buildList {
                for (i in 0 until g.poles.size - 1) {
                    val n = if (r() < .6) 1 + floor(r() * 2).toInt() else 0
                    repeat(n) { add(Bird(g.poles[i] + 15 + r() * 50, i)) }
                }
            }
            // Which way they look comes from the scene's own random, so the mockup's rng(4) still places them.
            for (bird in birds) {
                bird.faceLeft = random.nextBoolean()
                bird.turnIn = nextTurn()
            }
            birdPoles = g.poles
        }

        if (options.residents) moveWalkers(dt, g) else walkers.clear()
        moveTrain(dt, g, options.frequency)

        arm += ((if (near(g)) 1.0 else 0.0) - arm) * min(1.0, dt * 3)
        redT = max(0.0, redT - dt)

        val tr = train
        for (bird in birds) {
            if (tr != null && bird.fly == 0.0 && bird.x > tr.x - 15 && bird.x < tr.x + tr.len + 15) startle(bird, tr.dir)
            if (bird.fly > 0) {
                bird.fly += dt
                if (bird.fly > bird.away) {
                    // Back on the wire, looking whichever way.
                    bird.fly = 0.0
                    bird.faceLeft = random.nextBoolean()
                    bird.turnIn = nextTurn()
                }
            } else {
                bird.turnIn -= dt
                if (bird.turnIn <= 0) {
                    bird.faceLeft = !bird.faceLeft
                    bird.turnIn = nextTurn()
                }
            }
        }

        val drift = (train?.dir ?: 1) * 4 * dt
        smoke.removeAll { s ->
            s.life -= dt
            if (s.life > 0 && !s.spark) {
                s.y -= 7 * dt
                s.x -= drift
            }
            s.life <= 0
        }
        spray.removeAll { s ->
            s.life -= dt
            if (s.life > 0) {
                s.vy += 110 * dt
                s.x += s.vx * dt
                s.y = min(s.y + s.vy * dt, (g.gTop + 1).toDouble())
            }
            s.life <= 0
        }
    }

    /** Seconds until a sitting bird turns round. */
    private fun nextTurn(): Double = 3 + random.nextDouble() * 7

    /** Sends [bird] off: most fly ahead of a train going [trainDir], some back over it, each at its own pace. */
    private fun startle(bird: Bird, trainDir: Int) {
        bird.fly = 0.01
        bird.delay = random.nextDouble() * .6
        bird.dir = if (random.nextDouble() < .7) trainDir else -trainDir
        bird.vx = 7 + random.nextDouble() * 7
        bird.vy = .5 + random.nextDouble() * .4 // how quickly it climbs to its height, per second
        bird.phase = random.nextDouble() * 2 * PI
        bird.away = bird.delay + 6 + random.nextDouble() * 5
    }

    private fun moveWalkers(dt: Double, g: Geometry) {
        nextW -= dt
        if (nextW <= 0 && walkers.size < 2) {
            val (sprite, v) = RailSprites.WALKERS[random.nextInt(RailSprites.WALKERS.size)]
            val dir = if (random.nextDouble() < .5) 1 else -1
            walkers += Walker(sprite, v * dir, if (dir > 0) (g.x0 - sprite.w).toDouble() else g.x1.toDouble())
            nextW = 3 + random.nextDouble() * 5
        }
        walkers.removeAll { w ->
            w.x += w.v * dt
            w.x > g.x1 + 5 || w.x < g.x0 - w.sprite.w - 5
        }
    }

    private fun moveTrain(dt: Double, g: Geometry, frequency: TrainFrequency) {
        next -= dt
        var moveDt = dt
        if (train == null && next <= 0) {
            // It set off when the pause ran out, somewhere inside this step: the speed doesn't depend on the tick.
            moveDt = min(dt, -next)
            val cars = RailSprites.TRAINS[ti++ % RailSprites.TRAINS.size]
            val dir = if (ti % 3 == 0) -1 else 1
            val len = cars.sumOf { it.w + 1 }
            val speed = when (cars[0]) {
                RailSprites.HAND -> 13.0
                RailSprites.PLOW -> 17.0
                else -> 26.0
            }
            train = Train(cars, dir, len, if (dir > 0) (g.x0 - len).toDouble() else g.x1.toDouble(), speed * dir)
        }
        val tr = train ?: return
        tr.x += tr.v * moveDt
        forEachCar(tr) { car, x ->
            val top = g.railY - car.h
            when (car) {
                RailSprites.STEAM -> if (random.nextDouble() < dt * 8) {
                    smoke += Puff(if (tr.dir > 0) x + car.w - 3 else x + 2, top.toDouble(), 1.6)
                }
                RailSprites.TRAM -> if (random.nextDouble() < dt * 2) {
                    smoke += Puff(x + if (tr.dir > 0) 9 else 8, (top - 1).toDouble(), .2, spark = true)
                }
                RailSprites.PLOW -> {
                    sprayDue += SPRAY_PER_SECOND * dt
                    while (sprayDue >= 1) {
                        sprayDue -= 1
                        spray += Spray(
                            if (tr.dir > 0) x + car.w else x, (g.railY - 4).toDouble(),
                            (15 + random.nextDouble() * 25) * tr.dir, -30 - random.nextDouble() * 25, .8,
                        )
                    }
                }
            }
        }
        if (tr.x > g.x1 + 10 || tr.x + tr.len < g.x0 - 10) {
            train = null
            next = frequency.minPause + random.nextDouble() * frequency.spread
            redT = 3.0
        }
    }

    /** Each car of [tr] with its left edge, the engine first. */
    private inline fun forEachCar(tr: Train, action: (RailSprite, Double) -> Unit) {
        var cx = if (tr.dir > 0) tr.x + tr.len else tr.x
        for (car in tr.cars) {
            action(car, if (tr.dir > 0) cx - car.w else cx)
            cx += if (tr.dir > 0) -(car.w + 1) else car.w + 1
        }
    }

    /**
     * Paints the strip and the birds in the air above it, up to [skyHeight] above the strip. The rows above [CUT]
     * lie over the window's last rows (Ivan's call: the top half of the poles, the wires and the birds may cover
     * the text).
     */
    fun paint(c: PixelCanvas, width: Int, options: RailwayOptions, skyHeight: Int) {
        val g = geometry(width)
        val x0 = g.x0
        val x1 = g.x1
        val gTop = g.gTop
        val railY = g.railY
        val pathY = g.pathY

        // poles and wires, behind everything; the snow covers their feet
        if (options.poles) {
            for ((i, x) in g.poles.withIndex()) {
                c.rect(x, gTop - 17, 1, 18, JinglePalette.ROOF_BROWN)
                c.rect(x - 2, gTop - 15, 5, 1, JinglePalette.ROOF_BROWN)
                c.px(x, gTop - 18, JinglePalette.FAR_SNOW)
                c.rect(x - 2, gTop - 16, 2, 1, JinglePalette.FAR_SNOW)
                c.rect(x + 1, gTop - 16, 2, 1, JinglePalette.FAR_SNOW)
                if (i < g.poles.size - 1) wire(c, x, g.poles[i + 1], gTop - 15)
            }
        }

        // ground, the yard's snow, one row past the bottom so no gap shows when the height isn't whole pixels
        for (x in x0 until x1) {
            val top = g.crest[x]
            c.rect(x, top, 1, g.base - top + 1, JinglePalette.SNOW)
            c.px(x, top, JinglePalette.WHITE)
            if (g.crest[x + 1] > top || (x > 0 && g.crest[x - 1] > top)) c.px(x, top + 1, JinglePalette.SNOW_SHADE)
            if (hash(x, 3) > .82) c.px(x, top + 2 + floor(hash(x, 4) * max(1, pathY - top - 1)).toInt(), JinglePalette.SNOW_SPECK)
        }
        // the trodden path: a shaded rut with gaps, footprints here and there
        for (x in x0 until x1) {
            if (hash(x, 21) > .15) c.px(x, pathY + 2, JinglePalette.SNOW_SHADE)
            if (hash(x / 2, 22) > .7) c.px(x, pathY + 1, JinglePalette.SNOW_SHADE)
        }

        // the halt with its lamp
        val hx = jsRound(x0 + (x1 - x0) * .24)
        c.rect(hx, railY - 2, 30, 2, JinglePalette.STEEL)
        c.rect(hx, railY - 3, 30, 1, JinglePalette.FAR_SNOW)
        RailSprites.LAMP.draw(c, (hx + 4).toDouble(), (railY - 3 - RailSprites.LAMP.h).toDouble())
        c.glow(hx + 5.5, railY - 12.5, 13.0, JinglePalette.LAMP, .35, hx - 8, railY - 26, 28, 26)
        RailSprites.BENCH.draw(c, (hx + 18).toDouble(), (railY - 5).toDouble())
        c.rect(hx + 12, railY - 11, 7, 4, JinglePalette.WALL_BLUE)
        c.rect(hx + 12, railY - 12, 7, 1, JinglePalette.FAR_SNOW)
        c.rect(hx + 15, railY - 7, 1, 4, JinglePalette.ROOF_BROWN)

        c.clipped(x0, 0, x1 - x0, STRIP_HEIGHT) {
            // walkers on the path, behind the rails
            for (w in walkers) {
                val hops = w.sprite === RailSprites.FOX || w.sprite === RailSprites.HARE
                val bob = if (hops) -abs(sin(t * 12)) * 1.5 else 0.0
                w.sprite.draw(this, w.x, pathY + 2 - w.sprite.h + bob, flip = w.v < 0)
            }
            // rails: sleepers, some under snow, and the rail on them
            for (x in x0 until x1 step 4) {
                rect(x, railY, 2, 1, JinglePalette.TRUNK)
                if (hash(x, 23) > .65) px(x + (if (hash(x, 24) > .5) 1 else 0), railY, JinglePalette.SNOW)
            }
            rect(x0, railY - 1, x1 - x0, 1, JinglePalette.STEEL)
            // the train
            train?.let { tr ->
                forEachCar(tr) { car, x -> car.draw(this, x, (railY - car.h).toDouble(), flip = tr.dir < 0) }
            }
        }

        // the level crossing
        val near = near(g)
        val cx = g.crossing
        for (side in intArrayOf(-7, 7)) {
            val p0 = jsRound(cx + side)
            c.rect(p0, railY - 9, 1, 9, JinglePalette.FAR_SNOW)
            for (j in 0 until 9 step 2) c.px(p0, railY - 9 + j, JinglePalette.SANTA_RED)
            val on = near && floor(t * 3).toInt() % 2 == (if (side < 0) 0 else 1)
            c.rect(p0 - 1, railY - 11, 3, 2, if (on) JinglePalette.SIGNAL_RED else JinglePalette.WINDOW_FRAME)
            if (on) c.glow(p0 + .5, (railY - 10).toDouble(), 5.0, JinglePalette.SIGNAL_RED, .5, p0 - 5, railY - 15, 11, 11)
        }
        barrier(c, cx - 7, (railY - 6).toDouble(), PI / 2 * (1 - arm))

        // the semaphore
        val sx = jsRound(x0 + (x1 - x0) * .9)
        c.rect(sx, railY - 15, 1, 15, JinglePalette.ROOF_SLATE)
        c.rect(sx - 1, railY - 16, 3, 5, JinglePalette.COAL)
        val red = redT > 0 || near
        c.px(sx, railY - 15, if (red) JinglePalette.SIGNAL_RED else JinglePalette.NOSE_DARK)
        c.px(sx, railY - 13, if (red) JinglePalette.SIGNAL_RED_OFF else SEMAPHORE_GREEN)

        // bullfinches on the wires and flying off from a train
        if (options.poles) {
            for (bird in birds) {
                if (bird.seg + 1 >= g.poles.size) continue
                val wy = sagAt(g.poles[bird.seg], g.poles[bird.seg + 1], gTop - 15, bird.x)
                if (bird.sitting) RailSprites.FINCH.draw(c, bird.x - 3, wy - 6, flip = !bird.faceLeft)
                else flyingBird(c, bird, bird.fly - bird.delay, wy, x0, x1, skyHeight)
            }
        }

        // smoke, sparks, snow spray; nothing leaves the strip, smoke that rises high melts at its top edge
        for (s in smoke) {
            if (s.spark) {
                c.px(jsRound(s.x), jsRound(s.y), SPARK)
                continue
            }
            val size = 2 + (1.6 - s.life) * 2
            val alpha = (s.life / 1.6 * .55 * 255).roundToInt().coerceIn(0, 255)
            val sz = size.roundToInt()
            c.rect(jsRound(s.x - size / 2), jsRound(s.y - size / 2), sz, sz, smokeColors[alpha])
        }
        for (s in spray) c.px(jsRound(s.x), jsRound(s.y), JinglePalette.FAR_SNOW)
    }

    /** Whether a bird is in the air. */
    private fun hasFlyingBirds(): Boolean = birds.any { !it.sitting && it.fly - it.delay < FLIGHT }

    /** Whether the part of the strip above [CUT] (over the window's text) changes on every step now. */
    fun skyBusy(): Boolean {
        val g = geometry ?: return false
        return hasFlyingBirds() || near(g) || smoke.any { !it.spark && it.y - 3 < CUT } || spray.any { it.y < CUT }
    }

    /** What the part above [CUT] shows when nothing moves there: the semaphore and the birds sitting. Repaint it on a change. */
    fun skyState(): Int {
        var h = if (redT > 0) 1 else 0
        for (bird in birds) h = h * 31 + if (!bird.sitting) 3 else if (bird.faceLeft) 1 else 2
        return h
    }

    /**
     * A bird [k] seconds in the air: it speeds up from the wire, climbs up to [SKY] above the strip, over the
     * window's text, and flies on like a finch, a few wing beats and a short glide. It is gone at once, whole,
     * after [FLIGHT] seconds or before it would cross an end of the strip or the top of the window ([skyHeight]).
     */
    private fun flyingBird(c: PixelCanvas, bird: Bird, k: Double, wy: Double, x0: Int, x1: Int, skyHeight: Int) {
        if (k >= FLIGHT) return
        val dist = if (k < TAKE_OFF) bird.vx * k * k / (2 * TAKE_OFF) else bird.vx * (k - TAKE_OFF / 2)
        val x = bird.x - RailSprites.FINCH_GLIDE.w / 2 + bird.dir * dist
        if (x < x0 || x + RailSprites.FINCH_GLIDE.w > x1) return
        val start = wy - 6
        val y = start + (-SKY - start) * (1 - exp(-bird.vy * k))
        if (y < -skyHeight) return
        // A wing beat in 0.3 s for 0.8 s, then a glide of 0.5 s, each bird from its own point in the cycle.
        val cycle = (k + bird.phase / (2 * PI) * BEAT_CYCLE) % BEAT_CYCLE
        val sprite = when {
            cycle >= FLAPPING -> RailSprites.FINCH_GLIDE
            floor(cycle / HALF_BEAT).toInt() % 2 == 1 -> RailSprites.FINCH_WINGS_DOWN
            else -> RailSprites.FINCH_WINGS_UP
        }
        sprite.draw(c, x, y, flip = bird.dir < 0)
    }

    /** The mockup's `line(x0, x1, y, 5)`, halved: a sagging wire, a pixel a column. */
    private fun wire(c: PixelCanvas, xa: Int, xb: Int, y: Int) {
        for (x in xa..xb) c.px(x, jsRound(sagAt(xa, xb, y, x.toDouble())), JinglePalette.ROOF_SLATE)
    }

    /**
     * The crossing barrier, 7 blocks turned by [angle] around ([ox], [oy]): up when open, level when closed.
     * The mockup rotates the canvas; here each block stays upright and moves, so the pixels stay square.
     */
    private fun barrier(c: PixelCanvas, ox: Double, oy: Double, angle: Double) {
        val cos = cos(angle)
        val sin = sin(angle)
        for (i in 0 until 7) {
            val lx = -i - .5
            val ly = .5
            val rx = lx * cos - ly * sin
            val ry = lx * sin + ly * cos
            val color = if (i % 4 < 2) JinglePalette.SANTA_RED else JinglePalette.FAR_SNOW
            c.px(jsRound(ox + rx - .5), jsRound(oy + ry - .5), color)
        }
    }

    companion object {
        /** Logical pixels from the bottom of the window up to the birds on the wires. */
        const val STRIP_HEIGHT = 31

        /**
         * The strip's rows above this one are painted over the window's text, the rest has room of its own at
         * the bottom of the window: the cut goes through the middle of the poles (Ivan's call).
         */
        const val CUT = 16

        private const val SPRAY_PER_SECOND = 90.0

        /** Columns between the snow's heaps, about. */
        private const val MOUND_STEP = 36

        /** A bird's flight: seconds to reach full speed, seconds in sight at most, its wing beat and glide. */
        private const val TAKE_OFF = 0.7
        private const val FLIGHT = 4.5
        private const val HALF_BEAT = 0.15
        private const val FLAPPING = 0.8
        private const val BEAT_CYCLE = 1.3

        /** How high above the strip the birds climb, logical pixels. */
        const val SKY = 22.0

        /** The mockup's `sagAt` with its sag of 5, halved to 3. */
        private fun sagAt(xa: Int, xb: Int, y: Int, x: Double): Double =
            y + sin(((x - xa) / (xb - xa)).coerceIn(0.0, 1.0) * PI) * 3

        private val SEMAPHORE_GREEN = JinglePalette.BULBS[3]
        private val SPARK = JinglePalette.BULBS[2]

        /** Melting smoke by alpha, made once. */
        @Suppress("UseJBColor")
        private val smokeColors: List<Color> = List(256) {
            Color(JinglePalette.RAIL_SMOKE.red, JinglePalette.RAIL_SMOKE.green, JinglePalette.RAIL_SMOKE.blue, it)
        }
    }
}

/** The railway mockup's `rng(seed)`: a linear congruential generator, in [0, 1). */
internal class MockupLcg(seed: Int) : () -> Double {
    private var s = seed.toLong() and 0xFFFF_FFFFL

    override fun invoke(): Double {
        s = (s * 1664525 + 1013904223) and 0xFFFF_FFFFL
        return s / 4294967296.0
    }
}
