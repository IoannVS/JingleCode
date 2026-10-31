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
 * the strip and the railway never covers them. A window whose content already has a border of its own
 * is left alone. All of this leans on how the window lays out its content, so it goes through
 * [FeatureGate.guard]: if it throws, only the railway is off.
 */
@Service(Service.Level.PROJECT)
internal class RailwayStrips(private val project: Project) : Disposable {

    /** The windows we put a border on, by tool window id. */
    private val installed = HashMap<String, Installed>()

    private class Installed(val component: JComponent, val border: RailwayBorder) {
        /** Birds were in the air on the last step: their sky needs one more repaint to wipe them off. */
        var skyDirty = false
    }

    /**
     * The flying birds leave the strip and cross the window's text (Ivan's call), so they are painted
     * on the frame's glass pane, over the window but under popups, like the frame overlay.
     */
    private var root: JRootPane? = null

    private val skyPainter = object : Painter {
        override fun needsRepaint(): Boolean = true
        override fun paint(component: Component, g: Graphics2D) = paintSky(component, g)
        override fun addListener(listener: Painter.Listener) = Unit
        override fun removeListener(listener: Painter.Listener) = Unit
    }

    private fun installSky() {
        if (root != null) return
        val rootPane = WindowManager.getInstance().getFrame(project)?.rootPane ?: return
        IdeGlassPaneUtil.find(rootPane.contentPane).addPainter(rootPane.glassPane, skyPainter, this)
        root = rootPane
    }

    private fun paintSky(glass: Component, g: Graphics2D) {
        val rootPane = root ?: return
        if (installed.isEmpty() || !JingleCodeSettings.getInstance().state.railwayPoles) return
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.RAILWAY)) return
        gate.guard(JingleFeature.RAILWAY, Unit) {
            val popups = FrameGeometry.popupsOver(rootPane)
            for (entry in installed.values) {
                val c = entry.component
                if (!c.isShowing || SwingUtilities.getRootPane(c) !== rootPane) continue
                // Only over its own window, and never over a popup.
                val clip = Area(SwingUtilities.convertRectangle(c, Rectangle(0, 0, c.width, c.height), glass))
                popups.forEach { clip.subtract(Area(it)) }
                entry.border.paintSky(g, glass, clip)
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
                entry.border.options = options
                entry.border.step(dt, options)
                val flying = options.poles && entry.border.scene.hasFlyingBirds()
                if (glass != null && (flying || entry.skyDirty)) {
                    entry.border.skyRect(glass)?.let { glass.repaint(it) }
                }
                entry.skyDirty = flying
            }
        }
    }

    /** Puts the strips where they belong now; call on the EDT after tool windows change. */
    fun refresh() {
        if (project.isDisposed) return
        val gate = FeatureGate.getInstance()
        val ok = gate.guard(JingleFeature.RAILWAY, false) {
            installSky()
            update(if (gate.isActive(JingleFeature.RAILWAY)) bottomWindows() else emptyMap())
            true
        }
        if (!ok) removeAll()
    }

    /** Visible tool windows at the bottom of this project's frame and their content panels. */
    private fun bottomWindows(): Map<String, JComponent> {
        val manager = ToolWindowManager.getInstance(project)
        val result = HashMap<String, JComponent>()
        for (id in manager.toolWindowIds) {
            val window = manager.getToolWindow(id) ?: continue
            if (!window.isVisible || window.anchor != ToolWindowAnchor.BOTTOM) continue
            if (window.type != ToolWindowType.DOCKED && window.type != ToolWindowType.SLIDING) continue
            val component = window.contentManagerIfCreated?.component ?: continue
            if (!component.isShowing) continue
            result[id] = component
        }
        return result
    }

    private fun update(wanted: Map<String, JComponent>) {
        for ((id, entry) in installed.entries.toList()) {
            if (wanted[id] !== entry.component) {
                uninstall(entry)
                installed -= id
            }
        }
        val options = options()
        for ((id, component) in wanted) {
            val entry = installed[id]
            if (entry != null) {
                entry.border.options = options
                entry.border.relayout()
                continue
            }
            if (component.border != null) continue // someone else's border: don't fight over it
            // Each window its own trains, the same ones every time it is opened.
            val border = RailwayBorder(component, id.hashCode())
            border.options = options
            component.border = border
            installed[id] = Installed(component, border)
            border.relayout()
        }
    }

    private fun uninstall(entry: Installed) {
        (root?.glassPane as? JComponent)?.let { glass -> entry.border.skyRect(glass)?.let(glass::repaint) }
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
