package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Area
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * Keeps a [RailwayBorder] on the content of every visible tool window docked at the bottom.
 *
 * The border is set on [com.intellij.openapi.wm.ToolWindow.getComponent], the public content panel of the
 * window: it lays its content out inside its insets, so the terminal, the console or the Git log end above
 * the strip's lower part. The strip is painted on a pane of its own over the window, from edge to edge of its island.
 * A window whose content already has a border of its own
 * is left alone. All of this leans on how the window lays out its content, so it goes through
 * [FeatureGate.guard]: if it throws, only the railway is off.
 */
@Service(Service.Level.PROJECT)
internal class RailwayStrips(private val project: Project) : Disposable {

    /** The windows we put a border on, by tool window id. */
    private val installed = HashMap<String, Installed>()

    /** A bottom window with our border: its content panel and the tool window's own component, which its island holds. */
    private class Installed(val component: JComponent, val window: JComponent, val border: RailwayBorder)

    /**
     * The strips are painted on a pane of their own in the frame's layered pane, over the tool windows but under
     * popups and notification balloons: those open over the trains as over any other content, with their rounded
     * corners and shadows, and nothing is cut out of the strip for them. The strips reach the island's rounded edges
     * outside the content panel, and their top (the upper half of the poles, the wires) and the flying birds lie over
     * the window's text (Ivan's call). The overlay's snow is on the glass pane above: it falls over the trains.
     */
    private var root: JRootPane? = null
    private var pane: StripsPane? = null

    /** As big as the layered pane, transparent and deaf to the mouse: it only paints the strips. */
    private inner class StripsPane : JComponent() {
        init {
            isOpaque = false
            isFocusable = false
        }

        override fun contains(x: Int, y: Int): Boolean = false

        override fun paintComponent(g: Graphics) = paintStrips(g as Graphics2D)
    }

