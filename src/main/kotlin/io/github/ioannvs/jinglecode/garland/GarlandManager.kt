package io.github.ioannvs.jinglecode.garland

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.geom.Area
import javax.swing.JComponent
import javax.swing.JRootPane
import kotlin.math.ceil

/**
 * The garland in the gaps between panels of the project frame.
 *
 * Painted by a glass pane painter along the gap lines, sized against the UI font like the mockup.
 * Bulbs may lie on the empty rounded edges of the islands, never on popups. Frame geometry comes
 * from [GapFinder]; if it throws, [FeatureGate] turns the garland off for this session.
 */
@Service(Service.Level.PROJECT)
class GarlandManager(private val project: Project) : Disposable {

    private var root: JRootPane? = null
    private var lastDirty: List<Rectangle> = emptyList()

    /**
     * The last layout found. Glass pane repaints between frames (caret blink, typing) reuse it;
     * it is found again on every frame and after any component is moved, resized, shown, hidden, added or removed.
     */
    private var cachedLayout: GapLayout? = null
    private val geometryListener = AWTEventListener { cachedLayout = null }

    private val painter = object : Painter {
        override fun needsRepaint(): Boolean = true // painted on every glass pane repaint, not once
        override fun paint(component: Component, g: Graphics2D) = paintGarland(g)
        override fun addListener(listener: Painter.Listener) = Unit
        override fun removeListener(listener: Painter.Listener) = Unit
    }

    /**
     * Hooks into the project frame; call on the EDT.
     * Returns false if the frame isn't there yet, so the caller can try again later.
     */
    fun install(): Boolean {
        if (root != null) return true
        val frame = WindowManager.getInstance().getFrame(project) ?: return false
        val rootPane = frame.rootPane ?: return false
        root = rootPane
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(geometryListener, AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.CONTAINER_EVENT_MASK)
        Disposer.register(this) { toolkit.removeAWTEventListener(geometryListener) }
        IdeGlassPaneUtil.find(rootPane.contentPane).addPainter(rootPane.glassPane, painter, this)
        JingleAnimator.getInstance(project).subscribe(this, JingleFeature.GARLAND) { repaintGaps() }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { repaintGaps() })
        repaintGaps()
        return true
    }

    private val isOn: Boolean get() = FeatureGate.getInstance().isActive(JingleFeature.GARLAND)

    private fun layout(): GapLayout? {
        cachedLayout?.let { return it }
        val rootPane = root ?: return null
        return FeatureGate.getInstance().guard(JingleFeature.GARLAND, null) {
            GapFinder.find(project, rootPane)
        }?.also { cachedLayout = it }
    }

    /**
     * Repaints where the garland is now and where it was, so it never leaves a trail.
     *
     * Each strip is painted on its own: `repaint` would merge them into their bounding box, which is
     * nearly the whole frame, and the transparent glass pane would repaint the editor under it every frame.
     */
    private fun repaintGaps() {
        val glassPane = root?.glassPane as? JComponent ?: return
        cachedLayout = null
        val layout = if (isOn) layout() else null
        val dirty = layout?.dirtyRects(reachUser()).orEmpty()
        (lastDirty + dirty).forEach { glassPane.paintImmediately(it) }
        lastDirty = dirty
    }

    private fun paintGarland(g: Graphics2D) {
        if (!isOn) return
        val layout = layout() ?: return
        if (layout.isEmpty) return

        val clip = Area()
        layout.dirtyRects(reachUser()).forEach { clip.add(Area(it)) }
        layout.occluders.forEach { clip.subtract(Area(it)) }
        g.clip(clip)

        val mode = JingleCodeSettings.getInstance().state.garlandMode
        val t = (JingleAnimator.getInstance(project).current.tick % TICK_WRAP).toInt()

        val magnify = magnify()
        for (line in layout.vertical) {
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawVerticalGarland(toLogical(line.height), mode, t)
            }
        }
        for (line in layout.horizontal) {
            PixelCanvas.paint(g, line.x, line.y, magnify = magnify) {
                drawHorizontalGarland(toLogical(line.width), mode, t)
            }
        }
        layout.statusBand?.let { band ->
            // The strip is lower than the garland: hang it from the islands' bottom edge so the bulbs end
            // right above the status bar icons.
            PixelCanvas.paint(g, band.x, band.y + band.height, magnify = magnify) {
                drawStatusBarGarland(toLogical(band.width), mode, t)
            }
        }
    }

    override fun dispose() {
        // The painter is a child of this and is already gone: repaint the last strips to wipe the garland off.
        (root?.glassPane as? JComponent)?.let { glassPane -> lastDirty.forEach(glassPane::repaint) }
        lastDirty = emptyList()
        cachedLayout = null
        root = null
    }

    companion object {
        /** Garland pixel size relative to the UI font, user pixels per logical pixel. */
        private fun pixelUser(): Float =
            JingleCodeSettings.getInstance().state.garlandSize.pixelUser(JBFont.label().size2D)

        /** [pixelUser] as the magnification [PixelCanvas] expects. */
        private fun magnify(): Float = pixelUser() / JBUIScale.scale(1f)

        /** How far the garland reaches to each side of a gap line, user pixels. */
        private fun reachUser(): Int = ceil((GARLAND_REACH + 1) * pixelUser()).toInt()

        /** Keeps the tick an Int; divisible by every period the modes use. */
        private const val TICK_WRAP = 1_201_200L

        @JvmStatic
        fun getInstance(project: Project): GarlandManager = project.service()
    }
}
