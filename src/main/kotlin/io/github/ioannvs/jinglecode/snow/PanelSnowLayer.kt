package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.overlay.ProjectPanel
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import kotlin.math.ceil

/**
 * Snow falling over the trees of the Project and Commit panels, as in the editor ([SnowField], the same
 * amount and speed), over the drift scene too, in the pixels of the frame overlay. While it falls the
 * scene drops its own few flakes. One per frame overlay; it moves on the smooth clock, see [move].
 */
internal class PanelSnowLayer : OverlayLayer {

    override val feature = JingleFeature.PANEL_SNOW

    private val projectSnow = SnowField()
    private val commitSnow = SnowField()

    /** Seconds of snowfall so far; sways the flakes. */
    private var time = 0.0

    private fun panels(layout: FrameLayout): List<Pair<ProjectPanel, SnowField>> = listOfNotNull(
        layout.projectPanel?.let { it to projectSnow },
        layout.commitPanel?.let { it to commitSnow },
    )

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> =
        panels(layout).map { (panel, _) -> panel.content }

    /**
     * Moves the snow by [dt] seconds; [pixelUser] is the logical pixel as the canvas rounds it on this screen.
     * Returns whether any flake reached another pixel.
     */
    fun move(layout: FrameLayout, pixelUser: Float, dt: Double, amount: SnowAmount, speed: SnowSpeed): Boolean {
        time += dt
        var moved = false
        for ((panel, field) in panels(layout)) {
            val width = ceil(panel.content.width / pixelUser).toInt()
            val height = ceil(panel.content.height / pixelUser).toInt()
            if (field.step(width, height, dt, time, amount.areaPerFlake, speed.factor)) moved = true
        }
        return moved
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        // White snow is lost on a light tree: there it is ice blue, as in the editor.
        val color = if (ColorUtil.isDark(UIUtil.getTreeBackground())) JinglePalette.SNOW else JinglePalette.ICE
        for ((panel, field) in panels(layout)) {
            if (field.flakes.isEmpty()) continue
            val g2 = g.create() as Graphics2D
            try {
                val body = panel.body
                val arc = panel.arc.toFloat()
                // In the island's rounded corners, not on its border.
                g2.clip(RoundRectangle2D.Float(body.x.toFloat(), body.y.toFloat(), body.width.toFloat(), body.height.toFloat(), arc, arc))
                g2.clip(panel.content)
                PixelCanvas.paint(g2, panel.content.x, panel.content.y, magnify = scale.magnify) {
                    for (flake in field.flakes) {
                        px(PixelCanvas.jsRound(flake.x), PixelCanvas.jsRound(flake.y), color)
                    }
                }
            }
            finally {
                g2.dispose()
            }
        }
    }
}
