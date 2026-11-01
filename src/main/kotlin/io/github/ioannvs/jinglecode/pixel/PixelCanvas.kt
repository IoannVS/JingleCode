package io.github.ioannvs.jinglecode.pixel

import com.intellij.ui.scale.JBUIScale
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws in logical pixels, the units of the mockup's `px`/`rect`.
 *
 * One logical pixel is a square of [pixelSize] device pixels: user scale times system (HiDPI)
 * scale, optionally magnified, rounded to an integer, so pixels stay sharp and equal at 125 %, 150 %, 200 %.
 * Drawing happens in device space with antialiasing off and nearest neighbor interpolation.
 *
 * Use [paint] to get a canvas; don't keep it after the call returns.
 */
class PixelCanvas private constructor(
    private val g: Graphics2D,
    /** Device pixels per logical pixel, always >= 1. */
    val pixelSize: Int,
    /** Device pixels per user-space pixel. */
    private val deviceScale: Double,
    private val originX: Int,
    private val originY: Int,
) {
    /** How many whole logical pixels fit into [userLength] of user space. */
    fun toLogical(userLength: Int): Int = floor(userLength * deviceScale / pixelSize).toInt()

    fun px(x: Int, y: Int, color: Color) = rect(x, y, 1, 1, color)

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) {
        if (w <= 0 || h <= 0) return
        g.color = color
        g.fillRect(originX + x * pixelSize, originY + y * pixelSize, w * pixelSize, h * pixelSize)
    }

    /** Runs [block] with drawing clipped to the logical rectangle ([x], [y], [w], [h]), the mockup's `ctx.clip()`. */
    fun clipped(x: Int, y: Int, w: Int, h: Int, block: PixelCanvas.() -> Unit) {
        val g2 = g.create() as Graphics2D
        try {
            g2.clipRect(originX + x * pixelSize, originY + y * pixelSize, w * pixelSize, h * pixelSize)
            PixelCanvas(g2, pixelSize, deviceScale, originX, originY).block()
        } finally {
            g2.dispose()
        }
    }

    /**
     * Soft light, the mockup's `createRadialGradient(cx, cy, 0, cx, cy, r)` from [color] at [alpha] in the
     * middle to nothing at [r], filled over the rectangle ([x], [y], [w], [h]). Only for lights the mockup
     * makes glow; everything else stays opaque.
     */
    @Suppress("UseJBColor")
    fun glow(cx: Double, cy: Double, r: Double, color: Color, alpha: Double, x: Int, y: Int, w: Int, h: Int) {
        if (w <= 0 || h <= 0 || r <= 0) return
        val center = Point2D.Double(originX + cx * pixelSize, originY + cy * pixelSize)
        val inner = Color(color.red, color.green, color.blue, (alpha * 255).roundToInt().coerceIn(0, 255))
        val outer = Color(color.red, color.green, color.blue, 0)
        val paint = g.paint
        g.paint = RadialGradientPaint(center, (r * pixelSize).toFloat(), floatArrayOf(0f, 1f), arrayOf(inner, outer))
        g.fillRect(originX + x * pixelSize, originY + y * pixelSize, w * pixelSize, h * pixelSize)
        g.paint = paint
    }

    companion object {
        /**
         * Runs [block] with a canvas whose logical (0, 0) is at ([x], [y]) in [g]'s user space.
         * [magnify] enlarges the art before rounding (decorations look like the mockup at about 1.6);
         * [zoom] multiplies the rounded pixel size, for previews.
         */
        @JvmStatic
        fun paint(
            g: Graphics,
            x: Int,
            y: Int,
            zoom: Int = 1,
            magnify: Float = 1f,
            block: PixelCanvas.() -> Unit) {
            val g2 = g.create() as Graphics2D
            try {
                val tx = g2.transform
                val origin = tx.transform(Point2D.Double(x.toDouble(), y.toDouble()), null)
                val scale = deviceScale(tx)
                val size = pixelSize(scale, zoom, magnify)
                g2.transform = AffineTransform()
                setHints(g2)
                val ox = jsRound(origin.x)
                val oy = jsRound(origin.y)
                val clip = g2.clip
                if (isRectangular(clip)) {
                    PixelCanvas(g2, size, scale.toDouble(), ox, oy).block()
                }
                else {
                    // Under a shape clip (strips, rounded islands, popups cut out) Java2D fills every rectangle
                    // through its general paint pipe, allocating a raster and a hints map each time: about
                    // a gigabyte a second for a scene. So draw into a buffer and copy it through the clip once.
                    val bounds = clip.bounds
                    if (bounds.isEmpty) return
                    paintBuffered(g2, bounds) { ig -> PixelCanvas(ig, size, scale.toDouble(), ox - bounds.x, oy - bounds.y).block() }
                }
            } finally {
                g2.dispose()
            }
        }

        private fun setHints(g: Graphics2D) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        }

        private fun isRectangular(clip: Shape?): Boolean = clip == null || clip is Rectangle2D || (clip is Area && clip.isRectangular)

        /** A buffer kept between paints; taken while in use, so a nested paint gets its own. */
        private var spare: BufferedImage? = null

        @Synchronized
        private fun takeBuffer(w: Int, h: Int): BufferedImage {
            val image = spare?.takeIf { it.width >= w && it.height >= h }
            spare = null
            // Plain device pixels on purpose: copied 1:1 with an identity transform, not scaled like a HiDPI image.
            @Suppress("UndesirableClassUsage")
            return image ?: BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        }

        @Synchronized
        private fun returnBuffer(image: BufferedImage) {
            val old = spare
            if (old == null || old.width * old.height < image.width * image.height) spare = image
        }

        /** Runs [draw] on a transparent buffer for the device rectangle [bounds] and copies it into [g] under its clip. */
        private fun paintBuffered(g: Graphics2D, bounds: Rectangle, draw: (Graphics2D) -> Unit) {
            val image = takeBuffer(bounds.width, bounds.height)
            try {
                val ig = image.createGraphics()
                try {
                    ig.composite = AlphaComposite.Clear
                    ig.fillRect(0, 0, bounds.width, bounds.height)
                    ig.composite = AlphaComposite.SrcOver
                    ig.clipRect(0, 0, bounds.width, bounds.height)
                    setHints(ig)
                    draw(ig)
                } finally {
                    ig.dispose()
                }
                g.drawImage(image, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0, 0, bounds.width, bounds.height, null)
            } finally {
                returnBuffer(image)
            }
        }

        /** Device pixels per logical pixel for a device (system) scale. */
        @JvmStatic
        fun pixelSize(deviceScale: Float, zoom: Int = 1, magnify: Float = 1f): Int =
            max(1, (JBUIScale.scale(1f) * deviceScale * magnify).roundToInt()) * max(1, zoom)

        /** Size in [component]'s user space (what Swing layouts use) of [logical] logical pixels. */
        @JvmStatic
        fun userSize(logical: Int, component: Component?, zoom: Int = 1): Int {
            val sysScale = JBUIScale.sysScale(component)
            return ceil(logical * pixelSize(sysScale, zoom) / sysScale).toInt()
        }

        /** JavaScript `Math.round`: halves go up. */
        @JvmStatic
        fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

        private fun deviceScale(tx: AffineTransform): Float {
            // Swing transforms are scale + translate; take the larger axis to be safe.
            val s = max(abs(tx.scaleX), abs(tx.scaleY))
            return if (s > 0.0) s.toFloat() else 1f
        }
    }
}

