package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import kotlin.math.ceil

/**
 * A panel snow falls in: [content] inside the island's rounded [body] with corners of [arc]; [key] tells
 * the panels apart, so each keeps its own flakes.
 */
internal class SnowPlace(val key: String, val body: Rectangle, val arc: Int, val content: Rectangle)

/**
 * Snow falling over panels as in the editor ([SnowField], the same amount and speed), over their scenes too,
 * in the pixels of the frame overlay: the trees of the Project and Commit panels (where the drift drops its
 * own few flakes meanwhile), the tool windows at the right over Santa's yard. One per [feature] and frame
 * overlay; [places] says where it falls, [background] what it falls on. It moves on the smooth clock, see [move].
 */
internal class PanelSnowLayer(
    override val feature: JingleFeature,
    private val places: (FrameLayout) -> List<SnowPlace>,
    private val background: () -> Color,
) : OverlayLayer {

    private val fields = HashMap<String, SnowField>()

    /** Seconds of snowfall so far; sways the flakes. */
    private var time = 0.0

    private fun field(place: SnowPlace) = fields.getOrPut(place.key) { SnowField() }

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> =
        places(layout).map { it.content }

    /**
     * Moves the snow by [dt] seconds; [pixelUser] is the logical pixel as the canvas rounds it on this screen.
     * Returns whether any flake reached another pixel. Panels that are gone lose their flakes.
     */
    fun move(layout: FrameLayout, pixelUser: Float, dt: Double, amount: SnowAmount, speed: SnowSpeed): Boolean {
        time += dt
        var moved = false
        val places = places(layout)
        fields.keys.retainAll(places.mapTo(HashSet()) { it.key })
        for (place in places) {
            val width = ceil(place.content.width / pixelUser).toInt()
            val height = ceil(place.content.height / pixelUser).toInt()
            if (field(place).step(width, height, dt, time, amount.areaPerFlake, speed.factor)) moved = true
        }
        return moved
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        // White snow is lost on a light panel: there it is ice blue, as in the editor.
        val color = if (ColorUtil.isDark(background())) JinglePalette.SNOW else JinglePalette.ICE
        for (place in places(layout)) {
            val field = fields[place.key] ?: continue
            if (field.flakes.isEmpty()) continue
            if (!g.hitClip(place.content.x, place.content.y, place.content.width, place.content.height)) continue
            val g2 = g.create() as Graphics2D
            try {
                val body = place.body
                val arc = place.arc.toFloat()
                // In the island's rounded corners, not on its border.
                g2.clip(RoundRectangle2D.Float(body.x.toFloat(), body.y.toFloat(), body.width.toFloat(), body.height.toFloat(), arc, arc))
                g2.clip(place.content)
                PixelCanvas.paint(g2, place.content.x, place.content.y, magnify = scale.magnify) {
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
