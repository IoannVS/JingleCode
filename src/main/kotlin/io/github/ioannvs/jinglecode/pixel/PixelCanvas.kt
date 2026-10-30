package io.github.ioannvs.jinglecode.pixel

import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
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
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
                PixelCanvas(g2, size, scale.toDouble(), jsRound(origin.x), jsRound(origin.y)).block()
            } finally {
                g2.dispose()
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

