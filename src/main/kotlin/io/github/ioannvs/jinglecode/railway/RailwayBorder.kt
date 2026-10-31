package io.github.ioannvs.jinglecode.railway

import com.intellij.ui.scale.JBUIScale
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.border.Border
import kotlin.math.ceil
import kotlin.math.max

/**
 * Room for the railway at the bottom of a tool window's content: an empty bottom inset the [scene] is painted in.
 *
 * Its logical pixel is the mockup's CSS pixel, so the sprites (2 of them per sprite pixel, the mockup's `P`)
 * come out as big as the other decorations. The strip is as high as the mockup's, poles or not (Ivan's call).
 * A window lower than [MIN_WINDOW] gets no room and no strip.
 */
internal class RailwayBorder(private val component: JComponent, seed: Int) : Border {

    val scene = RailwayScene(seed)

    private var lastInset = -1

    override fun isBorderOpaque(): Boolean = false

    override fun getBorderInsets(c: Component): Insets = Insets(0, 0, inset(c), 0)

    /** Bottom inset for [c] in user pixels, 0 if it is too low for a strip. */
    private fun inset(c: Component): Int {
        val pixel = pixelUser(c)
        if (c.height < MIN_WINDOW * pixel) return 0
        return ceil(RailwayScene.STRIP_HEIGHT * pixel).toInt()
    }

    /** User pixels per logical pixel, as [PixelCanvas] rounds it on [c]'s screen. */
    private fun pixelUser(c: Component): Double {
        val sysScale = JBUIScale.sysScale(c)
        return PixelCanvas.pixelSize(sysScale, magnify = magnify()) / sysScale.toDouble()
    }

    /** The overlay's sprite pixel (the garland size setting against the UI font) is the mockup's `P`. */
    private fun magnify(): Float = OverlayScale.current().magnify / P

    /** Lays the content out again if the room it leaves has changed (size, scale, settings). */
    fun relayout() {
        val inset = inset(component)
        if (inset != lastInset) {
            lastInset = inset
            component.revalidate()
        }
        repaintStrip()
    }

    /** Moves the scene [dt] seconds on and repaints the strip, nothing else of the window. */
    fun step(dt: Double, options: RailwayOptions) {
        val inset = inset(component)
        if (inset <= 0 || !component.isShowing) return
        scene.step(dt, logicalWidth(), options)
        repaintStrip()
    }

    private fun logicalWidth(): Int = (component.width / pixelUser(component)).toInt()

    private fun repaintStrip() {
        val inset = inset(component)
        if (inset > 0) component.repaint(0, component.height - inset, component.width, inset)
    }

    /** Set by [RailwayStrips] before painting: what to show. */
    var options: RailwayOptions? = null

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val inset = inset(c)
        val options = options ?: return
        if (inset <= 0) return
        val g2 = g.create()
        try {
            // Nothing leaves the strip: smoke that rises high melts at its top edge.
            g2.clipRect(x, y + height - inset, width, inset)
            PixelCanvas.paint(g2, x, y + height - inset, magnify = magnify()) {
                FeatureGate.getInstance().guard(JingleFeature.RAILWAY, Unit) { scene.paint(this, toLogical(width), options) }
            }
        } finally {
            g2.dispose()
        }
    }

    /**
     * Where the flying birds may be, in [glass] coordinates: the strip and [RailwayScene.SKY] above it,
     * within the window; `null` without a strip.
     */
    fun skyRect(glass: Component): Rectangle? {
        val inset = inset(component)
        if (inset <= 0 || !component.isShowing) return null
        val sky = ceil((RailwayScene.SKY + 2 * P * 6) * pixelUser(component)).toInt()
        val top = max(0, component.height - inset - sky)
        return SwingUtilities.convertRectangle(component, Rectangle(0, top, component.width, component.height - top), glass)
    }

    /** Paints the birds in the air over the window, on [glass] (the frame's glass pane), clipped to [clip]. */
    fun paintSky(g: Graphics2D, glass: Component, clip: Shape) {
        val inset = inset(component)
        if (inset <= 0 || !component.isShowing || !scene.hasFlyingBirds()) return
        val origin = SwingUtilities.convertPoint(component, 0, component.height - inset, glass)
        val g2 = g.create() as Graphics2D
        try {
            g2.clip(clip)
            PixelCanvas.paint(g2, origin.x, origin.y, magnify = magnify()) {
                scene.paintSky(this, toLogical(component.width), toLogical(component.height - inset))
            }
        } finally {
            g2.dispose()
        }
    }

    fun dispose() {
        lastInset = -1
    }

    companion object {
        /** Lower windows get no railway (logical pixels, the whole window content). */
        const val MIN_WINDOW = 120
    }
}