    private fun installPane() {
        if (pane != null) return
        val rootPane = WindowManager.getInstance().getFrame(project)?.rootPane ?: return
        val layered = rootPane.layeredPane
        val strips = StripsPane()
        // The layered pane lays nothing out: follow its size.
        val resize = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = strips.setBounds(0, 0, layered.width, layered.height)
        }
        strips.setBounds(0, 0, layered.width, layered.height)
        layered.addComponentListener(resize)
        layered.add(strips, JLayeredPane.PALETTE_LAYER)
        Disposer.register(this) {
            layered.removeComponentListener(resize)
            layered.remove(strips)
            layered.repaint(strips.bounds)
        }
        root = rootPane
        pane = strips
    }

    /**
     * Paints the strips on the [pane], each clipped to its own island and clear of the sliding windows over it.
     * The pane covers the whole frame, so this comes with every repaint in it (the caret, the editor's snow):
     * strips that repaint doesn't touch are skipped before anything else.
     */
    private fun paintStrips(g: Graphics2D) {
        val rootPane = root ?: return
        val strips = pane ?: return
        if (installed.isEmpty()) return
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.RAILWAY)) return
        gate.guard(JingleFeature.RAILWAY, Unit) {
            for (entry in installed.values) {
                val rect = entry.border.repaintRect(strips) ?: continue
                if (!g.hitClip(rect.x, rect.y, rect.width, rect.height)) continue
                val c = entry.component
                if (!c.isShowing || SwingUtilities.getRootPane(c) !== rootPane) continue
                val clip = Area(Rectangle(0, 0, strips.width, strips.height))
                FrameGeometry.slidingIslands(project, rootPane, except = entry.window)
                    .forEach { clip.subtract(Area(SwingUtilities.convertRectangle(rootPane, it, strips))) }
                entry.border.paint(g, strips, clip)
            }
        }
    }

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { refresh() })
        // Trains move on the smooth clock; it runs only while the railway is on and the IDE is in front.
        // At 30 a second: a train is on the track most of the time, and at 15 its 26 pixels a second step 2, 2, 1 and judder (Ivan).
        JingleAnimator.getInstance(project).subscribeMotion(this, JingleFeature.RAILWAY) { dt -> move(dt) }
    }

    private fun options(): RailwayOptions {
        val state = JingleCodeSettings.getInstance().state
        return RailwayOptions(state.trainFrequency, state.railwayResidents, state.railwayPoles)
    }

    private fun move(dt: Double) {
        if (installed.isEmpty()) return
        val options = options()
        FeatureGate.getInstance().guard(JingleFeature.RAILWAY, Unit) {
            val strips = pane
            for (entry in installed.values) {
                relayout(entry)
                entry.border.options = options
                entry.border.step(dt, options)
                // Only where something changed, each part on its own: `repaint` would merge them into one across
                // the window, and with the train always somewhere on the track that is most of the strip again.
                if (strips != null && strips.isShowing) entry.border.damageRects(strips).forEach(strips::paintImmediately)
            }
        }
    }

    /** Gives [entry]'s border its island as it is now. */
    private fun relayout(entry: Installed) {
        val rootPane = root
        val body = rootPane?.let { FrameGeometry.toolWindowBody(project, it, entry.window) }
        entry.border.relayout(rootPane, body, FrameGeometry.islandArc())
    }

    /** Puts the strips where they belong now; call on the EDT after tool windows change. */
    fun refresh() {
        if (project.isDisposed) return
        val gate = FeatureGate.getInstance()
        val ok = gate.guard(JingleFeature.RAILWAY, false) {
            installPane()
            update(if (gate.isActive(JingleFeature.RAILWAY)) bottomWindows() else emptyMap())
            true
        }
        if (!ok) removeAll()
    }

    /** Visible tool windows at the bottom of this project's frame: their content panels and their own components. */
    private fun bottomWindows(): Map<String, Pair<JComponent, JComponent>> {
        val manager = ToolWindowManager.getInstance(project)
        val result = HashMap<String, Pair<JComponent, JComponent>>()
        for (id in manager.toolWindowIds) {
            val window = manager.getToolWindow(id) ?: continue
            if (!window.isVisible || window.anchor != ToolWindowAnchor.BOTTOM) continue
            if (window.type != ToolWindowType.DOCKED && window.type != ToolWindowType.SLIDING) continue
            val component = window.contentManagerIfCreated?.component ?: continue
            if (!component.isShowing) continue
            result[id] = component to window.component
        }
        return result
    }

    private fun update(wanted: Map<String, Pair<JComponent, JComponent>>) {
        for ((id, entry) in installed.entries.toList()) {
            if (wanted[id]?.first !== entry.component) {
                uninstall(entry)
                installed -= id
            }
        }
        val options = options()
        for ((id, components) in wanted) {
            val (component, window) = components
            val existing = installed[id]
            if (existing != null) {
                existing.border.options = options
                relayout(existing)
                repaintAll(existing.border)
                continue
            }
            if (component.border != null) continue // someone else's border: don't fight over it
            // Each window its own trains, the same ones every time it is opened.
            val border = RailwayBorder(component, id.hashCode())
            border.options = options
            component.border = border
            val entry = Installed(component, window, border)
            installed[id] = entry
            relayout(entry)
            repaintAll(border)
        }
    }

    private fun repaintAll(border: RailwayBorder) {
        pane?.let { strips -> border.repaintRect(strips)?.let(strips::repaint) }
    }

    private fun uninstall(entry: Installed) {
        repaintAll(entry.border)
        if (entry.component.border === entry.border) {
            entry.component.border = null
            entry.component.revalidate()
            entry.component.repaint()
        }
        entry.border.dispose()
    }

    private fun removeAll() {
        installed.values.forEach(::uninstall)
        installed.clear()
    }

    override fun dispose() = removeAll()

    companion object {
        @JvmStatic
        fun getInstance(project: Project): RailwayStrips = project.service()
    }
}
