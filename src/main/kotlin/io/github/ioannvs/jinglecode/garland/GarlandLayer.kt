package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * The garland in the gaps between panels and over the status bar. Bulbs may lie on the empty
 * rounded edges of the islands, never on popups (the overlay clips them away).
 */
internal object GarlandLayer : OverlayLayer {

    override val feature = JingleFeature.GARLAND

    /** Keeps the tick an Int; divisible by every period the modes use. */
    private const val TICK_WRAP = 1_201_200L

    /** A strip along each gap line, as wide as the garland reaches to each side of it. */
    override fun dirtyRects(layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val reach = scale.user(GARLAND_REACH + 1)
        return buildList {
            layout.vertical.forEach { add(Rectangle(it.x - reach, it.y, 2 * reach, it.height)) }
            layout.horizontal.forEach { add(Rectangle(it.x, it.y - reach, it.width, 2 * reach)) }
            layout.statusBand?.let { add(Rectangle(it.x, it.y - reach, it.width, it.height + 2 * reach)) }
        }
    }

    override fun paint(g: Graphics2D, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val mode = JingleCodeSettings.getInstance().state.garlandMode
        val t = (tick % TICK_WRAP).toInt()
        val magnify = scale.magnify
        for (line in layout.vertical) {
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawVerticalGarland(toLogical(line.height), mode, t)
            }
        }
        for (line in layout.horizontal) {
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawHorizontalGarland(toLogical(line.width), mode, t)
            }
        }
        layout.statusBand?.let { band ->
            // The strip is lower than the garland: hang it from the islands' bottom edge so the bulbs end
            // right above the status bar icons.
            PixelCanvas.paint(g, band.x, band.y + band.height, magnify = magnify) {
                drawStatusBarGarland(toLogical(band.width), mode, t)
            }
        }
    }
}
