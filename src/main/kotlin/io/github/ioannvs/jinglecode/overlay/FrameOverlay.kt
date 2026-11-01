package io.github.ioannvs.jinglecode.overlay

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
import com.intellij.util.ui.UIUtil
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.drift.DriftLayer
import io.github.ioannvs.jinglecode.fireworks.FireworksLayer
import io.github.ioannvs.jinglecode.garland.GarlandLayer
import io.github.ioannvs.jinglecode.icicles.FallingIciclesLayer
import io.github.ioannvs.jinglecode.icicles.IciclesLayer
import io.github.ioannvs.jinglecode.icicles.TabIciclesLayer
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import io.github.ioannvs.jinglecode.snow.PanelSnowLayer
import io.github.ioannvs.jinglecode.snow.SnowPlace
import io.github.ioannvs.jinglecode.railway.RailwayLayer
import io.github.ioannvs.jinglecode.yard.YardLayer
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.geom.Area
import javax.swing.JComponent
import javax.swing.JRootPane

/**
 * The decorations that live on the edges of the project frame's panels: the garland in the gaps,
 * the icicles under the toolbar, the icicles under the active tab, the drift and the snow in the Project and Commit panels. One glass pane painter for all of them.
 *
 * Each [OverlayLayer] paints only inside its own strips, minus popups and sliding tool windows,
 * and only those strips are repainted on a frame. Frame geometry comes from [FrameGeometry];
 * if it throws, [FeatureGate] turns every layer off for this session.
 */
@Service(Service.Level.PROJECT)
class FrameOverlay(private val project: Project) : Disposable {

    private val panelSnow = PanelSnowLayer(JingleFeature.PANEL_SNOW, ::treePanels) { UIUtil.getTreeBackground() }
    private val rightSnow = PanelSnowLayer(JingleFeature.RIGHT_SNOW, ::rightPanels) { UIUtil.getPanelBackground() }
    private val bottomSnow = PanelSnowLayer(JingleFeature.BOTTOM_SNOW, ::bottomPanels) { UIUtil.getPanelBackground() }
    private val santaYard = YardLayer()

    // In paint order: the snow falls over the drift, falling icicles over everything.
    private val layers: List<OverlayLayer> = listOf(GarlandLayer, IciclesLayer, TabIciclesLayer, DriftLayer, santaYard, RailwayLayer, panelSnow, rightSnow, bottomSnow, FireworksLayer, FallingIciclesLayer)
    private val features = layers.map { it.feature }

    private var root: JRootPane? = null
    private var lastDirty: List<Rectangle> = emptyList()
    private var lastTick = -1L

    /**
     * The last layout found. Glass pane repaints between frames (caret blink, typing) reuse it;
     * it is found again on every frame and after any component is moved, resized, shown, hidden, added or removed.
     */
    private var cachedLayout: FrameLayout? = null
    private val geometryListener = AWTEventListener { cachedLayout = null }

    private val painter = object : Painter {
        override fun needsRepaint(): Boolean = true // painted on every glass pane repaint, not once
        override fun paint(component: Component, g: Graphics2D) = paintLayers(g)
        override fun addListener(listener: Painter.Listener) = Unit
        override fun removeListener(listener: Painter.Listener) = Unit
    }

