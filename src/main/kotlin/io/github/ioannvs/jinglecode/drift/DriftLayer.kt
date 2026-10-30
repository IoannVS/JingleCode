package io.github.ioannvs.jinglecode.drift

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.sprites.seamRows
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import kotlin.math.min

/**
 * The drift with the snowman, the mailbox and the gifts at the bottom of the Project panel, from
 * edge to edge, in its rounded bottom corners. Always there (Ivan's call), also over the last rows
 * of a long tree. While the sleigh comes for the gifts, the scene reaches higher, into the sky above it.
 */
internal object DriftLayer : OverlayLayer {

    override val feature = JingleFeature.DRIFT

    /** Keeps the tick an Int. */
    private const val TICK_WRAP = 1_201_200L

    /** How far above the scene the sleigh flies, logical pixels. */
    private const val SKY_ROWS = 18

    /** Where the scene goes, root pane coordinates, or `null` without a Project panel. */
    internal fun scene(layout: FrameLayout, scale: OverlayScale): Rectangle? {
        val panel = layout.projectPanel ?: return null
        val bottom = min(panel.content.y + panel.content.height, panel.body.y + panel.body.height)
        val height = scale.user(SCENE_ROWS)
        return Rectangle(panel.content.x, bottom - height, panel.content.width, height)
    }

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val scene = scene(layout, scale) ?: return emptyList()
        if (GiftPile.getInstance(project).delivery() == null) return listOf(scene)
        val sky = scale.user(SKY_ROWS)
        val top = maxOf(layout.projectPanel!!.content.y, scene.y - sky)
        return listOf(Rectangle(scene.x, top, scene.width, scene.y + scene.height - top))
    }

    override fun isAnimating(project: Project): Boolean {
        val pile = GiftPile.getInstance(project)
        return pile.delivery() != null || pile.dropAgeMs() < 700
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val panel = layout.projectPanel ?: return
        val scene = scene(layout, scale) ?: return
        val body = panel.body
        g.clip(RoundRectangle2D.Float(body.x.toFloat(), body.y.toFloat(), body.width.toFloat(), body.height.toFloat(), panel.arc.toFloat(), panel.arc.toFloat()))
        val f = (tick % JingleAnimator.FRAMES).toInt()
        val t = (tick % TICK_WRAP).toInt()
        val pile = GiftPile.getInstance(project)
        val view = PileView(pile.count, pile.dropAgeMs(), pile.delivery())
        // The pixel size as the canvas rounds it on this screen, so the seam with the village is exact.
        val sysScale = maxOf(g.transform.scaleX, g.transform.scaleY).toFloat().takeIf { it > 0 } ?: 1f
        val pixelUser = PixelCanvas.pixelSize(sysScale, magnify = scale.magnify) / sysScale
        PixelCanvas.paint(g, scene.x, scene.y, magnify = scale.magnify) {
            // One column more than fits, so the drift reaches the right wall; the clip cuts the rest.
            drawProjectScene(toLogical(scene.width) + 1, f, t, view, rightEdge = seamRows(pixelUser))
        }
    }
}
