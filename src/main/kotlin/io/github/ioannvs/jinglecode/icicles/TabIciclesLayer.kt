package io.github.ioannvs.jinglecode.icicles

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.ui.JBUI
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.SwingUtilities
import kotlin.math.floor

/**
 * Icicles hanging from the bottom edge of the selected tab of the active editor window, the same as
 * under the toolbar, but with their roots grown a little into the tab, as under a roof. Ivan's call instead of the mockup's snow cap, which ran into those icicles.
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
        val row = row(project) ?: return
        // Inside the tab the roots fade into its background, solid colors, so they grow out of it rather than glare.
        val background = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground() ?: JBUI.CurrentTheme.EditorTabs.background()
        val root = ColorUtil.mix(background, JinglePalette.SNOW, ROOT_SHARE)
        val ledge = ColorUtil.mix(background, JinglePalette.ICE, LEDGE_SHARE)
        // The origin stays on the tab's bottom edge; the roots go up into the tab from there.
        PixelCanvas.paint(g, row.x, row.y, magnify = scale.magnify) {
            val hanging = IcicleFalls.getInstance(project).hanging(row, toLogical(layout.bottom - row.y))
            drawRootedIcicles(toLogical(row.width), (tick % TICK_WRAP).toInt(), root, ledge, hanging = hanging)
        }
    }

    /** The icicles under the active tab as a row hanging from its bottom edge, if there is such a tab. Not guarded. */
    fun row(project: Project): IcicleRow? {
        val tab = activeTab(project) ?: return null
        return IcicleRow(tab.x, tab.y + tab.height, tab.width, IcicleFall.TAB_SHARE, salt = TAB_SALT)
    }

    /** Apart from the toolbar rows' salts. */
    private const val TAB_SALT = 50021

    /** How much snow and ice show through the tab background in the upper roots and in the ledge on the edge. */
    private const val ROOT_SHARE = 0.3
    private const val LEDGE_SHARE = 0.6

    /** Where the icicles hang for [tab]: rooted in its bottom edge and hanging below it, as wide as it. */
    private fun icicles(tab: Rectangle, scale: OverlayScale): Rectangle {
        // One spare pixel for rounding to device pixels.
        val root = scale.user(ICICLE_ROOT + 1)
        return Rectangle(tab.x, tab.y + tab.height - root, tab.width, root + scale.user(ICICLE_MAX + 1))
    }

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
