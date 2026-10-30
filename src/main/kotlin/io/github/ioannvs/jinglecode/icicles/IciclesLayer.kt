package io.github.ioannvs.jinglecode.icicles

import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.garland.GARLAND_REACH
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.floor

/**
 * Icicles hanging from the toolbar along the top edge of the panels, in the strip of the top
 * islands' rounded border. Not over the gaps between panels: the garland hangs there.
 */
internal object IciclesLayer : OverlayLayer {

    override val feature = JingleFeature.ICICLES

    /** Divisible by 3, the glint period. */
    private const val TICK_WRAP = 1_201_200L

    override fun dirtyRects(layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val height = scale.user(ICICLE_MAX + 1)
        return layout.top.map { Rectangle(it.x, it.y, it.width, height) }
    }

    override fun paint(g: Graphics2D, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val t = (tick % TICK_WRAP).toInt()
        for (edge in layout.top) {
            // Gap lines that start at this edge, in logical pixels from its left end.
            val gaps = layout.vertical
                .filter { it.y <= edge.y + scale.user(ICICLE_MAX) && it.x in edge.x..edge.x + edge.width }
                .map { floor((it.x - edge.x) / scale.pixelUser).toInt() }
            PixelCanvas.paint(g, edge.x, edge.y, magnify = scale.magnify) {
                drawIcicles(toLogical(edge.width), t) { x -> gaps.any { x in it - GARLAND_REACH..it + GARLAND_REACH } }
            }
        }
    }
}
