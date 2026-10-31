package io.github.ioannvs.jinglecode.popups

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
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
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEvents
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.geom.Area
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.JRootPane
import kotlin.math.ceil

/**
 * Snow caps on the top edges of popups, notification balloons and context menus over the project frame
 * (`docs/POPUPS.md`). There is no public way to decorate other people's popups, so this watches window
 * and layered pane events, finds the popups with [PopupFinder] and paints the caps on the frame's glass
 * pane, just above each popup and never inside it (except the holly that hangs on a notification's corner).
 *
 * Everything that touches the popups goes through [FeatureGate.guard]: if it throws, both popup
 * decorations are off until restart.
 */
@Service(Service.Level.PROJECT)
class PopupSnow(private val project: Project) : Disposable {

    private var root: JRootPane? = null
    private var frame: Window? = null

    /** Popups found on the last refresh, root pane coordinates. */
    private var popups: List<FoundPopup> = emptyList()
    private var occluders: List<Rectangle> = emptyList()
    private var lastDirty: List<Rectangle> = emptyList()
    private var refreshQueued = false

    /** Notifications shown lately; their balloons are the ones that get the holly. */
    private val notifications: MutableSet<Notification> = Collections.newSetFromMap(WeakHashMap())

    private val gate get() = FeatureGate.getInstance()

    private val windowListener = AWTEventListener { event ->
        if (gate.guard(FEATURES, false) { isAboutPopups(event) }) queueRefresh()
    }

    private val painter = object : Painter {
        override fun needsRepaint(): Boolean = true
        override fun paint(component: Component, g: Graphics2D) = paintCaps(g)
        override fun addListener(listener: Painter.Listener) = Unit
        override fun removeListener(listener: Painter.Listener) = Unit
    }

    /** Hooks into the project frame; call on the EDT. False if the frame isn't there yet. */
    fun install(): Boolean {
        if (root != null) return true
        val ideFrame = WindowManager.getInstance().getFrame(project) ?: return false
        val rootPane = ideFrame.rootPane ?: return false
        root = rootPane
        frame = ideFrame

        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK or AWTEvent.CONTAINER_EVENT_MASK)
        Disposer.register(this) { toolkit.removeAWTEventListener(windowListener) }
        IdeGlassPaneUtil.find(rootPane.contentPane).addPainter(rootPane.glassPane, painter, this)

        // Notifications.TOPIC is not broadcast: listen where project and application notifications go.
        val shown = object : Notifications {
            override fun notify(notification: Notification) {
                notifications += notification
                // Santa's yard gets a letter; only this package listens to notifications (ArchitectureTest).
                JingleEvents.publish(project, JingleEvent.Notification)
                queueRefresh()
            }
        }
        project.messageBus.connect(this).subscribe(Notifications.TOPIC, shown)
        ApplicationManager.getApplication().messageBus.connect(this).apply {
            subscribe(Notifications.TOPIC, shown)
            subscribe(FeatureGate.TOPIC, FeatureGate.Listener { refresh() })
        }

