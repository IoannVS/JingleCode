package io.github.ioannvs.jinglecode.fireplace

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.ui.JBUI
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.fireplace.FireplaceSprite.drawFireplace
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.settings.JingleCodeConfigurable
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * The little fireplace in the bottom right corner of the IDE window; a click opens the plugin's settings.
 * Out of season or switched off it hides; the status bar's own menu can hide it too.
 */
internal class FireplaceWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ID

    override fun getDisplayName(): String = message("widget.fireplace.name")

    override fun createWidget(project: Project): StatusBarWidget = FireplaceWidget(project)

    companion object {
        const val ID = "JingleCode.Fireplace"
    }
}

private class FireplaceWidget(project: Project) : CustomStatusBarWidget {
    private val component = FireplaceComponent(project)

    override fun ID(): String = FireplaceWidgetFactory.ID

    override fun getComponent(): JComponent = component

    override fun dispose() = Unit
}

/**
 * Subscribes while it is in the status bar, in [addNotify]: the platform creates widgets off the EDT,
 * and the animator is EDT-only.
 */
private class FireplaceComponent(private val project: Project) : JComponent() {
    var frame: Int = 0
    private var subscriptions: Disposable? = null

    override fun addNotify() {
        super.addNotify()
        val disposable = Disposer.newDisposable(JingleAnimator.getInstance(project), "Jingle Code fireplace")
        subscriptions = disposable
        ApplicationManager.getApplication().messageBus.connect(disposable)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { updateVisibility() })
        JingleAnimator.getInstance(project).subscribe(disposable, JingleFeature.FIREPLACE) { f ->
            frame = f.index
            repaint()
        }
        updateVisibility()
    }

    override fun removeNotify() {
        subscriptions?.let(Disposer::dispose)
        subscriptions = null
        super.removeNotify()
    }

    private fun updateVisibility() {
        val visible = FeatureGate.getInstance().isActive(JingleFeature.FIREPLACE)
        if (isVisible != visible) {
            isVisible = visible
            parent?.revalidate()
        }
    }

    init {
        border = JBUI.Borders.empty(0, 4)
        toolTipText = message("widget.fireplace.tooltip")
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && !project.isDisposed) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, JingleCodeConfigurable::class.java)
                }
            }
        })
    }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        return Dimension(
            PixelCanvas.userSize(FireplaceSprite.WIDTH, this, ZOOM) + insets.left + insets.right,
            PixelCanvas.userSize(FireplaceSprite.HEIGHT, this, ZOOM) + insets.top + insets.bottom,
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize

    override fun paintComponent(g: Graphics) {
        // Twice the logical pixel, like the strip in the mockup; once if the status bar is too low for that.
        val zoom = if (height >= PixelCanvas.userSize(FireplaceSprite.HEIGHT, this, ZOOM)) ZOOM else 1
        val w = PixelCanvas.userSize(FireplaceSprite.WIDTH, this, zoom)
        val h = PixelCanvas.userSize(FireplaceSprite.HEIGHT, this, zoom)
        PixelCanvas.paint(g, (width - w) / 2, (height - h) / 2, zoom) { drawFireplace(frame) }
    }

    private companion object {
        const val ZOOM = 2
    }
}
