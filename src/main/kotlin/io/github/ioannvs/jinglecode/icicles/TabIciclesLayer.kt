package io.github.ioannvs.jinglecode.icicles

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.ui.JBUI
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.SwingUtilities
import kotlin.math.floor

/**
 * Icicles hanging from the bottom edge of the selected tab of the active editor window, the same as
 * under the toolbar. Ivan's call instead of the mockup's snow cap, which ran into those icicles.
 * In the Islands UI they hang from the tab's rounded background, not from its taller label.
 *
 * Only for tabs above the editor; the icicles hang over its top edge. The tab is found from public API
 * (`FileEditorManager.selectedEditor`, `JBTabs.getTabLabel`), but relies on the editor sitting inside
 * its `JBTabs`; that lookup is guarded, so if it breaks only these icicles go off.
 */
internal object TabIciclesLayer : OverlayLayer {

    override val feature = JingleFeature.TAB_ICICLES

    /** Divisible by 3, the glint period. */
    private const val TICK_WRAP = 1_201_200L

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val tab = FeatureGate.getInstance().guard(feature, null) { activeTab(project) } ?: return emptyList()
        return listOf(icicles(tab, scale))
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val tab = activeTab(project) ?: return
        val area = icicles(tab, scale)
        PixelCanvas.paint(g, area.x, area.y, magnify = scale.magnify) {
            drawIcicles(toLogical(area.width), (tick % TICK_WRAP).toInt())
        }
    }

    /** Where the icicles hang for [tab]: right under its bottom edge, as wide as it. */
    private fun icicles(tab: Rectangle, scale: OverlayScale): Rectangle =
        Rectangle(tab.x, tab.y + tab.height, tab.width, scale.user(ICICLE_MAX + 1))

    /** The selected tab of the active editor window, root pane coordinates, if it is above the editor. */
    private fun activeTab(project: Project): Rectangle? {
        val root = WindowManager.getInstance().getFrame(project)?.rootPane ?: return null
        val editor = FileEditorManager.getInstance(project).selectedEditor?.component ?: return null
        if (!editor.isShowing || SwingUtilities.getRootPane(editor) !== root) return null
        val tabs = tabsOf(editor) ?: return null
        val info = tabs.selectedInfo ?: return null
        val label = tabs.getTabLabel(info) ?: return null
        if (!label.isShowing || label.width <= 0) return null
        val labelBounds = SwingUtilities.convertRectangle(label.parent, label.bounds, root)
        val content = SwingUtilities.convertRectangle(editor.parent, editor.bounds, root)
        // Tabs at the bottom or on a side: nothing to hang from.
        if (labelBounds.y + labelBounds.height > content.y) return null
        return if (isIslandsUi() && label is Container) islandsTab(label, labelBounds) else labelBounds
    }

    private fun isIslandsUi(): Boolean = JBUI.getInt("Islands", 0) != 0

    /**
     * The rounded background the Islands UI paints for a top tab inside its [label] at [bounds], without its
     * rounded corners. Mirrors `IslandsTabPainter.paintTab`: the painter is internal, so its numbers are copied here.
     */
    private fun islandsTab(label: Container, bounds: Rectangle): Rectangle {
        val compact = UISettings.getInstance().compactMode
        val sideInset = JBUIScale.scale(if (compact) 2f else 4f)
        val verticalInset = maxOf(bounds.height - JBUIScale.scale(if (compact) 24f else 28f), JBUIScale.scale(if (compact) 4f else 8f))
        val top = floor(verticalInset / 2).toInt()
        val bottom = top + (bounds.height - verticalInset).toInt()
        // The painter's width: up to the right end of the label's content, plus its right inset.
        val width = (label.components.maxOfOrNull { it.x + it.width } ?: 0) + label.insets.right
        val corner = JBUI.CurrentTheme.MainToolbar.Button.hoverArc().float / 2
        val left = (sideInset + corner).toInt()
        val right = (width - sideInset - corner).toInt()
        if (right <= left || bottom <= top) return bounds
        return Rectangle(bounds.x + left, bounds.y + top, right - left, bottom - top)
    }

    private fun tabsOf(component: Component): JBTabs? {
        var current: Component? = component.parent
        while (current != null) {
            if (current is JBTabs) return current
            current = current.parent
        }
        return null
    }
}
