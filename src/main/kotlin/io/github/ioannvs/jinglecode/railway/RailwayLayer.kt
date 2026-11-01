package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * The railway strips as a layer of the frame overlay, under the snow of the bottom windows, as the yard is under
 * the snow of the right ones. [RailwayStrips] keeps them, moves them and repaints them on its own clock.
 */
internal object RailwayLayer : OverlayLayer {

    override val feature = JingleFeature.RAILWAY

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> =
        RailwayStrips.getInstance(project).stripRects()

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) =
        RailwayStrips.getInstance(project).paintStrips(g, overlay = true)
}
