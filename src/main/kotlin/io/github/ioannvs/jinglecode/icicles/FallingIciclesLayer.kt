package io.github.ioannvs.jinglecode.icicles

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.floor

/**
 * The icicles that broke off after a failed run, on their way down from under the toolbar and the
 * active tab to the bottom of the window, and their shards there. Over everything, the code too, for
 * about a second (Ivan's call). The icicles they broke off from leave their places empty meanwhile.
 */
internal object FallingIciclesLayer : OverlayLayer {

    override val feature = JingleFeature.RUN_REACTIONS

    /** The rows whose icicles are showing, so they can fall. */
    private fun rows(project: Project, layout: FrameLayout, scale: OverlayScale): List<IcicleRow> {
        val gate = FeatureGate.getInstance()
        return buildList {
            if (gate.isActive(JingleFeature.ICICLES)) addAll(IciclesLayer.rows(layout, scale))
            if (gate.isActive(JingleFeature.TAB_ICICLES)) gate.guard(JingleFeature.TAB_ICICLES, null) { TabIciclesLayer.row(project) }?.let(::add)
        }
    }

    override fun isAnimating(project: Project): Boolean = IcicleFalls.getInstance(project).current().isNotEmpty()

    /**
     * From the highest icicle in the air down to the bottom of the window, row by row, or just the shards
     * at the bottom. Going down, each frame's area lies inside the last one's, so the overlay repaints one.
     */
    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val falls = IcicleFalls.getInstance(project).current()
        if (falls.isEmpty()) return emptyList()
        // Close enough to what the canvas rounds to; a pixel of margin covers the rest.
        val pixel = scale.pixelUser
        val margin = scale.user(IcicleFall.SHARD_SPREAD + 1)
        return rows(project, layout, scale).mapNotNull { row ->
            var highest: Int? = null
            var shattering = false
            forEachIcicle(row, floor(row.width / pixel).toInt()) { x, length ->
                for (fall in falls) {
                    val drop = IcicleFall.drop(x + row.salt, length, floor((layout.bottom - row.y) / pixel).toInt(), fall.ageMs, fall.seed, row.share) ?: continue
                    drop.fallen?.let { highest = minOf(highest ?: it, it) }
                    if (drop.shattered != null) shattering = true
                }
            }
            val top = highest?.let { row.y + floor(it * pixel).toInt() - scale.user(1) }
                ?: if (shattering) layout.bottom - scale.user(IcicleFall.SHARD_HEIGHT + 2) else return@mapNotNull null
            Rectangle(row.x - margin, top, row.width + 2 * margin, layout.bottom - top)
        }
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val falls = IcicleFalls.getInstance(project).current()
        if (falls.isEmpty()) return
        for (row in rows(project, layout, scale)) {
            PixelCanvas.paint(g, row.x, row.y, magnify = scale.magnify) {
                val bottom = toLogical(layout.bottom - row.y)
                forEachIcicle(row, toLogical(row.width)) { x, length ->
                    for (fall in falls) {
                        val drop = IcicleFall.drop(x + row.salt, length, bottom, fall.ageMs, fall.seed, row.share) ?: continue
                        drop.fallen?.let { drawFallingIcicle(x, length, it) }
                        drop.shattered?.let { drawShards(x, bottom, length, it, fall.seed) }
                    }
                }
            }
        }
    }

    private inline fun forEachIcicle(row: IcicleRow, columns: Int, action: (x: Int, length: Int) -> Unit) {
        var x = 1
        while (x < columns) {
            if (!row.skip(x)) icicleLength(x)?.let { action(x, it) }
            x += 2
        }
    }
}
