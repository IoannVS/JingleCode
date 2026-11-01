package io.github.ioannvs.jinglecode.garland

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.FeatureGate
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
 * rounded edges of the islands, never on popups (the overlay clips them away). After a run or a build
 * the lights flash for a moment ([RunLights]): red after a failure, a golden wave after a success.
 */
internal object GarlandLayer : OverlayLayer {

    override val feature = JingleFeature.GARLAND

    /** Keeps the tick an Int; divisible by every period the modes use. */
    private const val TICK_WRAP = 1_201_200L

    /** A strip along each gap line, as wide as the garland reaches to each side of it. */
    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val reach = scale.user(GARLAND_REACH + 1)
        return buildList {
            layout.vertical.forEach { add(Rectangle(it.x - reach, it.y, 2 * reach, it.height)) }
            layout.horizontal.forEach { add(Rectangle(it.x, it.y - reach, it.width, 2 * reach)) }
            layout.statusBand?.let { add(Rectangle(it.x, it.y - reach, it.width, it.height + 2 * reach)) }
        }
    }

    override fun isAnimating(project: Project): Boolean = reacting() && RunLights.getInstance(project).isWaving

    private fun reacting() = FeatureGate.getInstance().isActive(JingleFeature.RUN_REACTIONS)

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val mode = JingleCodeSettings.getInstance().state.garlandMode
        val t = (tick % TICK_WRAP).toInt()
        val magnify = scale.magnify
        val lights = if (reacting()) RunLights.getInstance(project) else null
        // Each line gets its own salt, so neighbouring lines don't blink alike.
        var salt = 0
        for (line in layout.vertical) {
            val flash = lights?.flash(salt++)
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawVerticalGarland(toLogical(line.height), mode, t, flash)
            }
        }
        for (line in layout.horizontal) {
            val flash = lights?.flash(salt++)
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawHorizontalGarland(toLogical(line.width), mode, t, flash)
            }
        }
        layout.statusBand?.let { band ->
            val flash = lights?.flash(salt)
            // The strip is lower than the garland: hang it from the islands' bottom edge so the bulbs end
            // right above the status bar icons.
            PixelCanvas.paint(g, band.x, band.y + band.height, magnify = magnify) {
                drawStatusBarGarland(toLogical(band.width), mode, t, flash)
            }
        }
    }
}
