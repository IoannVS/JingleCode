package io.github.ioannvs.jinglecode.village

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import io.github.ioannvs.jinglecode.editor.EditorForeground
import io.github.ioannvs.jinglecode.editor.Z_VILLAGE
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.sprites.seamRows
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The village along the bottom of one editor, over the code (Ivan's call: code showing through it
 * looked poor) and under the snow, at the bottom of the view while the text scrolls. Its snow goes on
 * under the line numbers too, so it lies along the whole bottom edge of the editor, over them.
 * A click on a house lights its stove: thick smoke from the chimney. Called on the EDT.
 *
 * In the editor it is a part of the [EditorForeground]. Over the gutter it is a glass pane painter,
 * because the gutter paints its line numbers after everything a plugin can put into it; popups are
 * cut out of it. The scene is drawn into an image in device pixels, as wide as the gutter and the view
 * together, and copied from there; it is drawn again only when something in it changes: the animator
 * frame (smoke, sails, the lift, skiers, skaters), the size, the day, the evening, the style.
 */
internal class EditorVillage(
    private val editor: Editor,
    private val isOn: () -> Boolean,
    private val time: () -> VillageTime,
    private val style: () -> VillageStyle,
    /** The animator frame the moving things are at. */
    private val frame: () -> Int,
    /** Whether the stars hang over the village: when no snow falls in the editor. */
    private val showStars: () -> Boolean,
) : Disposable {

    private data class Key(
        val width: Int,
        val pixelSize: Int,
        val sysScale: Float,
        val time: VillageTime,
        val style: VillageStyle,
        val dark: Boolean,
        val stars: Boolean,
        val seam: Int,
        val frame: Int,
        val stoked: Set<Int>,
    )

    private class Picture(val key: Key, val image: BufferedImage, val houses: List<HouseBox>)

    private var picture: Picture? = null

    /** User pixels per logical pixel at the last paint, to know where the houses are. */
    private var pixelUser = 1f

    /** When each house's stove was lit by a click, `System.currentTimeMillis`. */
    private val stokedAt = HashMap<Int, Long>()

    private var gutterPainter: Painter? = null

    init {
        EditorForeground.of(editor).add(Z_VILLAGE, ::paintInEditor, this)
        // The editor copies the old picture when it scrolls; the village must stay at the bottom.
        editor.scrollingModel.addVisibleAreaListener({ if (isOn()) repaint() }, this)
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mousePressed(event: EditorMouseEvent) = onPress(event)
        }, this)
    }

    private val gutter: JComponent? get() = (editor as? EditorEx)?.gutterComponentEx

    private val gutterWidth: Int get() = gutter?.width ?: 0

    fun repaint() {
        if (editor.isDisposed) return
        editor.contentComponent.repaint(editor.scrollingModel.visibleArea)
        gutter?.repaint()
    }

    /** Repaints the strip the village takes, for the next frame of what moves in it. */
    fun repaintStrip() {
        if (editor.isDisposed || !isOn()) return
        val visible = editor.scrollingModel.visibleArea
        val h = ceil(VILLAGE_ROWS * pixelUser).toInt()
        editor.contentComponent.repaint(Rectangle(visible.x, visible.y + visible.height - h, visible.width, h))
        gutter?.let { it.repaint(Rectangle(0, it.visibleRect.let { r -> r.y + r.height - h }, it.width, h)) }
    }

    /** Repaints if the picture would change now (a new day, the evening, another style). */
    fun refresh() {
        val key = picture?.key ?: return
        if (key.time != time() || key.style != style().on(key.time.date)) repaint()
    }

    private fun stoked(now: Long): Set<Int> = stokedAt.filterValues { now - it < STOKE_MS }.keys

    /** A press on a house with a chimney lights its stove; the press stays with the village, the caret doesn't move. */
    private fun onPress(event: EditorMouseEvent) {
        if (!isOn() || event.isConsumed) return
        val houses = picture?.houses ?: return
        val visible = editor.scrollingModel.visibleArea
        val point = event.mouseEvent.point
        val left = visible.x - gutterWidth
        val top = visible.y + visible.height - VILLAGE_ROWS * pixelUser
        val x = floor((point.x - left) / pixelUser).toInt()
        val y = floor((point.y - top) / pixelUser).toInt()
        val house = houses.lastOrNull { it.hasChimney && it.contains(x, y) } ?: return
        stokedAt[house.index] = System.currentTimeMillis()
        event.consume()
        repaintStrip()
    }

    private fun paintInEditor(g: Graphics2D) {
        installGutterPainter()
        // The picture starts under the gutter, left of the view.
        val visible = editor.scrollingModel.visibleArea
        paintPicture(g, visible.x - gutterWidth, visible.y + visible.height, visible.width + gutterWidth)
    }

    /** Over the gutter through the glass pane, once the editor is in a frame. */
    private fun installGutterPainter() {
        if (gutterPainter != null) return
        val gutter = gutter ?: return
        if (!gutter.isShowing) return
        val root = SwingUtilities.getRootPane(gutter) ?: return
        val painter = object : Painter {
            override fun needsRepaint(): Boolean = true
            override fun paint(component: Component, g: Graphics2D) = paintOverGutter(g, root.glassPane)
            override fun addListener(listener: Painter.Listener) = Unit
            override fun removeListener(listener: Painter.Listener) = Unit
        }
        IdeGlassPaneUtil.find(gutter).addPainter(root.glassPane, painter, this)
        gutterPainter = painter
        gutter.repaint()
    }

    /** [g] is in the glass pane's coordinates: the gutter's visible part there, minus popups over it. */
    private fun paintOverGutter(g: Graphics2D, glass: Component) {
        val gutter = gutter ?: return
        if (!isOn() || editor.isDisposed || !gutter.isShowing) return
        val visible = SwingUtilities.convertRectangle(gutter, gutter.visibleRect, glass)
        if (visible.isEmpty) return
        val clip = Area(visible)
        SwingUtilities.getRootPane(gutter)?.let { root ->
            FrameGeometry.popupsOver(root).forEach { clip.subtract(Area(SwingUtilities.convertRectangle(root, it, glass))) }
        }
        val g2 = g.create() as Graphics2D
        try {
            g2.clip(clip)
            paintPicture(g2, visible.x, visible.y + visible.height, gutterWidth + editor.scrollingModel.visibleArea.width)
        }
        finally {
            g2.dispose()
        }
    }

    /** Copies the picture with its bottom left corner at ([left], [bottom]), user space of [g]. */
    private fun paintPicture(g: Graphics2D, left: Int, bottom: Int, userWidth: Int) {
        if (!isOn() || userWidth <= 0) return
        val picture = picture(g, userWidth)
        val corner = g.transform.transform(Point2D.Double(left.toDouble(), bottom.toDouble()), null)
        val g2 = g.create() as Graphics2D
        try {
            g2.transform = AffineTransform()
            g2.drawImage(picture.image, PixelCanvas.jsRound(corner.x), PixelCanvas.jsRound(corner.y) - picture.image.height, null)
        }
        finally {
            g2.dispose()
        }
    }

    /** The village for a picture [userWidth] wide, from the cache when nothing changed. */
    private fun picture(g: Graphics2D, userWidth: Int): Picture {
        val sysScale = maxOf(g.transform.scaleX, g.transform.scaleY).toFloat().takeIf { it > 0 } ?: 1f
        // A logical pixel is a tenth of the editor line, like the snow: the village follows the font and zoom.
        val magnify = editor.lineHeight / LOGICAL_PER_LINE / JBUIScale.scale(1f)
        val size = PixelCanvas.pixelSize(sysScale, magnify = magnify)
        pixelUser = size / sysScale
        val width = ceil(userWidth * sysScale / size).toInt() + 1
        val dark = ColorUtil.isDark(editor.colorsScheme.defaultBackground)
        val time = time()
        // The snow at the left edge runs on from the Project panel's drift at the same height on screen.
        val key = Key(
            width, size, sysScale, time, style().on(time.date), dark,
            stars = dark && showStars(), seam = seamRows(pixelUser), frame = frame(), stoked = stoked(System.currentTimeMillis()),
        )
        val old = picture
        if (old != null && old.key == key) return old
        // Plain device pixels on purpose: it is copied 1:1 with an identity transform, not scaled like a HiDPI image.
        @Suppress("UndesirableClassUsage")
        val image = old?.image?.takeIf { it.width == width * size && it.height == VILLAGE_ROWS * size }
            ?: BufferedImage(width * size, VILLAGE_ROWS * size, BufferedImage.TYPE_INT_ARGB)
        val ig = image.createGraphics()
        var houses = emptyList<HouseBox>()
        try {
            ig.composite = AlphaComposite.Clear
            ig.fillRect(0, 0, image.width, image.height)
            ig.composite = AlphaComposite.SrcOver
            // Scaled like the screen, so the canvas picks the same whole pixel size as on it.
            ig.scale(sysScale.toDouble(), sysScale.toDouble())
            PixelCanvas.paint(ig, 0, 0, magnify = magnify) {
                houses = drawVillage(key.style, width, time.seed, key.frame, time.evening, key.stars, key.seam, key.stoked)
            }
        }
        finally {
            ig.dispose()
        }
        return Picture(key, image, houses).also { picture = it }
    }

    override fun dispose() {
        picture = null
        repaint()
    }

    private companion object {
        const val LOGICAL_PER_LINE = 10f

        /** How long a stove lit by a click smokes, milliseconds. */
        const val STOKE_MS = 8_000L
    }
}
