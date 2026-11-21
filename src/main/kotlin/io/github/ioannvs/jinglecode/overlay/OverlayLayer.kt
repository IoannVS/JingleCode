package io.github.ioannvs.jinglecode.overlay

import com.intellij.openapi.project.Project
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.ceil

/**
 * One decoration of the [FrameOverlay]: the garland, the icicles, the drift. It says where it paints,
 * and [FrameOverlay] clips it to that, minus popups, and repaints exactly there on every frame
 * (unless it [movesItself]).
 */
internal interface OverlayLayer {
    val feature: JingleFeature

    /** Where this layer may paint for [layout], root pane coordinates. Also what is repainted every frame. */
    fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle>

    /**
     * Whether something moves continuously now (a sleigh flying, a gift falling): then the overlay
     * repaints on the smooth motion clock, not only on frames.
     */
    fun isAnimating(project: Project): Boolean = false

    /**
     * Whether the layer repaints itself on its own motion clock (the panel snow, Santa's yard, the railway):
     * then frames repaint it only where its [dirtyRects] moved, not all of it again.
     */
    val movesItself: Boolean get() = false

    /**
     * Frames with the same key look the same: a frame repaints the layer only when its key changes. Every strip
     * over the editor makes it lay out all the lines it crosses again, so a garland that changes every second
     * frame is not repainted on the others.
     */
    fun frameKey(project: Project, tick: Long): Long = tick

    /** Paints frame [tick] (see `JingleAnimator`); [g] is clipped to [dirtyRects] minus popups. */
    fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long)
}

/**
 * Pixel size of the frame overlay, the same for all its layers: the garland size setting against
 * the UI font, like the mockup (2 px pixels next to a 12 px font).
 */
internal class OverlayScale(
    /** User pixels per logical pixel. */
    val pixelUser: Float,
) {
    /** [pixelUser] as the magnification `PixelCanvas` expects. */
    val magnify: Float get() = pixelUser / JBUIScale.scale(1f)

    /** [logical] logical pixels in user pixels, rounded up. */
    fun user(logical: Int): Int = ceil(logical * pixelUser).toInt()

    companion object {
        fun current(): OverlayScale =
            OverlayScale(JingleCodeSettings.getInstance().state.garlandSize.pixelUser(JBFont.label().size2D))
    }
}
