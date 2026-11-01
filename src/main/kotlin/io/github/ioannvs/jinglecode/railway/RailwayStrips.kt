package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.ui.Painter
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities

/**
 * Keeps a [RailwayBorder] on the content of every visible tool window docked at the bottom.
 *
 * The border is set on [com.intellij.openapi.wm.ToolWindow.getComponent], the public content panel of the
 * window: it lays its content out inside its insets, so the terminal, the console or the Git log end above
 * the strip's lower part. The strip is painted on the glass pane, from edge to edge of the window's island.
 * A window whose content already has a border of its own
 * is left alone. All of this leans on how the window lays out its content, so it goes through
 * [FeatureGate.guard]: if it throws, only the railway is off.
 */
@Service(Service.Level.PROJECT)
internal class RailwayStrips(private val project: Project) : Disposable {

    /** The windows we put a border on, by tool window id. */
    private val installed = HashMap<String, Installed>()

    /** A bottom window with our border: its content panel and the tool window's own component, which its island holds. */
    private class Installed(val component: JComponent, val window: JComponent, val border: RailwayBorder) {
        /** Something moved over the text on the last step: its sky needs one more repaint to wipe it off. */
        var skyDirty = false

        /** [RailwayScene.skyState] as last painted. */
        var skyState = 0
    }

    /**
     * The strips are painted on the frame's glass pane, over the window but under popups: they reach the island's
     * rounded edges outside the content panel, and their top (the upper half of the poles, the wires) and the
     * flying birds lie over the window's text (Ivan's call). The frame overlay paints the ones in docked windows
     * with the Islands UI ([RailwayLayer]), so the bottom windows' snow falls over the trains as over the other
     * scenes; our own painter does the rest (sliding windows, which the overlay keeps clear of, and the classic UI).
     */
    private var root: JRootPane? = null

    private val painter = object : Painter {
        override fun needsRepaint(): Boolean = true
        override fun paint(component: Component, g: Graphics2D) = paintStrips(g, overlay = false)
        override fun addListener(listener: Painter.Listener) = Unit
        override fun removeListener(listener: Painter.Listener) = Unit
    }

    private fun installPainter() {
        if (root != null) return
        val rootPane = WindowManager.getInstance().getFrame(project)?.rootPane ?: return
        IdeGlassPaneUtil.find(rootPane.contentPane).addPainter(rootPane.glassPane, painter, this)
        root = rootPane
    }

    /** Whether the frame overlay paints the strip of tool window [id]. */
    private fun inOverlay(id: String): Boolean =
        FrameGeometry.isIslandsUi() && ToolWindowManager.getInstance(project).getToolWindow(id)?.type == ToolWindowType.DOCKED

    /** Where the overlay's strips and their birds may be, glass pane (root pane) coordinates. */
    fun stripRects(): List<Rectangle> {
        val glass = root?.glassPane ?: return emptyList()
        return installed.mapNotNull { (id, entry) -> if (inOverlay(id)) entry.border.repaintRect(glass, sky = true) else null }
    }

    /**
     * Paints the strips the [overlay] paints, or the others, on the glass pane, never over a popup; each clips
     * itself to its own island.
     */
    fun paintStrips(g: Graphics2D, overlay: Boolean) {
        val rootPane = root ?: return
        val glass = rootPane.glassPane ?: return
        if (installed.isEmpty()) return
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.RAILWAY)) return
        gate.guard(JingleFeature.RAILWAY, Unit) {
            val clip = Area(Rectangle(0, 0, glass.width, glass.height))
            FrameGeometry.popupsOver(rootPane).forEach { clip.subtract(Area(it)) }
            for ((id, entry) in installed) {
                val c = entry.component
                if (!c.isShowing || SwingUtilities.getRootPane(c) !== rootPane || inOverlay(id) != overlay) continue
                entry.border.paint(g, glass, clip)
            }
        }
    }

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { refresh() })
        // Trains move on the smooth clock; it runs only while the railway is on and the IDE is in front.
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
            val glass = root?.glassPane as? JComponent
            for (entry in installed.values) {
                relayout(entry)
                entry.border.options = options
                entry.border.step(dt, options)
                // The lower part every step; the part over the text only when something changes there.
                val busy = entry.border.scene.skyBusy()
                val state = entry.border.scene.skyState()
                val sky = busy || entry.skyDirty || state != entry.skyState
                if (glass != null) entry.border.repaintRect(glass, sky)?.let { glass.repaint(it) }
                entry.skyDirty = busy
                entry.skyState = state
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
            installPainter()
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
        (root?.glassPane as? JComponent)?.let { glass -> border.repaintRect(glass, sky = true)?.let(glass::repaint) }
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
