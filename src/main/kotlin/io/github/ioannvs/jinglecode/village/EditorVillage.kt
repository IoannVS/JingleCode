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
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
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
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JViewport
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
 * frame (smoke, sails, the lift, skiers, skaters, the residents' scenes), the size, the day, the evening,
 * the style.
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
    /** How often the residents play their scenes; null while they are switched off. */
    private val residents: () -> ResidentFrequency?,
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
        val residents: Map<String, Int>,
    )

    private class Picture(val key: Key, val image: BufferedImage, val houses: List<HouseBox>)

    private var picture: Picture? = null

    /** User pixels per logical pixel at the last paint, to know where the houses are. */
    private var pixelUser = 1f

    /** When each house's stove was lit by a click, `System.currentTimeMillis`. */
    private val stokedAt = HashMap<Int, Long>()

    private var gutterPainter: Painter? = null

    /** This village's own planner: each editor plays its scenes on its own. */
    private val scheduler = ResidentScheduler()

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

    private val viewport: JViewport? get() = (editor as? EditorEx)?.scrollPane?.viewport

    /** The editors' island body, root pane coordinates; `null` without the Islands UI. */
    private fun editorBody(root: JRootPane): Rectangle? {
        val project = editor.project ?: return null
        return FeatureGate.getInstance().guard(JingleFeature.VILLAGE, null) { FrameGeometry.editorBody(project, root) }
    }

    /**
     * Where the village stands, root pane coordinates: on the line of the snowman's drift and Santa's yard,
     * the bottom of the island, under a horizontal scroll bar and the corner the line numbers reach into.
     */
    private fun groundIn(root: JRootPane): Int? {
        val pane = (editor as? EditorEx)?.scrollPane ?: return null
        if (!pane.isShowing) return null
        return FrameGeometry.ground(SwingUtilities.convertRectangle(pane.parent, pane.bounds, root), editorBody(root))
    }

    /** How far below the bottom of the view the village stands, user pixels: 0 when the view reaches down to the line. */
    private fun belowView(): Int {
        val viewport = viewport ?: return 0
        val root = SwingUtilities.getRootPane(viewport) ?: return 0
        val ground = groundIn(root) ?: return 0
        return ground - SwingUtilities.convertPoint(viewport, 0, viewport.height, root).y
    }

    /** The part of the village under the view, root pane coordinates; it is painted over the glass pane. */
    private fun underView(): Pair<JRootPane, Rectangle>? {
        val viewport = viewport ?: return null
        val root = SwingUtilities.getRootPane(viewport) ?: return null
        val below = belowView()
        if (below <= 0) return null
        val view = SwingUtilities.convertRectangle(viewport.parent, viewport.bounds, root)
        return root to Rectangle(view.x - gutterWidth, view.y + view.height, view.width + gutterWidth, below)
    }

    fun repaint() {
        if (editor.isDisposed) return
        editor.contentComponent.repaint(editor.scrollingModel.visibleArea)
        gutter?.repaint()
        underView()?.let { (root, r) -> root.repaint(r) }
    }

    /** Repaints the strip the village takes, for the next frame of what moves in it. */
    fun repaintStrip() {
        if (editor.isDisposed || !isOn()) return
        val visible = editor.scrollingModel.visibleArea
        val h = ceil(VILLAGE_ROWS * pixelUser).toInt()
        val below = belowView()
        editor.contentComponent.repaint(Rectangle(visible.x, visible.y + visible.height + below - h, visible.width, h))
        gutter?.let { it.repaint(Rectangle(0, it.visibleRect.let { r -> r.y + r.height - h }, it.width, h)) }
        underView()?.let { (root, r) -> root.repaint(r) }
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
        val top = visible.y + visible.height + belowView() - VILLAGE_ROWS * pixelUser
        val x = floor((point.x - left) / pixelUser).toInt()
        val y = floor((point.y - top) / pixelUser).toInt()
        val house = houses.lastOrNull { it.hasChimney && it.contains(x, y) } ?: return
        stokedAt[house.index] = System.currentTimeMillis()
        event.consume()
        repaintStrip()
    }

    private fun paintInEditor(g: Graphics2D) {
        installGutterPainter()
        // The picture starts under the gutter, left of the view; what is below the view the glass pane paints.
        val visible = editor.scrollingModel.visibleArea
        paintPicture(g, visible.x - gutterWidth, visible.y + visible.height + belowView(), visible.width + gutterWidth)
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

    /**
     * [g] is in the glass pane's coordinates: the gutter's visible part there and the strip under the view
     * (a horizontal scroll bar, the corner), minus popups over them.
     */
    private fun paintOverGutter(g: Graphics2D, glass: Component) {
        val gutter = gutter ?: return
        if (!isOn() || editor.isDisposed || !gutter.isShowing) return
        val root = SwingUtilities.getRootPane(gutter) ?: return
        val ground = groundIn(root) ?: return
        val visible = SwingUtilities.convertRectangle(gutter, gutter.visibleRect, glass)
        if (visible.isEmpty) return
        val clip = Area(visible)
        underView()?.let { (_, r) -> clip.add(Area(SwingUtilities.convertRectangle(root, r, glass))) }
        // Most glass pane repaints are the overlay's strips elsewhere in the frame.
        g.clipBounds?.let { if (!clip.intersects(it)) return }
        // The editor's own side is cut by the island's rounded body; the glass pane is above the island,
        // and the gutter reaches into its border under the garland, so it is cut to the body here.
        val body = editorBody(root)
        if (body != null) {
            val b = SwingUtilities.convertRectangle(root, body, glass)
            val arc = FrameGeometry.islandArc().toFloat()
            clip.intersect(Area(RoundRectangle2D.Float(b.x.toFloat(), b.y.toFloat(), b.width.toFloat(), b.height.toFloat(), arc, arc)))
        }
        FrameGeometry.popupsOver(root).forEach { clip.subtract(Area(SwingUtilities.convertRectangle(root, it, glass))) }
        // The same bottom as in the editor, so the gutter's part is not a step lower or higher.
        val bottom = SwingUtilities.convertPoint(root, 0, ground, glass).y
        val g2 = g.create() as Graphics2D
        try {
            g2.clip(clip)
            paintPicture(g2, visible.x, bottom, gutterWidth + editor.scrollingModel.visibleArea.width)
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
        val style = style().on(time.date)
        val frame = frame()
        val frequency = residents()
        if (frequency == null) scheduler.clear()
        val playing = frequency?.let { scheduler.at(frame, style.scene.residents, it) }.orEmpty()
        // The snow at the left edge runs on from the Project panel's drift at the same height on screen.
        val key = Key(
            width, size, sysScale, time, style, dark,
            stars = dark && showStars(), seam = seamRows(pixelUser), frame = frame, stoked = stoked(System.currentTimeMillis()),
            residents = playing,
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
                houses = drawVillage(key.style, width, time.seed, key.frame, time.evening, key.stars, key.seam, key.stoked, key.residents)
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
