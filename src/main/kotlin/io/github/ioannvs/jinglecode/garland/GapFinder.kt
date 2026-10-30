package io.github.ioannvs.jinglecode.garland

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where the gaps between panels are. All rectangles are in root pane coordinates (user space).
 *
 * @param vertical lines between side-by-side islands: `x` is the line, `y`/`height` its extent, `width` is 0
 * @param horizontal lines between stacked islands: `y` is the line, `x`/`width` its extent, `height` is 0
 * @param statusBand free strip between the islands and the status bar content
 * @param occluders things above the gaps (popups, sliding tool windows) that must stay clean
 */
internal class GapLayout(
    val vertical: List<Rectangle>,
    val horizontal: List<Rectangle>,
    val statusBand: Rectangle?,
    val occluders: List<Rectangle>,
) {
    /** Area to repaint on every frame, a little wider than the lines. */
    fun dirtyRects(margin: Int): List<Rectangle> = buildList {
        vertical.forEach { add(Rectangle(it.x - margin, it.y, 2 * margin, it.height)) }
        horizontal.forEach { add(Rectangle(it.x, it.y - margin, it.width, 2 * margin)) }
        statusBand?.let { add(Rectangle(it.x, it.y - margin, it.width, it.height + 2 * margin)) }
    }

    val isEmpty: Boolean get() = vertical.isEmpty() && horizontal.isEmpty() && statusBand == null
}

/**
 * Finds the gaps of the Islands UI from public API: tool window and editor components, the status bar.
 *
 * This is the one place that depends on how the IDE lays out Swing components. In the Islands UI
 * neighbouring islands touch each other, and the visible gap is their own rounded border, so
 * a gap is the line where two islands meet. If anything here breaks, the garland is switched off.
 */
internal object GapFinder {
    /** Islands closer than this are neighbours. */
    private const val TOUCH_DISTANCE = 12

    /** Shorter shared edges are not worth a garland. */
    private const val MIN_LENGTH = 48

    /** The tallest thing in the status bar is a 16 px icon. */
    private const val STATUS_ICON = 16

    private fun isIslandsUi(): Boolean = JBUI.getInt("Islands", 0) != 0

    fun find(project: Project, root: JRootPane): GapLayout? {
        if (!isIslandsUi()) return null

        val docked = ArrayList<JComponent>()
        val sliding = ArrayList<JComponent>()
        val toolWindows = ToolWindowManager.getInstance(project)
        for (id in toolWindows.toolWindowIds) {
            val window = toolWindows.getToolWindow(id) ?: continue
            if (!window.isVisible) continue
            val component = window.component
            if (!component.isShowing || SwingUtilities.getRootPane(component) !== root) continue
            when (window.type) {
                ToolWindowType.DOCKED -> docked += component
                ToolWindowType.SLIDING -> sliding += component
                else -> Unit // floating and windowed ones live in their own windows
            }
        }
        FileEditorManagerEx.getInstanceEx(project).component
            ?.takeIf { it.isShowing && SwingUtilities.getRootPane(it) === root }
            ?.let { docked += it }

        val anchors = docked + sliding
        val islands = docked.map { boundsIn(islandOf(it, anchors), root) }
        val occluders = sliding.map { boundsIn(islandOf(it, anchors), root) } + popupsOver(root)

        val touch = JBUI.scale(TOUCH_DISTANCE)
        val minLength = JBUI.scale(MIN_LENGTH)
        val vertical = ArrayList<Rectangle>()
        val horizontal = ArrayList<Rectangle>()
        for (a in islands) {
            for (b in islands) {
                if (a === b) continue
                // a is left of b
                if (abs(b.x - (a.x + a.width)) <= touch) {
                    val top = max(a.y, b.y)
                    val bottom = min(a.y + a.height, b.y + b.height)
                    if (bottom - top >= minLength) vertical += Rectangle((a.x + a.width + b.x) / 2, top, 0, bottom - top)
                }
                // a is above b
                if (abs(b.y - (a.y + a.height)) <= touch) {
                    val left = max(a.x, b.x)
                    val right = min(a.x + a.width, b.x + b.width)
                    if (right - left >= minLength) horizontal += Rectangle(left, (a.y + a.height + b.y) / 2, right - left, 0)
                }
            }
        }

        return GapLayout(vertical, horizontal, statusBand(project, root, islands), occluders)
    }

    /**
     * The island holding [content]: its topmost ancestor that holds no other island.
     * For a tool window this includes its header; for the editor, all splitters.
     */
    private fun islandOf(content: JComponent, anchors: List<JComponent>): Component {
        var current: Component = content
        while (true) {
            val parent = current.parent ?: return current
            if (parent is JRootPane || anchors.any { it !== content && SwingUtilities.isDescendingFrom(it, parent) }) {
                return current
            }
            current = parent
        }
    }

    /** The strip above the status bar content, under the islands. */
    private fun statusBand(project: Project, root: JRootPane, islands: List<Rectangle>): Rectangle? {
        val statusBar = WindowManager.getInstance().getStatusBar(project)?.component ?: return null
        if (!statusBar.isShowing || SwingUtilities.getRootPane(statusBar) !== root || islands.isEmpty()) return null
        val bar = boundsIn(statusBar, root)
        val insets = statusBar.insets
        // From just under the rounded island bottoms down to where the centered 16 px icons begin.
        val top = bar.y - JBUI.scale(1)
        val contentHeight = bar.height - insets.top - insets.bottom
        val bottom = bar.y + insets.top + (contentHeight - JBUI.scale(STATUS_ICON)) / 2
        if (bottom <= top) return null
        return Rectangle(bar.x + insets.left, top, bar.width - insets.left - insets.right, bottom - top)
    }

    /**
     * Lightweight popups, balloons and the like in the frame's layered pane.
     * Overlays as big as half the frame are transparent layers, not popups: ignore them.
     */
    private fun popupsOver(root: JRootPane): List<Rectangle> {
        val half = root.width.toLong() * root.height / 2
        return root.layeredPane.components
            .filter { it.isVisible && it !== root.contentPane && it.width.toLong() * it.height < half }
            .map { boundsIn(it, root) }
    }

    private fun boundsIn(component: Component, root: JRootPane): Rectangle =
        SwingUtilities.convertRectangle(component.parent ?: return component.bounds, component.bounds, root)
}