    private val gate get() = FeatureGate.getInstance()

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
        val animator = JingleAnimator.getInstance(project)
        // One subscription per layer, so the timer runs while any of them is on; one repaint per frame.
        for (feature in features) {
            animator.subscribe(this, feature) { frame ->
                if (frame.tick != lastTick) {
                    lastTick = frame.tick
                    repaintLayers()
                    followMotion()
                }
            }
        }
        // The panel snow falls on its own clock, as fast as the editor's, and repaints only itself.
        val settings = JingleCodeSettings.getInstance()
        for (snow in listOf(panelSnow, rightSnow, bottomSnow)) {
            animator.subscribeMotion(this, snow.feature, intervalMs = { settings.state.snowFrameRate.intervalMs }) { dt ->
                moveSnow(snow, dt)
            }
        }
        // Santa's yard moves on the smooth clock too, like the mockup, and repaints only its strips.
        animator.subscribeMotion(this, JingleFeature.SANTA_YARD) { dt -> moveYard(dt) }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { repaintLayers() })
        repaintLayers()
        return true
    }

    private fun moveSnow(snow: PanelSnowLayer, dt: Double) {
        val rootPane = root ?: return
        val glassPane = rootPane.glassPane as? JComponent ?: return
        if (!gate.isActive(snow.feature)) return
        val layout = layout() ?: return
        val scale = OverlayScale.current()
        val state = JingleCodeSettings.getInstance().state
        val moved = gate.guard(snow.feature, false) {
            // The pixel as the canvas rounds it on this screen, so the flakes move pixel by pixel.
            val sysScale = JBUIScale.sysScale(rootPane)
            val pixelUser = PixelCanvas.pixelSize(sysScale, magnify = scale.magnify) / sysScale
            snow.move(layout, pixelUser, dt, state.snowAmount, state.snowSpeed)
        }
        if (moved) snow.dirtyRects(project, layout, scale).forEach { glassPane.paintImmediately(it) }
    }

    private fun moveYard(dt: Double) {
        val rootPane = root ?: return
        val glassPane = rootPane.glassPane as? JComponent ?: return
        if (!gate.isActive(santaYard.feature)) return
        val layout = layout() ?: return
        val scale = OverlayScale.current()
        val moved = gate.guard(santaYard.feature, false) { santaYard.move(project, layout, scale, JBUIScale.sysScale(rootPane), dt) }
        if (moved) santaYard.dirtyRects(project, layout, scale).forEach { glassPane.paintImmediately(it) }
    }

    private fun activeLayers(): List<OverlayLayer> = layers.filter { gate.isActive(it.feature) }

    /** The smooth clock, subscribed only while a layer is animating; frames check it each time. */
    private var motion: Disposable? = null

    private fun followMotion() {
        val animating = activeLayers().firstOrNull { it.isAnimating(project) }
        if (animating != null && motion == null) {
            val subscription = Disposer.newDisposable("Jingle Code overlay motion")
            Disposer.register(this, subscription)
            JingleAnimator.getInstance(project).subscribeMotion(subscription, animating.feature) { repaintLayers() }
            motion = subscription
        }
        else if (animating == null) {
            motion?.let(Disposer::dispose)
            motion = null
        }
    }

    private fun layout(): FrameLayout? {
        cachedLayout?.let { return it }
        val rootPane = root ?: return null
        return gate.guard(features, null) { FrameGeometry.find(project, rootPane) }?.also { cachedLayout = it }
    }

    /**
     * Repaints where the layers are now and where they were, so they never leave a trail.
     *
     * Each strip is painted on its own: `repaint` would merge them into their bounding box, which is
     * nearly the whole frame, and the transparent glass pane would repaint the editor under it every frame.
     */
    private fun repaintLayers() {
        val glassPane = root?.glassPane as? JComponent ?: return
        cachedLayout = null
        val active = activeLayers()
        val layout = if (active.isNotEmpty()) layout() else null
        val scale = OverlayScale.current()
        val dirty = layout?.let { l -> active.flatMap { it.dirtyRects(project, l, scale) } }.orEmpty()
        withoutNested(lastDirty + dirty).forEach { glassPane.paintImmediately(it) }
        lastDirty = dirty
    }

    /** [rects] without the ones inside another (the same strip as on the last frame, say): repainted with it. */
    private fun withoutNested(rects: List<Rectangle>): List<Rectangle> = rects.filterIndexed { i, r ->
        rects.indices.none { j -> j != i && rects[j].contains(r) && (rects[j] != r || j < i) }
    }

    private fun paintLayers(g: Graphics2D) {
        val active = activeLayers()
        if (active.isEmpty()) return
        val layout = layout() ?: return
        val scale = OverlayScale.current()
        val tick = JingleAnimator.getInstance(project).current.tick
        for (layer in active) {
            val strips = layer.dirtyRects(project, layout, scale)
            if (strips.isEmpty()) continue
            val clip = Area()
            strips.forEach { clip.add(Area(it)) }
            layout.occluders.forEach { clip.subtract(Area(it)) }
            val g2 = g.create() as Graphics2D
            try {
                g2.clip(clip)
                gate.guard(layer.feature, Unit) { layer.paint(g2, project, layout, scale, tick) }
            }
            finally {
                g2.dispose()
            }
        }
    }

    override fun dispose() {
        // The painter is a child of this and is already gone: repaint the last strips to wipe the decorations off.
        (root?.glassPane as? JComponent)?.let { glassPane -> lastDirty.forEach(glassPane::repaint) }
        lastDirty = emptyList()
        cachedLayout = null
        root = null
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): FrameOverlay = project.service()
    }
}

/** Where the panel snow falls: the trees of the Project and Commit panels. */
private fun treePanels(layout: FrameLayout): List<SnowPlace> = listOfNotNull(
    layout.projectPanel?.let { SnowPlace("project", it.body, it.arc, it.content) },
    layout.commitPanel?.let { SnowPlace("commit", it.body, it.arc, it.content) },
)

/** Where the snow over Santa's yard falls: the content of every tool window at the right. */
private fun rightPanels(layout: FrameLayout): List<SnowPlace> = layout.rightPanels.map { SnowPlace(it.id, it.body, it.arc, it.content) }

/** Where the snow over the railway falls: the content of every tool window docked at the bottom. */
private fun bottomPanels(layout: FrameLayout): List<SnowPlace> = layout.bottomPanels.map { SnowPlace("bottom:" + it.id, it.body, it.arc, it.content) }
