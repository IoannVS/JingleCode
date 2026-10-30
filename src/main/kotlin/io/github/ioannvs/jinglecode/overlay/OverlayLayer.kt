package io.github.ioannvs.jinglecode.overlay

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.ceil

/**
 * One decoration of the [FrameOverlay]: the garland, the icicles. It says where it paints,
 * and [FrameOverlay] clips it to that, minus popups, and repaints exactly there on every frame.
 */
internal interface OverlayLayer {
    val feature: JingleFeature

    /** Where this layer may paint for [layout], root pane coordinates. Also what is repainted every frame. */
    fun dirtyRects(layout: FrameLayout, scale: OverlayScale): List<Rectangle>

    /** Paints frame [tick] (see `JingleAnimator`); [g] is clipped to [dirtyRects] minus popups. */
    fun paint(g: Graphics2D, layout: FrameLayout, scale: OverlayScale, tick: Long)
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
