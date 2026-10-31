package io.github.ioannvs.jinglecode.drift

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.overlay.ProjectPanel
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
 *
 * The same scene stands in the Commit panel, at the bottom of the tree of changes, right above the
 * splitter to the commit message. There it doesn't meet the editor's village, so no seam and no fir tree,
 * and its own bottom corners are rounded like the island's, so it reads as resting on the splitter.
 */
internal object DriftLayer : OverlayLayer {

    override val feature = JingleFeature.DRIFT

    /** Keeps the tick an Int. */
    private const val TICK_WRAP = 1_201_200L

    /** How far above the scene the sleigh flies, logical pixels. */
    private const val SKY_ROWS = 18

    /** A panel the scene stands in, and whether its snow goes on into the village at its right wall. */
    private class Place(val panel: ProjectPanel, val seam: Boolean)

    private fun places(layout: FrameLayout): List<Place> = listOfNotNull(
        layout.projectPanel?.let { Place(it, seam = true) },
        layout.commitPanel?.let { Place(it, seam = false) },
    )

    /** Where the scene goes in [panel], root pane coordinates: at the bottom of its tree. */
    internal fun scene(panel: ProjectPanel, scale: OverlayScale): Rectangle {
        val bottom = min(panel.content.y + panel.content.height, panel.body.y + panel.body.height)
        val height = scale.user(SCENE_ROWS)
        return Rectangle(panel.content.x, bottom - height, panel.content.width, height)
    }

    /** Where the scene goes in the Project panel, or `null` without one. */
    internal fun scene(layout: FrameLayout, scale: OverlayScale): Rectangle? = layout.projectPanel?.let { scene(it, scale) }

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val flying = GiftPile.getInstance(project).delivery() != null
        return places(layout).map { place ->
            val scene = scene(place.panel, scale)
            if (!flying) return@map scene
            val top = maxOf(place.panel.content.y, scene.y - scale.user(SKY_ROWS))
            Rectangle(scene.x, top, scene.width, scene.y + scene.height - top)
        }
    }

    private fun rounded(r: Rectangle, arc: Int) =
        RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), arc.toFloat(), arc.toFloat())

    override fun isAnimating(project: Project): Boolean {
        val pile = GiftPile.getInstance(project)
        return pile.delivery() != null || pile.dropAgeMs() < 700
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val f = (tick % JingleAnimator.FRAMES).toInt()
        val t = (tick % TICK_WRAP).toInt()
        val pile = GiftPile.getInstance(project)
        val view = PileView(pile.count, pile.dropAgeMs(), pile.delivery())
        // The pixel size as the canvas rounds it on this screen, so the seam with the village is exact.
        val sysScale = maxOf(g.transform.scaleX, g.transform.scaleY).toFloat().takeIf { it > 0 } ?: 1f
        val pixelUser = PixelCanvas.pixelSize(sysScale, magnify = scale.magnify) / sysScale
        // While the panel snow falls the scene drops its own few flakes.
        val sharedSnow = FeatureGate.getInstance().isActive(JingleFeature.PANEL_SNOW)
        for (place in places(layout)) {
            val panel = place.panel
            val scene = scene(panel, scale)
            val g2 = g.create() as Graphics2D
            try {
                g2.clip(rounded(if (place.seam) panel.body else Rectangle(scene.x, panel.body.y, scene.width, scene.y + scene.height - panel.body.y), panel.arc))
                // Not below the tree: in the Commit panel the commit message is there.
                g2.clip(panel.content)
                PixelCanvas.paint(g2, scene.x, scene.y, magnify = scale.magnify) {
                    // One column more than fits, so the drift reaches the right wall; the clip cuts the rest.
                    drawProjectScene(toLogical(scene.width) + 1, f, t, view, rightEdge = if (place.seam) seamRows(pixelUser) else null, flakes = !sharedSnow)
                }
            }
            finally {
                g2.dispose()
            }
        }
    }
}
