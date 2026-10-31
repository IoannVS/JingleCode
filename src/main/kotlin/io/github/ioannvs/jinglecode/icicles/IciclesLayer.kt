package io.github.ioannvs.jinglecode.icicles

import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.garland.GARLAND_REACH
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.floor

/**
 * Icicles hanging from the toolbar along the top edge of the panels, in the strip of the top
 * islands' rounded border, with their roots grown a little into the toolbar, as under the active tab.
 * Not over the gaps between panels: the garland hangs there.
 */
internal object IciclesLayer : OverlayLayer {

    override val feature = JingleFeature.ICICLES

    /** Divisible by 3, the glint period. */
    private const val TICK_WRAP = 1_201_200L

    /** How much snow and ice show through the toolbar background in the upper roots and in the ledge on the edge. */
    private const val ROOT_SHARE = 0.3
    private const val LEDGE_SHARE = 0.6

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        // One spare pixel for rounding to device pixels.
        val root = scale.user(ICICLE_ROOT + 1)
        val height = root + scale.user(ICICLE_MAX + 1)
        return layout.top.map { Rectangle(it.x, it.y - root, it.width, height) }
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val t = (tick % TICK_WRAP).toInt()
        // Above the edge the roots fade into the toolbar background, solid colors, so they grow out of it rather than glare.
        val background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)
        val root = ColorUtil.mix(background, JinglePalette.SNOW, ROOT_SHARE)
        val ledge = ColorUtil.mix(background, JinglePalette.ICE, LEDGE_SHARE)
        for (edge in layout.top) {
            // Gap lines that start at this edge, in logical pixels from its left end.
            val gaps = layout.vertical
                .filter { it.y <= edge.y + scale.user(ICICLE_MAX) && it.x in edge.x..edge.x + edge.width }
                .map { floor((it.x - edge.x) / scale.pixelUser).toInt() }
            PixelCanvas.paint(g, edge.x, edge.y, magnify = scale.magnify) {
                drawRootedIcicles(toLogical(edge.width), t, root, ledge) { x ->
                    gaps.any { x in it - GARLAND_REACH..it + GARLAND_REACH }
                }
            }
        }
    }
}
