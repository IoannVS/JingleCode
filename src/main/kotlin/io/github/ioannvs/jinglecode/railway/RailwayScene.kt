package io.github.ioannvs.jinglecode.railway

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
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
 * The railway mockup's `strip()` for one tool window, pixel for pixel: snow, rails and the path behind them,
 * poles with bullfinches on the wires, the halt with its lamp, the level crossing, the semaphore, the trains
 * and the walkers. Coordinates are logical pixels of a strip [width] wide and [STRIP_HEIGHT] high, the
 * mockup's CSS pixels with `win` at (0, 0).
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
        var vx = 30.0
        var vy = 14.0
        var phase = 0.0
        var away = 8.0
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

    /** The left edge of the train on the track, if one is passing. */
    val trainX: Double? get() = train?.x

    /** The mockup's geometry for a strip [width] wide. */
    private class Geometry(width: Int) {
        val x0 = 8
        val x1 = width - 8
        val base = STRIP_HEIGHT - 4
        val gTop = base - 14
        val railY = base - 6
        val pathY = base - 11
        val poles: List<Int> = (x0 + 40 until x1 - 10 step 170).toList()
        val crossing = x0 + (x1 - x0) * 0.66
    }

    /** Whether the train is close enough to the level crossing to close it. */
    private fun near(g: Geometry): Boolean {
        val tr = train ?: return false
        return tr.x + tr.len > g.crossing - 110 && tr.x < g.crossing + 110
    }

    /** Moves everything [dt] seconds on. */
    fun step(dt: Double, width: Int, options: RailwayOptions) {
        val g = Geometry(width)
        t += dt
        if (birdPoles != g.poles) {
            // The mockup's rng(4), the same birds on the same wires.
            val r = MockupLcg(4)
            birds = buildList {
                for (i in 0 until g.poles.size - 1) {
                    val n = if (r() < .6) 1 + floor(r() * 2).toInt() else 0
                    repeat(n) { add(Bird(g.poles[i] + 30 + r() * 100, i)) }
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
            if (tr != null && bird.fly == 0.0 && bird.x > tr.x - 30 && bird.x < tr.x + tr.len + 30) startle(bird, tr.dir)
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

        val drift = (train?.dir ?: 1) * 8 * dt
        smoke.removeAll { s ->
            s.life -= dt
            if (s.life > 0 && !s.spark) {
                s.y -= 14 * dt
                s.x -= drift
            }
            s.life <= 0
        }
        spray.removeAll { s ->
            s.life -= dt
            if (s.life > 0) {
                s.vy += 220 * dt
                s.x += s.vx * dt
                s.y = min(s.y + s.vy * dt, (g.gTop + 2).toDouble())
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
        bird.vx = 14 + random.nextDouble() * 14
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
            w.x > g.x1 + 10 || w.x < g.x0 - w.sprite.w - 10
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
            val len = cars.sumOf { it.w + 2 }
            val speed = when (cars[0]) {
                RailSprites.HAND -> 26.0
                RailSprites.PLOW -> 34.0
                else -> 52.0
            }
            train = Train(cars, dir, len, if (dir > 0) (g.x0 - len).toDouble() else g.x1.toDouble(), speed * dir)
        }
        val tr = train ?: return
        tr.x += tr.v * moveDt
        forEachCar(tr) { car, x ->
            val top = g.railY - car.h
            when (car) {
                RailSprites.STEAM -> if (random.nextDouble() < dt * 8) {
                    smoke += Puff(if (tr.dir > 0) x + car.w - 6 else x + 4, top.toDouble(), 1.6)
                }
                RailSprites.TRAM -> if (random.nextDouble() < dt * 2) {
                    smoke += Puff(x + if (tr.dir > 0) 18 else 16, (top - 2).toDouble(), .2, spark = true)
                }
                RailSprites.PLOW -> {
                    // The mockup throws 3 flakes a browser frame, about 60 frames a second.
                    sprayDue += SPRAY_PER_SECOND * dt
                    while (sprayDue >= 1) {
                        sprayDue -= 1
                        spray += Spray(
                            if (tr.dir > 0) x + car.w else x, (g.railY - 8).toDouble(),
                            (30 + random.nextDouble() * 50) * tr.dir, -60 - random.nextDouble() * 50, .8,
                        )
                    }
                }
            }
        }
        if (tr.x > g.x1 + 20 || tr.x + tr.len < g.x0 - 20) {
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
            cx += if (tr.dir > 0) -(car.w + 2) else car.w + 2
        }
    }

    fun paint(c: PixelCanvas, width: Int, options: RailwayOptions) {
        val g = Geometry(width)
        val x0 = g.x0
        val x1 = g.x1
        val gTop = g.gTop
        val railY = g.railY
        val pathY = g.pathY

        // poles and wires, behind everything
        if (options.poles) {
            for ((i, x) in g.poles.withIndex()) {
                c.rect(x, gTop - 34, 2, 34, JinglePalette.ROOF_BROWN)
                c.rect(x - 4, gTop - 32, 10, 2, JinglePalette.ROOF_BROWN)
                c.rect(x - 4, gTop - 34, 10, 1, JinglePalette.FAR_SNOW)
                if (i < g.poles.size - 1) wire(c, x + 1, g.poles[i + 1] + 1, gTop - 31)
            }
        }

        // ground
        c.rect(x0, gTop, x1 - x0, 2, JinglePalette.FAR_SNOW)
        c.rect(x0, gTop + 2, x1 - x0, g.base - gTop - 2, JinglePalette.PUFF)
        c.rect(x0, pathY + 2, x1 - x0, 1, JinglePalette.SNOW_SHADE)
        for (x in x0 + 3 until x1 step 7) c.rect(x, pathY + 1, 3, 1, JinglePalette.SNOW_SHADE)

        // the halt with its lamp
        val hx = x0 + (x1 - x0) * .24
        c.rect(jsRound(hx), railY - 3, 60, 3, JinglePalette.STEEL)
        c.rect(jsRound(hx), railY - 4, 60, 1, JinglePalette.FAR_SNOW)
        RailSprites.LAMP.draw(c, hx + 8, (railY - 4 - 22).toDouble())
        c.glow(hx + 11, (railY - 25).toDouble(), 26.0, JinglePalette.LAMP, .35, jsRound(hx - 16), railY - 52, 56, 52)
        RailSprites.BENCH.draw(c, hx + 36, (railY - 7).toDouble())
        c.rect(jsRound(hx + 24), railY - 22, 14, 7, JinglePalette.WALL_BLUE)
        c.rect(jsRound(hx + 25), railY - 21, 12, 1, JinglePalette.FAR_SNOW)
        c.rect(jsRound(hx + 30), railY - 15, 2, 12, JinglePalette.ROOF_BROWN)

        c.clipped(x0, 0, x1 - x0, STRIP_HEIGHT) {
            // walkers on the path, behind the rails
            for (w in walkers) {
                val hops = w.sprite === RailSprites.FOX || w.sprite === RailSprites.HARE
                val bob = if (hops) -abs(sin(t * 12)) * 2 else 0.0
                w.sprite.draw(this, w.x, pathY + 2 - w.sprite.h + bob, flip = w.v < 0)
            }
            // rails
            for (x in x0 until x1 step 7) rect(x, railY, 4, 2, JinglePalette.TRUNK)
            rect(x0, railY - 1, x1 - x0, 1, JinglePalette.STEEL)
            // the train
            train?.let { tr ->
                forEachCar(tr) { car, x -> car.draw(this, x, (railY - car.h).toDouble(), flip = tr.dir < 0) }
            }
        }

        // the level crossing
        val near = near(g)
        val cx = g.crossing
        for (side in intArrayOf(-14, 14)) {
            val p0 = jsRound(cx + side)
            c.rect(p0, railY - 18, 2, 18, JinglePalette.FAR_SNOW)
            for (j in 0 until 18 step 4) c.rect(p0, railY - 18 + j, 2, 2, JinglePalette.SANTA_RED)
            val on = near && floor(t * 3).toInt() % 2 == (if (side < 0) 0 else 1)
            c.rect(p0 - 2, railY - 22, 6, 4, if (on) JinglePalette.SIGNAL_RED else JinglePalette.WINDOW_FRAME)
            if (on) c.glow(p0 + 1.0, (railY - 20).toDouble(), 10.0, JinglePalette.SIGNAL_RED, .5, p0 - 10, railY - 30, 22, 22)
        }
        barrier(c, cx - 14, (railY - 12).toDouble(), PI / 2 * (1 - arm))

        // the semaphore
        val sx = jsRound(x0 + (x1 - x0) * .9)
        c.rect(sx, railY - 30, 2, 30, JinglePalette.ROOF_SLATE)
        c.rect(sx - 2, railY - 32, 6, 10, JinglePalette.COAL)
        val red = redT > 0 || near
        c.rect(sx, railY - 31, 2, 2, if (red) JinglePalette.SIGNAL_RED else JinglePalette.NOSE_DARK)
        c.rect(sx, railY - 27, 2, 2, if (red) JinglePalette.SIGNAL_RED_OFF else SEMAPHORE_GREEN)

        // bullfinches on the wires, flying off from a train
        if (options.poles) {
            for (bird in birds) {
                if (bird.seg + 1 >= g.poles.size) continue
                val wy = sagAt(g.poles[bird.seg], g.poles[bird.seg + 1], gTop - 31, bird.x)
                // Seconds in the air; until then it sits, a beat late after its neighbours.
                val k = bird.fly - bird.delay
                if (bird.fly == 0.0 || k <= 0) RailSprites.FINCH.draw(c, bird.x - 6, wy - 12, flip = bird.faceLeft)
                // flying ones are painted over the window by paintSky
            }
        }

        // smoke, sparks, snow spray
        for (s in smoke) {
            if (s.spark) {
                c.rect(jsRound(s.x), jsRound(s.y), 2, 2, SPARK)
                continue
            }
            val size = 4 + (1.6 - s.life) * 4
            val alpha = (s.life / 1.6 * .55 * 255).roundToInt().coerceIn(0, 255)
            val sz = size.roundToInt()
            c.rect(jsRound(s.x - size / 2), jsRound(s.y - size / 2), sz, sz, smokeColors[alpha])
        }
        for (s in spray) c.rect(jsRound(s.x), jsRound(s.y), 2, 2, JinglePalette.FAR_SNOW)
    }

    /** Whether a bird is in the air and must be painted by [paintSky]. */
    fun hasFlyingBirds(): Boolean = birds.any { it.fly > 0 && it.fly - it.delay > 0 && it.fly - it.delay < FLIGHT }

    /**
     * The birds in the air, over the window's text above the strip (Ivan's call: the strip itself never covers
     * it, the birds may). Same coordinates as [paint]; negative `y` is above the strip, up to [skyHeight].
     */
    fun paintSky(c: PixelCanvas, width: Int, skyHeight: Int) {
        if (birds.isEmpty()) return
        val g = Geometry(width)
        if (g.poles.size < 2) return
        for (bird in birds) {
            val k = bird.fly - bird.delay
            if (bird.fly == 0.0 || k <= 0 || bird.seg + 1 >= g.poles.size) continue
            val wy = sagAt(g.poles[bird.seg], g.poles[bird.seg + 1], g.gTop - 31, bird.x)
            flyingBird(c, bird, k, wy, g.x0, g.x1, skyHeight)
        }
    }

    /**
     * A bird [k] seconds in the air: it speeds up from the wire, climbs up to [SKY] above the strip, over the
     * window's text, and flies on like a finch, a few wing beats and a short glide. It is gone at once, whole,
     * after [FLIGHT] seconds or before it would cross an end of the strip or the top of the window ([skyHeight]).
     */
    private fun flyingBird(c: PixelCanvas, bird: Bird, k: Double, wy: Double, x0: Int, x1: Int, skyHeight: Int) {
        if (k >= FLIGHT) return
        val dist = if (k < TAKE_OFF) bird.vx * k * k / (2 * TAKE_OFF) else bird.vx * (k - TAKE_OFF / 2)
        val x = bird.x - 6 + bird.dir * dist
        if (x < x0 || x + RailSprites.FINCH.w > x1) return
        val start = wy - 12
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

    /** The mockup's `line(x0, x1, y, 5)`: a sagging wire, a pixel a column. */
    private fun wire(c: PixelCanvas, xa: Int, xb: Int, y: Int) {
        for (x in xa..xb) c.rect(x, jsRound(sagAt(xa, xb, y, x.toDouble())), 1, 1, JinglePalette.ROOF_SLATE)
    }

    /**
     * The crossing barrier, 14 blocks turned by [angle] around ([ox], [oy]): up when open, level when closed.
     * The mockup rotates the canvas; here each block stays upright and moves, so the pixels stay square.
     */
    private fun barrier(c: PixelCanvas, ox: Double, oy: Double, angle: Double) {
        val cos = cos(angle)
        val sin = sin(angle)
        for (i in 0 until 14) {
            val lx = -i * 2 - 2 + 1.0
            val ly = 1.0
            val rx = lx * cos - ly * sin
            val ry = lx * sin + ly * cos
            val color = if (i % 4 < 2) JinglePalette.SANTA_RED else JinglePalette.FAR_SNOW
            c.rect(jsRound(ox + rx - 1), jsRound(oy + ry - 1), 2, 2, color)
        }
    }

    companion object {
        /** Logical pixels from the bottom of the window up to the birds on the wires. */
        const val STRIP_HEIGHT = 62

        private const val SPRAY_PER_SECOND = 180.0

        /** A bird's flight: seconds to reach full speed, seconds in sight at most, its wing beat and glide. */
        private const val TAKE_OFF = 0.7
        private const val FLIGHT = 4.5
        private const val HALF_BEAT = 0.15
        private const val FLAPPING = 0.8
        private const val BEAT_CYCLE = 1.3

        /** How high above the strip the birds climb, logical pixels. */
        const val SKY = 44.0

        /** The mockup's `sagAt` with its sag of 5. */
        private fun sagAt(xa: Int, xb: Int, y: Int, x: Double): Double =
            y + sin(((x - xa) / (xb - xa)).coerceIn(0.0, 1.0) * PI) * 5

        private val SEMAPHORE_GREEN = JinglePalette.BULBS[3]
        private val SPARK = JinglePalette.BULBS[2]

        /** Melting smoke by alpha, made once. */
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
