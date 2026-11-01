package io.github.ioannvs.jinglecode.railway

import com.intellij.ui.scale.JBUIScale
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.border.Border
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Room for the railway at the bottom of a tool window's content: an empty bottom inset under the lower part of the
 * [scene], from the middle of the poles down ([RailwayScene.CUT]); the part above lies over the window's last rows.
 *
 * The strip itself is painted on the glass pane ([paint]), like Santa's yard: from edge to edge of the window's
 * island, in its rounded bottom corners, standing on the bottom of the island's body ([place]). Its pixel is the
 * overlay's, the yard's and the drift's: one pixel and one style along the line.
 * A window lower than [MIN_WINDOW] gets no room and no strip. ho ho ho
 */
internal class RailwayBorder(private val component: JComponent, seed: Int) : Border {

    val scene = RailwayScene(seed)

    /** The island's body around the window and how round its corners are, [component] coordinates. */
    private class Place(val body: Rectangle, val arc: Int)

    private var place: Place? = null

    private var lastInset = -1

    override fun isBorderOpaque(): Boolean = false

    // inset() is already in user pixels: JBUI.insets would scale it a second time
    @Suppress("UseDPIAwareInsets")
    override fun getBorderInsets(c: Component): Insets = Insets(0, 0, inset(), 0)

    /** The island's body in [component] coordinates, or the component itself without the Islands UI. */
    private fun body(): Rectangle = place?.body ?: Rectangle(0, 0, component.width, component.height)

    /** Where the strip stands, [component] coordinates: the bottom of the island's body if the content reaches it. */
    private fun ground(): Int = FrameGeometry.ground(Rectangle(0, 0, component.width, component.height), place?.body)

    /** Bottom inset in user pixels, 0 if the window is too low for a strip. */
    private fun inset(): Int {
        val pixel = pixelUser()
        if (component.height < MIN_WINDOW * pixel) return 0
        val room = ceil((RailwayScene.STRIP_HEIGHT - RailwayScene.CUT) * pixel).toInt()
        return max(1, room - (ground() - component.height))
    }

    /** The strip, [component] coordinates, or `null` without one. */
    private fun strip(): Rectangle? {
        if (inset() <= 0 || !component.isShowing) return null
        val body = body()
        val height = ceil(RailwayScene.STRIP_HEIGHT * pixelUser()).toInt()
        val bottom = ground()
        return Rectangle(body.x, bottom - height, body.width, height)
    }

    /** User pixels per logical pixel, as [PixelCanvas] rounds it on [component]'s screen. */
    private fun pixelUser(): Double {
        val sysScale = JBUIScale.sysScale(component)
        return PixelCanvas.pixelSize(sysScale, magnify = magnify()) / sysScale.toDouble()
    }

    /** The overlay's pixel (the garland size setting against the UI font), as the yard's. */
    private fun magnify(): Float = OverlayScale.current().magnify

    /** One column more than fits, so the snow reaches the right wall; the clip cuts the rest. */
    private fun logicalWidth(): Int = floor(body().width / pixelUser()).toInt() + 1

    /**
     * Takes the island's [body] (root pane coordinates of [root], `null` without the Islands UI) and its [arc],
     * and lays the content out again if the room it leaves has changed (size, scale, settings).
     */
    fun relayout(root: Component?, body: Rectangle?, arc: Int) {
        place = if (root != null && body != null) Place(SwingUtilities.convertRectangle(root, body, component), arc) else null
        val inset = inset()
        if (inset != lastInset) {
            lastInset = inset
            component.revalidate()
        }
    }

    /** Moves the scene [dt] seconds on. */
    fun step(dt: Double, options: RailwayOptions) {
        if (strip() == null) return
        scene.step(dt, logicalWidth(), options)
    }

    /** Set by [RailwayStrips] before painting: what to show. */
    var options: RailwayOptions? = null

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) = Unit

    /**
     * What [paint] covers, in [glass] coordinates: the strip and, if [sky], [RailwayScene.SKY] above it for the
     * birds; without [sky], only the strip's lower part, under the window's text. `null` without a strip.
     */
    fun repaintRect(glass: Component, sky: Boolean): Rectangle? {
        val strip = strip() ?: return null
        val pixel = pixelUser()
        val top = if (sky) {
            max(0, strip.y - ceil((RailwayScene.SKY + 2 * RailSprites.FINCH_GLIDE.h) * pixel).toInt())
        } else {
            strip.y + floor(RailwayScene.CUT * pixel).toInt()
        }
        val r = Rectangle(strip.x, top, strip.width, strip.y + strip.height - top)
        return SwingUtilities.convertRectangle(component, r, glass)
    }

    /** Paints the strip and the birds in the air on [glass] (the frame's glass pane), clipped to [clip] and the island. */
    fun paint(g: Graphics2D, glass: Component, clip: Shape) {
        val options = options ?: return
        val strip = strip() ?: return
        val body = body()
        val arc = (place?.arc ?: 0).toFloat()
        val g2 = g.create() as Graphics2D
        try {
            val island = SwingUtilities.convertRectangle(component, body, glass)
            val area = Area(clip)
            area.intersect(Area(RoundRectangle2D.Float(island.x.toFloat(), island.y.toFloat(), island.width.toFloat(), island.height.toFloat(), arc, arc)))
            g2.clip(area)
            val origin = SwingUtilities.convertPoint(component, strip.x, strip.y, glass)
            PixelCanvas.paint(g2, origin.x, origin.y, magnify = magnify()) {
                FeatureGate.getInstance().guard(JingleFeature.RAILWAY, Unit) {
                    scene.paint(this, logicalWidth(), options, skyHeight = toLogical(strip.y))
                }
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
        const val MIN_WINDOW = 60
    }
}