        // The sparkle and the berry need frames; a frame also catches anything the events missed.
        val animator = JingleAnimator.getInstance(project)
        var lastTick = -1L
        for (feature in FEATURES) {
            animator.subscribe(this, feature) { frame ->
                if (frame.tick != lastTick) {
                    lastTick = frame.tick
                    refresh()
                }
            }
        }
        refresh()
        return true
    }

    /** A window owned by our frame or a component of its layered pane appeared, moved, changed or went away. */
    private fun isAboutPopups(event: AWTEvent): Boolean {
        val rootPane = root ?: return false
        return when (val source = event.source) {
            is Window -> source !== frame && isOwnedByFrame(source)
            rootPane.layeredPane -> true
            is Component -> source.parent === rootPane.layeredPane
            else -> false
        }
    }

    private fun isOwnedByFrame(window: Window): Boolean {
        var owner = window.owner
        while (owner != null) {
            if (owner === frame) return true
            owner = owner.owner
        }
        return false
    }

    private fun queueRefresh() {
        if (refreshQueued) return
        refreshQueued = true
        ApplicationManager.getApplication().invokeLater {
            refreshQueued = false
            if (!project.isDisposed) refresh()
        }
    }

    private fun activeKinds(): Set<PopupKind> = PopupKind.entries.filterTo(mutableSetOf()) { gate.isActive(it.feature) }

    /** Finds the popups again and repaints where their caps are now and where they were. */
    private fun refresh() {
        val rootPane = root ?: return
        val ideFrame = frame ?: return
        val kinds = activeKinds()
        val balloons = notifications.mapNotNull { it.balloon }
        popups = if (kinds.isEmpty()) emptyList()
        else gate.guard(FEATURES, emptyList()) { PopupFinder.find(rootPane, ideFrame, balloons, kinds) }
        occluders = if (popups.isEmpty()) emptyList() else gate.guard(FEATURES, emptyList()) { PopupFinder.occludersIn(rootPane) }
        val pixel = pixelUser(rootPane)
        val dirty = popups.mapNotNull { capArea(it, pixel) }
        val glassPane = rootPane.glassPane as? JComponent ?: return
        // Each cap on its own: a merged repaint would cover everything between a menu and a balloon.
        (lastDirty + dirty).distinct().forEach { glassPane.paintImmediately(it) }
        lastDirty = dirty
    }

    /** User pixels per logical pixel: the same pixel as the garland and the icicles. */
    private fun pixelUser(component: Component): Float {
        val sysScale = JBUIScale.sysScale(component)
        return PixelCanvas.pixelSize(sysScale, magnify = OverlayScale.current().magnify) / sysScale
    }

    /**
     * Where [popup]'s decoration may paint, root pane coordinates: the strip above its top edge, a little wider
     * for the holly. Null when the cap doesn't fit above the popup (it is pressed against the top of the frame).
     */
    private fun capArea(popup: FoundPopup, pixel: Float): Rectangle? {
        val b = popup.bounds
        // A pixel more everywhere: the canvas rounds its origin to device pixels.
        val capHeight = ceil(popup.kind.capHeight * pixel).toInt() + 1
        if (b.y - capHeight < 0 || b.width <= 0) return null
        if (!popup.kind.holly) return Rectangle(b.x, b.y - capHeight, b.width, capHeight)
        val above = ceil(HOLLY_ABOVE * pixel).toInt() + 1
        val out = ceil(HOLLY_OUT * pixel).toInt() + 1
        val below = ceil((HOLLY_H - HOLLY_ABOVE) * pixel).toInt() + 1
        return Rectangle(b.x, b.y - maxOf(capHeight, above), b.width + out, maxOf(capHeight, above) + below)
    }

    private fun paintCaps(g: Graphics2D) {
        val rootPane = root ?: return
        if (popups.isEmpty()) return
        val pixel = pixelUser(rootPane)
        val magnify = OverlayScale.current().magnify
        val tick = JingleAnimator.getInstance(project).current.tick
        val all = popups.map { it.bounds } + occluders
        for (popup in popups) {
            val area = capArea(popup, pixel) ?: continue
            gate.guard(popup.kind.feature, Unit) { paintCap(g, popup, area, all, magnify, tick) }
        }
    }

    /** Paints [popup]'s cap (and holly) clipped to [area] minus every popup in [all]; the holly may hang on its own popup. */
    private fun paintCap(g: Graphics2D, popup: FoundPopup, area: Rectangle, all: List<Rectangle>, magnify: Float, tick: Long) {
        val kind = popup.kind
        val b = popup.bounds
        fun clipped(allowOwn: Boolean, block: Graphics2D.() -> Unit) {
            val clip = Area(area)
            all.filter { !allowOwn || it != b }.forEach { clip.subtract(Area(it)) }
            val g2 = g.create() as Graphics2D
            try {
                g2.clip(clip)
                g2.block()
            }
            finally {
                g2.dispose()
            }
        }
        clipped(allowOwn = false) {
            PixelCanvas.paint(this, b.x, b.y, magnify = magnify) {
                val width = toLogical(b.width) - kind.left - kind.right
                if (width > 0) drawPopupCap(kind.left, -kind.capHeight, width, kind.capHeight, tick)
            }
        }
        if (kind.holly) clipped(allowOwn = true) {
            PixelCanvas.paint(this, b.x, b.y, magnify = magnify) {
                drawHolly(toLogical(b.width) + HOLLY_OUT - HOLLY_W, -HOLLY_ABOVE, tick)
            }
        }
    }

    override fun dispose() {
        (root?.glassPane as? JComponent)?.let { glassPane -> lastDirty.forEach(glassPane::repaint) }
        lastDirty = emptyList()
        popups = emptyList()
        root = null
        frame = null
    }

    companion object {
        private val FEATURES = listOf(JingleFeature.POPUPS, JingleFeature.CONTEXT_MENUS)

        /** The holly sticks out of the notification's top right corner as in the mockup: `right: -8px; top: -10px`, a third of that. */
        private const val HOLLY_OUT = 3
        private const val HOLLY_ABOVE = 3

        @JvmStatic
        fun getInstance(project: Project): PopupSnow = project.service()
    }
}
