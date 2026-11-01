package io.github.ioannvs.jinglecode.yard

import io.github.ioannvs.jinglecode.drift.ElfPose
import io.github.ioannvs.jinglecode.drift.drawElf
import io.github.ioannvs.jinglecode.drift.drawReindeerHead
import io.github.ioannvs.jinglecode.drift.drawSleigh
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import io.github.ioannvs.jinglecode.sprites.drawFirTree
import io.github.ioannvs.jinglecode.village.MOCKUP_WIDTH
import io.github.ioannvs.jinglecode.village.StarLook
import io.github.ioannvs.jinglecode.village.starLook
import io.github.ioannvs.jinglecode.yard.YardSprites.FINCH
import io.github.ioannvs.jinglecode.yard.YardSprites.FINCHF
import io.github.ioannvs.jinglecode.yard.YardSprites.GIFT_COLORS
import io.github.ioannvs.jinglecode.yard.YardSprites.SANTA
import java.awt.Color
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Santa's yard mockup's `strip()` for one right tool window, at the pixel of the snowman's drift and the
 * editor's village: the mockup at half its size, so one logical pixel here is one sprite pixel there. Distant
 * firs, the village's snow, the stable with the fence and the hay, the grazing reindeer, the workshop with its
 * clock, an elf on the roof, Santa on the bench, the chute, the sleigh, the big fir, the mailbox and the sack.
 * Firs are the village's ([drawFirTree]); the sleigh, the reindeer and the elves are the ones that come for
 * the gifts after a push ([drawSleigh], [drawElf]). Coordinates are logical pixels of a strip [width] wide
 * and [STRIP_HEIGHT] high.
 *
 * The mockup moves things while drawing; here [step] moves them by the seconds passed (so the speed doesn't
 * depend on the frame rate) and [paint] only draws. The mockup's `Math.random()` is a seeded [random].
 * The events (the mockup's `EV`) are [taskStarted], [taskFinished] and [letter].
 */
internal class YardScene(seed: Int) {

    private enum class ChiefState { SIT, WALK_TO, RIDE, WALK_BACK }
    private enum class SleighState { PARK, GO, AWAY, BACK }
    private enum class ElfState { IDLE, TO_PILE, TO_SLEIGH }
    private enum class BirdState { IN, DROP, OUT }
    private enum class TreeMode { OK, FAIL }

    private class Chief(var x: Double, var state: ChiefState = ChiefState.SIT)
    private class Sleigh(var x: Double, var state: SleighState = SleighState.PARK, var v: Double = 0.0)
    private class Elf(var x: Double, val home: Double, var wait: Double) {
        var state = ElfState.IDLE
        var carry: Color? = null
    }
    private class ChuteGift(val color: Color, var k: Double = 0.0)
    private class Smoke(var x: Double, var y: Double, var life: Double, val run: Boolean)
    private class Particle(var x: Double, var y: Double, var vx: Double, var vy: Double, var life: Double, val color: Color) {
        var landed = false
    }
    private class Bird {
        var state = BirdState.IN
        var k = 0.0
        var x = 0.0
        var y = 0.0
        var ly = 0.0
    }

    private val random = Random(seed)

    private var t = 0.0
    private var running = false
    private var load = 0
    private var mail = 0
    private var scratch = 0.0
    private val chute = ArrayList<ChuteGift>()
    private var pile = 0
    private var chuteT = 0.0
    private var chief: Chief? = null
    private var sleigh: Sleigh? = null
    private var elves: List<Elf>? = null
    private val smoke = ArrayList<Smoke>()
    private val confetti = ArrayList<Particle>()
    private val sparks = ArrayList<Particle>()
    private val puffs = ArrayList<Particle>()
    private var bird: Bird? = null
    private var smokeT = 0.0
    private var shovelT = 0.0
    private var treeMode: TreeMode? = null
    private var treeT = 0.0
    private var fetching = false
    private var departed = false
    private var awayFor = 0.0

    /** The sleigh's left edge, for tests. */
    internal val sleighX: Double? get() = sleigh?.x

    private var geometry: Geometry? = null

    private fun geometry(width: Int): Geometry {
        geometry?.takeIf { it.width == width }?.let { return it }
        // The mockup's reset() when the window is resized: everyone back to their places.
        val g = Geometry(width)
        geometry = g
        chief = Chief(g.bx)
        sleigh = Sleigh(g.parkX)
        elves = g.homes.map { Elf(it, it, random.nextDouble()) }
        chute.clear()
        pile = 0
        return g
    }

    /** Moves everything [dt] seconds on in a strip [width] logical pixels wide. */
    fun step(dt: Double, width: Int) {
        val g = geometry(width)
        t += dt
        scratch = max(0.0, scratch - dt)
        val ch = chief ?: return
        val sl = sleigh ?: return
        val elves = elves ?: return
        if (sl.state == SleighState.PARK) sl.x = g.parkX

        // smoke from the chimney, thicker while a task runs
        smokeT -= dt
        if (smokeT <= 0) {
            smoke += Smoke(g.wx + 18, g.gy - 23, 1.4, running)
            smokeT = if (running) .18 else 1.1
        }
        // snow off the roof elf's shovel
        shovelT -= dt
        if (shovelT <= 0) {
            repeat(5) {
                puffs += Particle(g.wx - 2, g.roofElfBottom - 3.0, -15 - random.nextDouble() * 15, -15 - random.nextDouble() * 10, 1.0, JinglePalette.FAR_SNOW)
            }
            shovelT = 1.25
        }

        // gifts sliding down the chute while a task runs
        if (running && sl.state == SleighState.PARK) {
            chuteT -= dt
            if (chuteT <= 0) {
                chute += ChuteGift(GIFT_COLORS[random.nextInt(GIFT_COLORS.size)])
                chuteT = 1.4
            }
        }
        chute.removeAll { gift ->
            gift.k += dt * (.6 + gift.k)
            (gift.k >= 1).also { if (it) pile = min(4, pile + 1) }
        }

        // Santa and the sleigh
        fun walk(target: Double): Boolean {
            val d = target - ch.x
            ch.x += sign(d) * min(abs(d), WALK * dt)
            return abs(target - ch.x) < 1
        }
        when (ch.state) {
            ChiefState.WALK_TO -> if (walk(sl.x + 2)) {
                ch.state = ChiefState.RIDE
                sl.state = SleighState.GO
                sl.v = 5.0
            }
            ChiefState.WALK_BACK -> if (walk(g.bx)) ch.state = ChiefState.SIT
            else -> Unit
        }
        when (sl.state) {
            SleighState.GO -> {
                sl.v = min(60.0, sl.v + 45 * dt)
                sl.x += sl.v * dt
                if (sl.x > g.x1 + 5) {
                    sl.x = g.x0 - 70
                    load = 0
                    if (fetching) {
                        // Off to the pile by the snowman: away until that visit is over ([comeBack]).
                        fetching = false
                        departed = true
                        awayFor = 0.0
                        sl.state = SleighState.AWAY
                    }
                    else {
                        sl.state = SleighState.BACK
                    }
                }
            }
            SleighState.AWAY -> {
                awayFor += dt
                if (awayFor > AWAY_MAX) sl.state = SleighState.BACK
            }
            SleighState.BACK -> {
                val d = g.parkX - sl.x
                sl.v = max(9.0, min(60.0, d * 1.4))
                sl.x += sl.v * dt
                if (sl.x >= g.parkX) {
                    sl.x = g.parkX
                    sl.state = SleighState.PARK
                    sl.v = 0.0
                    ch.state = ChiefState.WALK_BACK
                    ch.x = g.parkX + 2
                }
            }
            SleighState.PARK -> Unit
        }
        if ((sl.state == SleighState.GO || sl.state == SleighState.BACK) && random.nextDouble() < dt * SPARKS_PER_SECOND) {
            sparks += Particle(sl.x + 1, g.gyAt(sl.x + 15) - lift(g, sl) - 1 - random.nextDouble() * 3, 0.0, 0.0, .6, JinglePalette.FAR_SNOW)
        }

        // elves: take from the chute pile, carry to the sleigh
        for (e in elves) {
            fun go(target: Double): Boolean {
                val d = target - e.x
                e.x += sign(d) * min(abs(d), WALK * dt)
                return abs(target - e.x) < 1
            }
            when (e.state) {
                ElfState.IDLE -> if (running && sl.state == SleighState.PARK) {
                    e.wait -= dt
                    if (e.wait <= 0) e.state = ElfState.TO_PILE
                } else if (abs(e.x - e.home) > 1) go(e.home)
                ElfState.TO_PILE -> if (go(g.chuteEnd - 4)) {
                    if (pile > 0) {
                        pile--
                        e.carry = GIFT_COLORS[random.nextInt(GIFT_COLORS.size)]
                        e.state = ElfState.TO_SLEIGH
                    } else if (!running) e.state = ElfState.IDLE
                }
                ElfState.TO_SLEIGH -> if (sl.state != SleighState.PARK) e.state = ElfState.IDLE
                else if (go(g.parkX + 4)) {
                    e.carry = null
                    load = min(6, load + 1)
                    e.state = if (running) ElfState.TO_PILE else ElfState.IDLE
                }
            }
        }

        // the bullfinch brings a letter
        bird?.let { b ->
            b.k += dt
            val tx = g.mx + 8
            val ty = g.mgy - 15
            when (b.state) {
                BirdState.IN -> {
                    val k = min(1.0, b.k / 1.6)
                    b.x = g.x1 + 5 + (tx - g.x1 - 5) * k
                    b.y = g.base - 29 + (ty - g.base + 29) * k + sin(b.k * 10)
                    if (k >= 1) {
                        b.state = BirdState.DROP
                        b.k = 0.0
                        b.ly = b.y + 6
                    }
                }
                BirdState.DROP -> {
                    b.ly += 20 * dt
                    if (b.ly >= g.mgy - 7) {
                        mail = min(5, mail + 1)
                        b.state = BirdState.OUT
                        b.k = 0.0
                    }
                }
                BirdState.OUT -> {
                    b.x += 35 * dt
                    b.y -= 10 * dt
                    if (b.x > g.x1 + 10) bird = null
                }
            }
        }

        // particles
        confetti.removeAll { c ->
            c.life -= dt
            if (c.life > 0 && !c.landed) {
                c.vy += 120 * dt
                c.x += c.vx * dt
                c.y += c.vy * dt
                val ground = g.groundY(c.x)
                if (c.y >= ground) {
                    c.y = ground
                    c.landed = true
                }
            }
            c.life <= 0
        }
        puffs.removeAll { s ->
            s.life -= dt
            if (s.life > 0) {
                s.vy += 60 * dt
                s.x += s.vx * dt
                s.y += s.vy * dt
            }
            s.life <= 0
        }
        sparks.removeAll { s ->
            s.life -= dt
            s.life <= 0
        }
        smoke.removeAll { s ->
            s.life -= dt
            if (s.life > 0) {
                s.y -= 6 * dt
                s.x += 2 * dt
            }
            s.life <= 0
        }
        treeT = max(0.0, treeT - dt)
        if (treeT == 0.0) treeMode = null
    }

    // ---------- events (the mockup's EV) ----------

    /** A task started: gifts come down the chute, the elves carry them to the sleigh. */
    fun taskStarted() {
        if (running) return
        running = true
        chuteT = 0.0
        elves?.forEachIndexed { i, e -> e.wait = .3 + i * .9 }
    }

    /** A task finished: the tree flashes gold and Santa rides off, or a gift bursts and the tree blinks red. */
    fun taskFinished(succeeded: Boolean) = if (succeeded) success() else error()

    private fun success() {
        running = false
        treeMode = TreeMode.OK
        treeT = 3.0
        val ch = chief ?: return
        val sl = sleigh ?: return
        if (sl.state == SleighState.PARK && ch.state == ChiefState.SIT) {
            load = max(load + pile, 2)
            pile = 0
            ch.state = ChiefState.WALK_TO
        }
    }

    private fun error() {
        running = false
        treeMode = TreeMode.FAIL
        treeT = 2.5
        scratch = 2.6
        val g = geometry ?: return
        val e = elves?.firstOrNull { it.carry != null }
        val sl = sleigh
        when {
            e != null -> {
                burst(e.x + 2, g.gy - 13)
                e.carry = null
                e.state = ElfState.IDLE
            }
            pile > 0 -> {
                pile--
                burst(g.chuteEnd - 3, g.gy - 5)
            }
            load > 0 && sl != null && sl.state == SleighState.PARK -> {
                load--
                burst(sl.x + 7, g.gy - 11)
            }
            else -> burst(g.chuteEnd - 3, g.gy - 5)
        }
    }

    /** Tasks ended without a result (stopped, cancelled), or the reactions are off: the yard just goes quiet. */
    fun taskStopped() {
        running = false
    }

    /**
     * A push: the sleigh goes for the gifts by the snowman. Santa walks to it and rides off to the right, up into
     * the sky; it stays away until [comeBack]. Returns whether it is leaving (or already riding off); `false` if it
     * is on its way back or away already, then nobody leaves.
     */
    fun fetch(): Boolean {
        val ch = chief ?: return false
        val sl = sleigh ?: return false
        return when {
            sl.state == SleighState.PARK && ch.state == ChiefState.SIT -> {
                load = 0
                ch.state = ChiefState.WALK_TO
                fetching = true
                true
            }
            ch.state == ChiefState.WALK_TO || sl.state == SleighState.GO -> {
                fetching = true
                true
            }
            else -> false
        }
    }

    /** Whether the sleigh has just left for the snowman's pile, since the last call. */
    fun takeDeparted(): Boolean = departed.also { departed = false }

    /** Whether the sleigh is away at the snowman's pile. */
    val away: Boolean get() = sleigh?.state == SleighState.AWAY

    /** The visit by the snowman is over: the sleigh comes back from the left with [cargo] gifts. */
    fun comeBack(cargo: Int) {
        val sl = sleigh ?: return
        if (sl.state != SleighState.AWAY) return
        load = cargo.coerceIn(0, 6)
        sl.state = SleighState.BACK
    }

    /** A notification: the bullfinch brings a letter to the sack. */
    fun letter() {
        if (bird == null) bird = Bird()
    }

    /** The sack is emptied, once a day. */
    fun emptySack() {
        mail = 0
    }

    private fun burst(x: Double, y: Double) {
        for (i in 0 until 34) {
            confetti += Particle(x, y, (random.nextDouble() - .5) * 70, -30 - random.nextDouble() * 45, 3 + random.nextDouble() * 1.5, YardSprites.CONFETTI[i % 6])
        }
    }

    // ---------- painting ----------

    /** Draws the strip as it is now; [time] is the time of day for the clock, [sky] the stars over the yard, if any. */
    fun paint(c: PixelCanvas, width: Int, time: LocalTime, sky: YardSky? = null) {
        val g = geometry(width)
        val ch = chief ?: return
        val sl = sleigh ?: return
        val groups = g.layout

        if (sky != null) c.stars(width, sky)

        // back layer: distant dark firs along the horizon
        var x = g.x0 + 5
        var i = 0
        while (x < g.x1) {
            if (i % 3 != 1) c.farFir(jsRound(x), jsRound(g.groundY(x)) + 2, 5 + (i * 7 % 9) / 2)
            x += 13 + (i * 13 % 17) / 2
            i++
        }
        c.ground(g)

        groups[FIRS_L]?.let { f ->
            c.fir(g, f + 4, 13)
            c.fir(g, f + 10, 9)
            c.birch(jsRound(f + 1), jsRound(g.gyAt(f + 1)), 10)
        }
        groups[STABLE]?.let { s -> c.stable(jsRound(s + 15), jsRound(g.gyAt(s + 25))) }
        groups[GRAZE]?.let { gx -> c.grazer(jsRound(gx), jsRound(g.gyAt(gx + 6))) }
        val wx = jsRound(g.wx)
        val gy = jsRound(g.gy)
        c.workshop(wx, gy, time)
        // the elf shovelling snow on the roof
        run {
            val ex = wx + 2
            val bottom = g.roofElfBottom
            val sw = if (floor(t * 1.6).toInt() % 2 != 0) -1 else 0
            c.drawElf(ex, bottom, ElfPose.STAND, 0)
            c.rect(ex - 3 + sw, bottom - 2, 3, 1, JinglePalette.BIRCH_TWIG)
            c.rect(ex - 4 + sw, bottom - 2, 1, 2, JinglePalette.STEEL)
        }
        c.bench(jsRound(g.bx), gy)
        // the chute from the hatch to the sleigh
        val chuteEnd = jsRound(g.chuteEnd)
        val gyP = jsRound(g.gyP)
        c.chute(wx + 24, gy - 9, chuteEnd, gyP - 2, gyP)
        for (k in 0 until min(pile, 3)) {
            c.gift(chuteEnd - 4 + k * 3 - (if (k > 1) 3 else 0), gyP - 3 - (if (k > 1) 3 else 0), GIFT_COLORS[(k + 2) % 5])
        }
        // tree, mail
        groups[TREE]?.let { tr -> c.bigTree(jsRound(tr + 10), jsRound(g.gyAt(tr + 10))) }
        val mx = jsRound(g.mx)
        val mgy = jsRound(g.mgy)
        c.mailbox(mx, mgy)
        c.sack(mx + 10, mgy, mail)
        groups[FIRS_R]?.let { f ->
            c.fir(g, f + 4, 11)
            c.fir(g, f + 9, 8)
        }

        // gifts sliding down the chute
        for (gift in chute) {
            val k = min(1.0, gift.k)
            c.gift(jsRound(wx + 24 + (chuteEnd - wx - 24) * k) - 1, jsRound(gy - 9 + (gyP - 2 - (gy - 9)) * k) - 4, gift.color)
        }

        if (ch.state == ChiefState.SIT) c.chief(ch.x, (gy - 3).toDouble(), sitting = true)
        val moving = sl.state != SleighState.PARK
        val teamGround = jsRound(g.gyAt(sl.x + 15))
        if (sl.state != SleighState.AWAY) c.drawSleigh(jsRound(sl.x), teamGround - 7 - jsRound(lift(g, sl)), if (moving) floor(t * 10).toInt() else 0, cargo = load, santa = ch.state == ChiefState.RIDE)
        if (ch.state == ChiefState.WALK_TO || ch.state == ChiefState.WALK_BACK) {
            c.chief(ch.x, g.gyAt(ch.x + 4) - floor(t * 8).toInt() % 2, sitting = false)
        }
        for (e in elves.orEmpty()) {
            val hop = e.state == ElfState.IDLE && sin(t * 2 + e.home) > .92
            val pose = when {
                e.carry != null -> ElfPose.TOSS
                e.state != ElfState.IDLE -> ElfPose.RUN
                hop -> ElfPose.JUMP
                else -> ElfPose.STAND
            }
            val ex = jsRound(e.x)
            val bottom = jsRound(g.gyAt(e.x + 2)) - 1 - (if (hop) 1 else 0)
            c.drawElf(ex, bottom, pose, floor(t * 10).toInt())
            e.carry?.let { c.gift(ex + 1, bottom - 9, it) }
        }

        // the bullfinch with a letter
        bird?.let { b ->
            val flap = if (floor(b.k * 8).toInt() % 2 != 0) FINCH else FINCHF
            when (b.state) {
                BirdState.IN -> {
                    flap.draw(c, b.x, b.y, flip = true)
                    c.letterAt(b.x, b.y + 6)
                }
                BirdState.DROP -> {
                    c.letterAt(b.x, b.ly)
                    FINCH.draw(c, b.x, b.y, flip = true)
                }
                BirdState.OUT -> flap.draw(c, b.x, b.y, flip = false)
            }
        }

        // particles; confetti, sparks and smoke melt away, as in the mockup
        for (p in confetti) c.px(jsRound(p.x), jsRound(p.y), alpha(p.color, min(1.0, p.life)))
        for (p in puffs) c.px(jsRound(p.x), jsRound(p.y), JinglePalette.FAR_SNOW)
        for ((k, p) in sparks.withIndex()) c.px(jsRound(p.x), jsRound(p.y), alpha(if (k % 2 != 0) JinglePalette.LAMP else JinglePalette.FAR_SNOW, p.life / .6))
        for (s in smoke) {
            val size = 1.5 + (1.4 - s.life) * 1.5
            val sz = size.roundToInt()
            c.rect(jsRound(s.x - size / 2), jsRound(s.y - size / 2), sz, sz, alpha(JinglePalette.RAIL_SMOKE, s.life / 1.4 * (if (s.run) .6 else .35)))
        }
    }

    /**
     * How high the sleigh flies over the snow: it takes off a little after it sets off and lands as it comes back
     * (it rides on the ground in the mockup; Ivan: the sleigh flies off).
     */
    private fun lift(g: Geometry, sl: Sleigh): Double = when (sl.state) {
        SleighState.GO -> ((sl.x - g.parkX - 10) * .3).coerceIn(0.0, MAX_LIFT)
        SleighState.BACK -> ((g.parkX - sl.x - 10) * .3).coerceIn(0.0, MAX_LIFT)
        else -> 0.0
    }

    // ---------- the mockup's drawing functions, at half size ----------

    private fun PixelCanvas.letterAt(x: Double, y: Double) {
        rect(jsRound(x) + 1, jsRound(y), 4, 3, JinglePalette.FAR_SNOW)
        rect(jsRound(x) + 2, jsRound(y) + 1, 2, 1, JinglePalette.SNOW_SHADE)
    }

    /** The mockup's `workshop`: log walls, a red roof under snow, the clock in the gable, the gate, the hatch. */
    private fun PixelCanvas.workshop(wx: Int, gy: Int, time: LocalTime) {
        val ww = 25
        // walls, log ends at the corners
        rect(wx, gy - 12, ww, 12, JinglePalette.TRUNK)
        for (y in intArrayOf(gy - 10, gy - 7, gy - 4)) rect(wx, y, ww, 1, JinglePalette.ROOF_BROWN)
        for (k in 0 until 6) {
            px(wx - 1, gy - 12 + k * 2, JinglePalette.LOG_CUT)
            px(wx + ww, gy - 12 + k * 2, JinglePalette.LOG_CUT)
        }
        // chimney
        rect(wx + 17, gy - 21, 3, 5, JinglePalette.WALL_RED)
        rect(wx + 17, gy - 20, 3, 1, JinglePalette.CHIMNEY_BAND)
        rect(wx + 16, gy - 22, 5, 1, JinglePalette.FAR_SNOW)
        // roof, a step a row, snow on its edges and top
        for (j in 0 until 8) {
            val y = gy - 14 - j
            val xa = wx - 2 + j * 2
            val xb = wx + ww + 2 - j * 2
            if (xb <= xa) break
            rect(xa, y, xb - xa, 1, if (j >= 6) JinglePalette.FAR_SNOW else if (j % 2 != 0) JinglePalette.WALL_RED else JinglePalette.SANTA_RED)
            if (j < 6) {
                px(xa, y, JinglePalette.FAR_SNOW)
                px(xb - 1, y, JinglePalette.FAR_SNOW)
            }
        }
        rect(wx - 2, gy - 13, ww + 4, 1, JinglePalette.FAR_SNOW)
        // icicles under the eaves
        var k = 0
        for (x in wx - 1 until wx + ww + 1 step 3) rect(x, gy - 12, 1, 1 + k++ % 2, JinglePalette.SNOW_SHADE)
        // the clock in the gable shows the time
        val cx = wx + 12
        val cy = gy - 17
        rect(cx - 1, cy - 2, 3, 1, JinglePalette.GOLD)
        rect(cx - 2, cy - 1, 5, 3, JinglePalette.FAR_SNOW)
        rect(cx - 1, cy + 2, 3, 1, JinglePalette.FAR_SNOW)
        val minuteAngle = time.minute / 60.0 * PI * 2
        val hourAngle = ((time.hour % 12) + time.minute / 60.0) / 12 * PI * 2
        for (d in 1..2) px(cx + jsRound(sin(minuteAngle) * d), cy + jsRound(-cos(minuteAngle) * d).coerceAtLeast(-1), JinglePalette.COAL)
        px(cx + jsRound(sin(hourAngle)), cy + jsRound(-cos(hourAngle)), JinglePalette.WINDOW_FRAME)
        px(cx, cy, JinglePalette.COAL)
        // the weathervane star
        rect(cx, gy - 23, 1, 2, JinglePalette.ROOF_BROWN)
        rect(cx, gy - 26, 1, 3, JinglePalette.GOLD)
        rect(cx - 1, gy - 25, 3, 1, JinglePalette.GOLD)
        // the window, flickering while a task runs
        val lit = if (running && floor(t * 6).toInt() % 3 == 0) JinglePalette.WINDOW_FLICKER else JinglePalette.LAMP
        glowAt(wx + 5.5, gy - 6.5, if (running) 13.0 else 8.0, if (running) .38 else .22)
        rect(wx + 2, gy - 9, 7, 5, JinglePalette.LOG_CUT)
        rect(wx + 3, gy - 8, 5, 3, lit)
        rect(wx + 5, gy - 8, 1, 3, JinglePalette.LOG_CUT)
        rect(wx + 1, gy - 4, 9, 1, JinglePalette.FAR_SNOW)
        // the big double gate with a wreath
        rect(wx + 11, gy - 9, 9, 9, JinglePalette.GATE)
        rect(wx + 12, gy - 8, 3, 8, JinglePalette.ROOF_BROWN)
        rect(wx + 16, gy - 8, 3, 8, JinglePalette.ROOF_BROWN)
        px(wx + 14, gy - 4, JinglePalette.GOLD)
        rect(wx + 14, gy - 12, 3, 3, JinglePalette.FIR)
        px(wx + 15, gy - 11, JinglePalette.TRUNK)
        px(wx + 15, gy - 10, JinglePalette.SANTA_RED)
        // the hatch the chute starts from, lit while a task runs
        rect(wx + 20, gy - 11, 4, 4, JinglePalette.GATE)
        rect(wx + 21, gy - 10, 2, 2, if (running) JinglePalette.LAMP else JinglePalette.CLOTH_DARK)
    }

    private fun PixelCanvas.stable(sx: Int, gy: Int) {
        val ws = 21
        rect(sx, gy - 8, ws, 8, JinglePalette.ROOF_BROWN)
        rect(sx, gy - 6, ws, 1, JinglePalette.GATE)
        rect(sx, gy - 3, ws, 1, JinglePalette.GATE)
        for (j in 0 until 5) {
            val y = gy - 10 - j
            val inset = jsRound(j * 2.5)
            val xa = sx - 2 + inset
            val xb = sx + ws + 2 - inset
            rect(xa, y, xb - xa, 1, if (j >= 3) JinglePalette.FAR_SNOW else JinglePalette.TRUNK)
            if (j < 3) {
                px(xa, y, JinglePalette.FAR_SNOW)
                px(xb - 1, y, JinglePalette.FAR_SNOW)
            }
        }
        rect(sx - 2, gy - 9, ws + 4, 1, JinglePalette.FAR_SNOW)
        // the open stall with a reindeer looking out and nodding
        rect(sx + 7, gy - 7, 8, 7, JinglePalette.STALL)
        rect(sx + 8, gy - 2, 6, 2, JinglePalette.PALE_WOOD)
        val nod = if (sin(t * 1.3) > .6) 1 else 0
        drawReindeerHead(sx + 4, gy - 8 + nod, JinglePalette.NOSE_DARK)
        rect(sx + 7, gy - 4, 8, 1, JinglePalette.CHUTE_SHADE)
        // the hay stack
        rect(sx + ws + 1, gy - 4, 6, 4, JinglePalette.PALE_WOOD)
        rect(sx + ws + 2, gy - 5, 4, 1, JinglePalette.PALE_WOOD)
        rect(sx + ws + 2, gy - 6, 4, 1, JinglePalette.FAR_SNOW)
        rect(sx + ws + 1, gy - 2, 6, 1, JinglePalette.HAY_SHADE)
        // the fence on the left with bullfinches
        val fx = sx - 13
        for (x in fx until sx - 1 step 3) {
            rect(x, gy - 5, 1, 5, JinglePalette.TRUNK)
            px(x, gy - 6, JinglePalette.FAR_SNOW)
        }
        rect(fx, gy - 4, sx - 1 - fx, 1, JinglePalette.CHUTE_SHADE)
        rect(fx, gy - 2, sx - 1 - fx, 1, JinglePalette.CHUTE_SHADE)
        FINCH.draw(this, (fx + 2).toDouble(), (gy - 10).toDouble())
        FINCH.draw(this, (fx + 7).toDouble(), (gy - 10).toDouble(), flip = true)
    }

    /** The mockup's `grazer`: the drift's reindeer, standing, now and then its head down to the snow. */
    private fun PixelCanvas.grazer(x: Int, gy: Int) {
        val y = gy - 7
        val down = if (sin(t * .9) > -.2) 3 else 0
        rect(x + 1, y + 3, 6, 2, JinglePalette.REINDEER)
        drawReindeerHead(x, y + down, JinglePalette.NOSE_DARK)
        px(x, y + 3, JinglePalette.TAIL)
        rect(x + 1, y + 5, 1, 2, JinglePalette.STICK)
        rect(x + 6, y + 5, 1, 2, JinglePalette.STICK)
    }

    /** The mockup's `bigTree`: the village's fir, taller, with lights on its tiers, a star and gifts. */
    private fun PixelCanvas.bigTree(cx: Int, gy: Int) {
        val height = 23
        drawFirTree(cx, gy, height / 18.0)
        val top = gy - height
        var k = 0
        for (r in intArrayOf(6, 9, 12, 16, 19, 22)) {
            val half = (r / 2.5).toInt()
            for (dx in -half + 1 until half step 3) {
                k++
                var color = YardSprites.TREE_LIGHTS[k % 4]
                var on = (floor(t * 2).toInt() + k) % 3 != 0
                when (treeMode) {
                    TreeMode.OK -> {
                        color = JinglePalette.LAMP
                        on = true
                    }
                    TreeMode.FAIL -> {
                        color = JinglePalette.SIGNAL_RED
                        on = floor(t * 6).toInt() % 2 == 0
                    }
                    null -> Unit
                }
                px(cx + dx, top + r, if (on) color else JinglePalette.TREE_BULB_OFF)
            }
        }
        rect(cx, top - 3, 1, 3, JinglePalette.GOLD)
        rect(cx - 1, top - 2, 3, 1, JinglePalette.GOLD)
        if (treeMode == TreeMode.OK && floor(t * 8).toInt() % 2 != 0) {
            px(cx - 2, top - 2, JinglePalette.STAR)
            px(cx + 2, top - 2, JinglePalette.STAR)
            px(cx, top - 4, JinglePalette.STAR)
        }
        val ok = treeMode == TreeMode.OK
        glowAt(cx + .5, top - 1.5, if (ok) 11.0 else 6.0, if (ok) .45 else .25)
        gift(cx - 6, gy - 3, JinglePalette.SANTA_RED)
        gift(cx + 3, gy - 3, JinglePalette.WALL_BLUE)
        gift(cx - 2, gy - 2, JinglePalette.GIFT_PLUM)
    }

    /** Santa, his bottom row above [yb]; scratching his head with a `?` after an error. */
    private fun PixelCanvas.chief(x: Double, yb: Double, sitting: Boolean) {
        val sprite = if (sitting) YardSprites.SANTA_SITTING else SANTA
        val top = yb - sprite.h
        sprite.draw(this, x, top)
        if (scratch > 0) {
            val left = jsRound(x)
            val y = jsRound(top)
            rect(left + 7, y + 4, 1, 3, JinglePalette.SANTA_RED)
            rect(left + 6, y + 2, 1, 2, JinglePalette.SANTA_RED)
            px(left + 5, y + 1, JinglePalette.BATHER)
            question(left + 10, y - 1 - floor(t * 3).toInt() % 2)
        }
    }

    /** The `?` with its baseline at [baseline], as `fillText` puts it. */
    private fun PixelCanvas.question(x: Int, baseline: Int) {
        val rows = YardSprites.QUESTION
        val top = baseline - rows.size
        for ((j, row) in rows.withIndex()) {
            for ((i, ch) in row.withIndex()) if (ch == '#') px(x + i, top + j, JinglePalette.FAR_SNOW)
        }
    }

    private fun PixelCanvas.glowAt(cx: Double, cy: Double, r: Double, alpha: Double) {
        glow(cx, cy, r, JinglePalette.LAMP, alpha, floor(cx - r).toInt(), floor(cy - r).toInt(), (2 * r).toInt() + 2, (2 * r).toInt() + 2)
    }

    companion object {
        /** Logical pixels from the bottom of the window to the top of the strip (the mockup's clip, halved, and room for smoke). */
        const val STRIP_HEIGHT = 38

        /** How fast Santa and the elves walk, logical pixels a second. */
        private const val WALK = 15.0

        /** How high the sleigh flies, logical pixels. */
        private const val MAX_LIFT = 22.0

        /** The sleigh comes back by itself if nobody calls it back within this many seconds. */
        private const val AWAY_MAX = 40.0

        /** The mockup throws a spark behind the moving sleigh on every other browser frame. */
        private const val SPARKS_PER_SECOND = 30.0

        private val alphas = HashMap<Color, Array<Color?>>()

        /** [color] at [a] opacity, made once per level. */
        private fun alpha(color: Color, a: Double): Color {
            val level = (a * 255).roundToInt().coerceIn(0, 255)
            val cache = alphas.getOrPut(color) { arrayOfNulls(256) }
            return cache[level] ?: Color(color.red, color.green, color.blue, level).also { cache[level] = it }
        }
    }
}

// ---------- the mockup's layout and ground ----------

internal const val FIRS_L = "firsL"
internal const val STABLE = "stable"
internal const val GRAZE = "graze"
internal const val SHOP = "shop"
internal const val PARK = "park"
internal const val TREE = "tree"
internal const val MAIL = "mail"
internal const val FIRS_R = "firsR"

/** The drift's sleigh with its two reindeer, from the elf riding at its back to the lead reindeer's nose. */
private const val TEAM_LEN = 34

private class Group(val key: String, val w: Int, val opt: Int = 0)

/**
 * The mockup's `layout(x0, w)` at half size: the groups left to right with uneven gaps (their weights), the
 * optional ones dropping out of a narrow strip: the grazing reindeer first, then the stable, then the big fir,
 * then the firs on the left. Returns each group's left edge.
 */
internal fun yardLayout(x0: Double, w: Double): Map<String, Double> {
    val groups = listOf(
        Group(FIRS_L, 12), Group(STABLE, 43, opt = 2), Group(GRAZE, 13, opt = 3), Group(SHOP, 43),
        Group(PARK, TEAM_LEN + 2), Group(TREE, 20, opt = 1), Group(MAIL, 15), Group(FIRS_R, 11),
    )
    val gaps = mapOf(GRAZE to .15, STABLE to .6, SHOP to 1.0, PARK to .5, TREE to 1.5, MAIL to .35, FIRS_R to .5)
    var use = groups
    val minGap = 3
    fun need(a: List<Group>) = a.sumOf { it.w } + minGap * (a.size - 1)
    for (lvl in intArrayOf(3, 2, 1)) if (need(use) > w) use = use.filter { it.opt != lvl }
    if (need(use) > w) use = use.filter { it.key != FIRS_L }
    val spare = max(0.0, w - use.sumOf { it.w })
    val ws = use.drop(1).sumOf { gaps[it.key] ?: 1.0 }
    val layout = LinkedHashMap<String, Double>()
    var x = x0
    for ((i, g) in use.withIndex()) {
        if (i > 0) x += spare * (gaps[g.key] ?: 1.0) / ws
        layout[g.key] = x
        x += g.w
    }
    return layout
}

/** One rounded heap under a group: the mockup's `mounds`. */
private class Mound(val x: Double, val r: Double, val h: Double)

/** The mockup's `bump(x, c, w, h)`. */
private fun bump(x: Double, c: Double, w: Double, h: Double): Double {
    val d = (x - c) / w
    return if (abs(d) < 1) h * (1 - d * d) * (1 - d * d) else 0.0
}

/** Where everything stands in a strip [width] logical pixels wide; the mockup's variables in `strip()`, halved. */
internal class Geometry(val width: Int) {
    val x0 = 0.0
    val x1 = width.toDouble()
    val base = YardScene.STRIP_HEIGHT.toDouble()
    val gTop = base - 7
    val layout = yardLayout(x0, x1 - x0)
    val wx = layout.getValue(SHOP) + 1
    val bx = wx + 1
    val parkX = jsRound(layout.getValue(PARK)).toDouble()
    val chuteEnd = parkX - 2
    private val mounds: List<Mound> = buildList {
        add(Mound(wx + 12, 40.0, 3.4))
        add(Mound(x0 + 7, 20.0, 3.0))
        layout[TREE]?.let { add(Mound(it + 10, 22.0, 3.4)) }
        layout[STABLE]?.let { add(Mound(it + 25, 30.0, 3.0)) }
        layout[MAIL]?.let { add(Mound(it + 18, 15.0, 2.0)) }
    }
    val homes: List<Double> = listOf(wx + 27, layout[STABLE]?.let { it + 37 } ?: (wx - 8))
    val gy = gyAt(wx + 13)
    val gyP = gyAt(chuteEnd)
    val mx = layout.getValue(MAIL)
    val mgy = gyAt(mx + 6)

    /** The elf on the roof stands on its third step from the eaves. */
    val roofElfBottom: Int = jsRound(gy) - 17

    /** The mockup's `groundY`: the top row of the snow, from the heaps and a gentle wave. */
    fun groundY(x: Double): Double {
        var b = 0.0
        for (m in mounds) b += bump(x, m.x, m.r, m.h)
        b += sin(x / 13) * 0.8
        return gTop - max(0, jsRound(b))
    }

    /** One row under the snow's top: things standing at [x] have their bottom row on the snow's crest. */
    fun gyAt(x: Double): Double = groundY(x) + 1
}

/**
 * The mockup's `ground`, the village's: solid snow, a one-pixel white crest, shade on the steps down,
 * sparse speckles. It goes one row past the bottom, so no gap shows when the window's height isn't a whole
 * number of pixels.
 */
internal fun PixelCanvas.ground(g: Geometry) {
    val base = jsRound(g.base)
    for (x in 0 until g.width) {
        val t = jsRound(g.groundY(x.toDouble()))
        val tn = jsRound(g.groundY(x + 1.0))
        rect(x, t, 1, base - t + 1, JinglePalette.SNOW)
        px(x, t, JinglePalette.WHITE)
        if (tn > t) px(x, t + 1, JinglePalette.SNOW_SHADE)
        if (hash(x, 3) > .82) px(x, t + 3 + floor(hash(x, 4) * max(1, base - t - 4)).toInt(), JinglePalette.SNOW_SPECK)
    }
}

/** The village's fir [h] high, its foot on the snow at [x]. */
private fun PixelCanvas.fir(g: Geometry, x: Double, h: Int) = drawFirTree(jsRound(x), jsRound(g.gyAt(x)), h / 18.0)

/** The mockup's `farFir`: a dark fir on the horizon with a pale tip, [base] its lowest row plus one. */
internal fun PixelCanvas.farFir(cx: Int, base: Int, h: Int) {
    for (y in 0 until h) {
        val w = 1 + 2 * (y / 3)
        rect(cx - w / 2, base - h + y, w, 1, JinglePalette.FIR_FAR)
    }
    px(cx, base - h, JinglePalette.FAR_FIR_TIP)
}

/** A birch like the village's: a white trunk [h] high with dark marks, twigs with a bit of snow. */
internal fun PixelCanvas.birch(x: Int, gy: Int, h: Int) {
    rect(x, gy - h, 1, h, JinglePalette.BIRCH)
    for (y in gy - h + 2 until gy step 3) if (hash(x + y, 7) > .4) px(x, y, JinglePalette.BIRCH_MARK)
    for (k in 0 until 4) {
        val ty = gy - h + 2 + k * 2
        val d = if (k % 2 != 0) 1 else -1
        px(x + d, ty, JinglePalette.BIRCH_TWIG)
        px(x + d * 2, ty - 1, JinglePalette.BIRCH_TWIG)
        if (k % 2 == 0) px(x + d * 2, ty - 2, JinglePalette.SNOW)
    }
}

/** The mockup's `gift`, 3 pixels square: a box with a ribbon down the middle and a bow on top. */
internal fun PixelCanvas.gift(x: Int, y: Int, color: Color) {
    val ribbon = if (color == JinglePalette.GOLD) JinglePalette.SANTA_RED else JinglePalette.GOLD
    rect(x, y, 3, 3, color)
    rect(x + 1, y, 1, 3, ribbon)
    px(x, y - 1, ribbon)
    px(x + 2, y - 1, ribbon)
}

/**
 * The mockup's `chute` from ([xa], [ya]) to ([xb], [yb]), a column at a time: the slide, its shade
 * underneath, the snow on top and two posts down to the snow at [ground].
 */
internal fun PixelCanvas.chute(xa: Int, ya: Int, xb: Int, yb: Int, ground: Int) {
    fun yAt(x: Int) = if (xb == xa) ya else jsRound(ya + (yb - ya) * (x - xa).toDouble() / (xb - xa))
    for (k in 1 until 3) {
        val x = xa + (xb - xa) * k / 3
        val y = yAt(x)
        rect(x, y + 2, 1, ground - y - 2, JinglePalette.ROOF_BROWN)
    }
    for (x in xa..xb) {
        val y = yAt(x)
        px(x, y - 1, JinglePalette.FAR_SNOW)
        px(x, y, JinglePalette.LOG_CUT)
        px(x, y + 1, JinglePalette.CHUTE_SHADE)
    }
}

/** The mockup's `mailbox`. */
internal fun PixelCanvas.mailbox(x: Int, gy: Int) {
    rect(x + 2, gy - 6, 1, 6, JinglePalette.ROOF_BROWN)
    rect(x, gy - 10, 5, 4, JinglePalette.SANTA_RED)
    rect(x, gy - 11, 5, 1, JinglePalette.FAR_SNOW)
    rect(x + 1, gy - 9, 3, 1, JinglePalette.COAL)
    px(x + 2, gy - 7, JinglePalette.GOLD)
}

/** The mockup's `sack`: fatter with each of the 5 levels of letters. */
internal fun PixelCanvas.sack(cx: Int, gy: Int, level: Int) {
    val w = 5 + level
    val h = 4 + level
    val left = cx - w / 2
    rect(left, gy - h, w, h, JinglePalette.LOG_CUT)
    rect(left, gy - h, 1, h, JinglePalette.SACK_SHADE)
    rect(left + 1, gy - 1, w - 1, 1, JinglePalette.SACK_SHADE)
    rect(left + 1, gy - h, w - 2, 1, JinglePalette.FAR_SNOW)
    rect(cx - 1, gy - h - 2, 2, 2, JinglePalette.LOG_CUT)
    rect(cx - 2, gy - h - 1, 4, 1, JinglePalette.SANTA_RED)
    if (level > 0) {
        rect(cx - 1, gy - h - 4, 3, 2, JinglePalette.FAR_SNOW)
        px(cx, gy - h - 3, JinglePalette.SNOW_SHADE)
    }
    if (level > 2) rect(cx + 1, gy - h - 5, 2, 2, JinglePalette.LETTER_YELLOW)
}

/** The mockup's `bench`. */
internal fun PixelCanvas.bench(bx: Int, gy: Int) {
    rect(bx, gy - 3, 9, 1, JinglePalette.TRUNK)
    rect(bx, gy - 4, 9, 1, JinglePalette.FAR_SNOW)
    rect(bx, gy - 2, 1, 2, JinglePalette.ROOF_BROWN)
    rect(bx + 8, gy - 2, 1, 2, JinglePalette.ROOF_BROWN)
}

/**
 * Stars over the yard, as over the editor's village (Ivan's call): they hang there when no snow falls in the
 * window and its background is dark. [frame] is the animator's frame, they twinkle on it; [seed] places them
 * (the day's, stable all day); in the [evening] there are more of them. The moon is the village's alone (Ivan).
 */
internal class YardSky(val frame: Int, val seed: Int, val evening: Boolean)

/** The village's `sky`, as many stars per column, in the rows above the yard. */
private fun PixelCanvas.stars(width: Int, sky: YardSky) {
    val count = (if (sky.evening) 60 else 12) * width / MOCKUP_WIDTH
    for (i in 0 until count) {
        val star = i + sky.seed
        val x = (hash(star, 1) * width).toInt()
        val y = (hash(star, 2) * SKY_ROWS).toInt()
        val warm = hash(star, 3) > 0.5
        val shine = if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD
        when (starLook(star, sky.frame)) {
            StarLook.OUT -> Unit
            StarLook.DIM -> px(x, y, if (warm) JinglePalette.STAR_DIM else JinglePalette.STAR_COLD_DIM)
            StarLook.SHINE -> px(x, y, shine)
            StarLook.FLASH -> {
                px(x, y, JinglePalette.WHITE)
                px(x - 1, y, shine)
                px(x + 1, y, shine)
                px(x, y - 1, shine)
                px(x, y + 1, shine)
            }
        }
    }
}

/** Rows of the strip the stars hang in: above the roofs' snow, behind the workshop's chimney and the big fir. */
private const val SKY_ROWS = 20
