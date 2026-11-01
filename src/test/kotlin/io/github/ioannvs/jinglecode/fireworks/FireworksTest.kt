package io.github.ioannvs.jinglecode.fireworks

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class FireworksTest {

    @Test
    fun `a tall sky gets the mockup size, a low one smaller bursts, a tiny one none`() {
        assertEquals(MOCKUP_RADIUS, BurstFit.of(30)!!.radius)
        assertTrue(BurstFit.of(12)!!.radius < MOCKUP_RADIUS)
        assertNull(BurstFit.of(7))
    }

    /** Mockup `drawBurst`, run as is. */
    private fun mockup(cx: Int, cy: Int, a: Double, seed: Int): Map<Pair<Int, Int>, Color> {
        val out = HashMap<Pair<Int, Int>, Color>()
        fun px(x: Int, y: Int, c: Color) { out[x to y] = c }
        if (a !in 0.0..1400.0) return out
        if (a < 300) { val y = PixelCanvas.jsRound(cy + (1 - a / 300) * 10); px(cx, y, Color(0xfff7d1)); px(cx, y + 1, Color(0xff9f2e)); return out }
        val r = min(8.0, (a - 300) / 70)
        for (k in 0 until 12) {
            if (a > 1050 && hash(k, kotlin.math.floor(a / 60).toInt()) > 0.5) continue
            val ang = k / 12.0 * PI * 2
            px(cx + PixelCanvas.jsRound(cos(ang) * r), cy + PixelCanvas.jsRound(sin(ang) * r + r * r * 0.04),
                listOf(JinglePalette.BULBS[(k + seed) % 4], Color(0xfff7d1), Color(0xffd75e))[k % 3])
        }
        return out
    }

    private fun painted(burst: Burst, a: Long, width: Int = 40, rows: Int = 30, imageRows: Int = rows): Map<Pair<Int, Int>, Color> {
        val image = BufferedImage(width, imageRows, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        PixelCanvas.paint(g, 0, 0) { drawBurst(width / 2, burst, a, rows) }
        g.dispose()
        val out = HashMap<Pair<Int, Int>, Color>()
        for (x in 0 until width) for (y in 0 until imageRows) {
            val argb = image.getRGB(x, y)
            if (argb ushr 24 != 0) out[x to y] = Color(argb and 0xffffff)
        }
        return out
    }

    @Test
    fun `the ring in the mockup colors is the mockup burst`() {
        val ring = Burst(0, centerY = 9, radius = 8, delayMs = 0, kind = BurstKind.RING, colors = 0, seed = 2)
        for (a in listOf(0L, 150L, 299L, 300L, 500L, 860L, 1100L, 1300L, 1400L, 1401L)) {
            assertEquals("a = $a", mockup(20, 9, a.toDouble(), 2), painted(ring, a))
        }
    }

    @Test
    fun `every kind stays inside the sky it was planned for`() {
        for (kind in BurstKind.entries) for (rows in 9..24) {
            val fit = BurstFit.of(rows) ?: continue
            val radius = (MIN_RADIUS..fit.radius).last { burstHeight(it, kind) <= rows }
            val lowest = rows - burstHeight(radius, kind) + radius
            for (centerY in radius..lowest) {
                val burst = Burst(0, centerY, radius, 0, kind, colors = 3, seed = 7)
                for (a in 0L..BURST_MS step 20) {
                    val pixels = painted(burst, a, rows = rows, imageRows = rows + 10).keys
                    assertTrue("$kind rows $rows y $centerY a $a", pixels.all { (_, y) -> y in 0 until rows })
                }
            }
        }
    }

    private val sky = Sky(height = 40, fit = BurstFit.of(16)!!, gaps = listOf(30 until 90, 200 until 700, 760 until 900))

    @Test
    fun `shows differ, and their bursts stay in the gaps and never overlap while they burn`() {
        val plans = (1..40).map { FireworksPlan.plan(sky, 2.5f, it) }
        for (plan in plans) {
            assertTrue(plan.size in FireworksPlan.MIN_BURSTS..FireworksPlan.MAX_BURSTS)
            for (burst in plan) {
                val half = FireworksPlan.half(burst.radius, 2.5f)
                assertTrue(sky.gaps.any { burst.x - half >= it.first && burst.x + half <= it.last })
            }
            for ((a, b) in plan.zipWithNext()) {
                assertTrue(b.delayMs - a.delayMs in FireworksPlan.MIN_PAUSE_MS..FireworksPlan.MAX_PAUSE_MS)
            }
            for (a in plan) for (b in plan) {
                if (a === b || abs(a.delayMs - b.delayMs) >= BURST_MS) continue
                assertTrue(abs(a.x - b.x) >= FireworksPlan.half(a.radius, 2.5f) + FireworksPlan.half(b.radius, 2.5f))
            }
            assertTrue(plan.last().delayMs + BURST_MS <= FireworksPlan.LONGEST_MS)
        }
        // Different every time: counts, places, kinds, colors, sizes, heights and pauses.
        assertTrue(plans.map { it.size }.toSet().size >= 3)
        assertTrue(plans.map { p -> p.map { it.x } }.toSet().size == plans.size)
        assertEquals(BurstKind.entries.toSet(), plans.flatten().map { it.kind }.toSet())
        assertTrue(plans.flatten().map { it.colors }.toSet().size >= 4)
        assertTrue(plans.flatten().map { it.radius }.toSet().size >= 3)
        assertTrue(plans.flatten().map { it.centerY }.toSet().size >= 3)
        assertTrue(plans.flatMap { p -> p.zipWithNext { a, b -> b.delayMs - a.delayMs } }.toSet().size >= 10)
        // But the same show is planned the same way.
        assertEquals(FireworksPlan.plan(sky, 2.5f, 5), FireworksPlan.plan(sky, 2.5f, 5))
    }

    @Test
    fun `a cramped toolbar still gets at least five bursts, and one with no room none`() {
        val cramped = Sky(height = 40, fit = BurstFit.of(16)!!, gaps = listOf(10 until 60, 300 until 345))
        for (show in 1..40) {
            val plan = FireworksPlan.plan(cramped, 2.5f, show)
            assertTrue(plan.size >= FireworksPlan.MIN_BURSTS)
            for (burst in plan) {
                val half = FireworksPlan.half(burst.radius, 2.5f)
                assertTrue(cramped.gaps.any { burst.x - half >= it.first && burst.x + half <= it.last })
            }
        }
        val none = Sky(height = 40, fit = BurstFit.of(16)!!, gaps = listOf(10 until 30))
        assertEquals(emptyList<Burst>(), FireworksPlan.plan(none, 2.5f, 1))
    }

    @Test
    fun `gaps leave the toolbar's things clear`() {
        assertEquals(listOf(44 until 66, 104 until 396, 484 until 500),
            ToolbarSky.gaps(500, listOf(0 until 40, 70 until 100, 400 until 480), padding = 4))
    }
}
