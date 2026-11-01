package io.github.ioannvs.jinglecode.fireworks

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import io.github.ioannvs.jinglecode.icicles.ICICLE_ROOT
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The empty sky of the main toolbar, where the fireworks go: [height] user pixels from the top of the
 * window down to the icicles' roots, and the [gaps] between its buttons and widgets, root pane x.
 */
internal class Sky(val height: Int, val fit: BurstFit, val gaps: List<IntRange>)

/**
 * Finds the gaps between the main toolbar's buttons and widgets by walking the components over it:
 * every showing component that has no showing children of its own takes its width, except plain
 * panels and fillers, which are just room. Leans on how the toolbar is laid out, so the fireworks
 * call it through `FeatureGate.guard`: if it breaks, only the fireworks go off.
 */
internal object ToolbarSky {
    /** Logical pixels kept clear around the toolbar's things. */
    private const val PADDING = 2

    fun find(project: Project, layout: FrameLayout, scale: OverlayScale): Sky? {
        val root = WindowManager.getInstance().getFrame(project)?.rootPane ?: return null
        // The toolbar ends where the top islands begin; the icicles' roots grow into its bottom.
        val islandsTop = layout.top.minOfOrNull { it.y } ?: return null
        val height = islandsTop - scale.user(ICICLE_ROOT + 1)
        val fit = BurstFit.of((height / scale.pixelUser).toInt()) ?: return null
        val sky = Rectangle(0, 0, root.width, height)
        val taken = ArrayList<IntRange>()
        collect(root.layeredPane, root, sky, taken)
        val gaps = gaps(root.width, taken, scale.user(PADDING))
        return if (gaps.isEmpty()) null else Sky(height, fit, gaps)
    }

    private fun collect(parent: Container, root: JRootPane, sky: Rectangle, taken: MutableList<IntRange>) {
        for (child in parent.components) {
            if (!child.isShowing || child.width <= 0 || child.height <= 0) continue
            val bounds = SwingUtilities.convertRectangle(parent, child.bounds, root)
            if (!bounds.intersects(sky)) continue
            if (child is Container && child.components.any(Component::isShowing)) collect(child, root, sky, taken)
            else if (!isRoom(child, bounds, sky)) taken += bounds.x until bounds.x + bounds.width
        }
    }

    private fun isRoom(component: Component, bounds: Rectangle, sky: Rectangle): Boolean =
        component.javaClass == JPanel::class.java || component is Box.Filler || bounds.width > sky.width / 2

    /** The free parts of `0 until width`, with [taken] kept [padding] clear. */
    fun gaps(width: Int, taken: List<IntRange>, padding: Int): List<IntRange> {
        val gaps = ArrayList<IntRange>()
        var free = 0
        for (range in taken.sortedBy { it.first }) {
            val start = range.first - padding
            if (start > free) gaps += free until start
            free = maxOf(free, range.last + 1 + padding)
        }
        if (free < width) gaps += free until width
        return gaps
    }
}

/**
 * Plans a show of fireworks: every show is different, from its `show` number. Five to eight bursts of
 * different kinds, colors and sizes, at different heights, in random places of the [Sky.gaps], after
 * uneven pauses. Bursts burning at the same time never overlap; one that is out leaves its place to the
 * next. If a burst finds no place, it gets smaller, and at the smallest size it goes into the widest gap
 * anyway, so a show always has all its bursts. `pixelUser` is user pixels per logical pixel.
 *
 * Pure: the same show in the same sky is the same plan.
 */
internal object FireworksPlan {
    const val MIN_BURSTS = 5
    const val MAX_BURSTS = 8

    /** Pauses between rockets, ms. */
    const val MIN_PAUSE_MS = 120L
    const val MAX_PAUSE_MS = 450L

    /** Tries for a free place for each burst; every third one makes it smaller. */
    private const val TRIES = 15

    /** The longest a show can take. */
    const val LONGEST_MS = BURST_MS + (MAX_BURSTS - 1) * MAX_PAUSE_MS

    /** Half the width of a burst of [radius], user pixels: the ring, its crossed tips and a pixel of air. */
    fun half(radius: Int, pixelUser: Float): Int = ((radius + 2) * pixelUser).roundToInt()

    fun plan(sky: Sky, pixelUser: Float, show: Int): List<Burst> {
        var draw = 0
        // Small numbers for the hash, which is a sine.
        val base = 50 + (show % 400) * 7
        fun random(): Double = hash(base, 100 + draw++)
        fun length(gap: IntRange) = gap.last - gap.first + 1

        // Only the gaps where at least the smallest burst fits.
        val gaps = sky.gaps.filter { length(it) > 2 * half(MIN_RADIUS, pixelUser) }
        if (gaps.isEmpty()) return emptyList()
        val free = gaps.sumOf(::length)
        val rows = sky.fit.rows

        /** A center for a burst [half] wide in a random place, wider gaps more likely; `null` if it doesn't fit there. */
        fun place(half: Int, gap: IntRange? = null): Int? {
            var at = (random() * free).toInt()
            val chosen = gap ?: gaps.firstOrNull { at -= length(it); at < 0 } ?: gaps.last()
            val room = length(chosen) - 2 * half
            if (room <= 0) return null
            return chosen.first + half + (random() * room).toInt()
        }

        val wanted = MIN_BURSTS + floor(random() * (MAX_BURSTS - MIN_BURSTS + 1)).toInt()
        val bursts = ArrayList<Burst>()
        var delay = 0L
        for (k in 0 until wanted) {
            val kind = BurstKind.entries[floor(random() * BurstKind.entries.size).toInt()]
            val largest = (MIN_RADIUS..sky.fit.radius).lastOrNull { burstHeight(it, kind) <= rows } ?: MIN_RADIUS
            var radius = MIN_RADIUS + ((largest - MIN_RADIUS) * random()).roundToInt()
            val colors = floor(random() * BURST_COLORS.size).toInt()
            val height = random()
            val burning = bursts.filter { delay - it.delayMs < BURST_MS }
            var x: Int? = null
            for (attempt in 0 until TRIES) {
                if (attempt > 0 && attempt % 3 == 0 && radius > MIN_RADIUS) radius--
                val half = half(radius, pixelUser)
                val at = place(half) ?: continue
                if (burning.none { abs(it.x - at) < half + half(it.radius, pixelUser) }) {
                    x = at
                    break
                }
            }
            if (x == null) {
                radius = MIN_RADIUS
                x = place(half(radius, pixelUser), gaps.maxBy(::length))!!
            }
            // The ring droops, so the center may sit anywhere that keeps all of it in the sky.
            val lowest = rows - burstHeight(radius, kind) + radius
            val centerY = radius + floor(height * (lowest - radius + 1)).toInt()
            bursts += Burst(x, centerY, radius, delay, kind, colors, seed = base + k)
            delay += MIN_PAUSE_MS + (random() * (MAX_PAUSE_MS - MIN_PAUSE_MS)).toLong()
        }
        return bursts
    }
}
