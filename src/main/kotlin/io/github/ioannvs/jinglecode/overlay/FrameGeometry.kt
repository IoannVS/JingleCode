package io.github.ioannvs.jinglecode.overlay

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where the edges of the panels are. All rectangles are in root pane coordinates (user space).
 *
 * @param vertical lines between side-by-side islands: `x` is the line, `y`/`height` its extent, `width` is 0
 * @param horizontal lines between stacked islands: `y` is the line, `x`/`width` its extent, `height` is 0
 * @param top the top edge of the top row of islands, right under the toolbar: `y` is the line, `x`/`width` its extent
 * @param statusBand free strip between the islands and the status bar content
 * @param occluders things above the panels (popups, sliding tool windows) that must stay clean
 * @param projectPanel the Project tool window, if it is docked and showing its tree
 * @param commitPanel the Commit tool window, if it is docked and showing its tree of changes;
 *   its [ProjectPanel.content] ends where the tree does, above the splitter to the commit message
 */
internal class FrameLayout(
    val vertical: List<Rectangle>,
    val horizontal: List<Rectangle>,
    val top: List<Rectangle>,
    val statusBand: Rectangle?,
    val occluders: List<Rectangle>,
    val projectPanel: ProjectPanel? = null,
    val commitPanel: ProjectPanel? = null,
    /** The bottom of the window, root pane coordinates: falling icicles break there. */
    val bottom: Int = 0,
    /** The tool windows docked at the right, each its own island: Santa's yard stands at their bottom. */
    val rightPanels: List<RightPanel> = emptyList(),
    /** The tool windows docked at the bottom, each its own island: the railway runs along their bottom. */
    val bottomPanels: List<RightPanel> = emptyList(),
)

/**
 * A tool window island docked at the right (or at the bottom, in [FrameLayout.bottomPanels]).
 *
 * @param id the tool window's id
 * @param body the island's rounded body (its bounds without the border), corners of [arc]
 * @param content the window's content under its header, inside the body
 */
internal class RightPanel(val id: String, val body: Rectangle, val arc: Int, val content: Rectangle)

/**
 * A tool window island with a tree: the Project panel or the Commit panel.
 *
 * @param body the island's rounded body (its bounds without the border), corners of [arc]
 * @param content where the tree's scroll pane is, inside the body
 */
internal class ProjectPanel(val body: Rectangle, val arc: Int, val content: Rectangle)

/**
 * Finds the edges of the Islands UI from public API: tool window and editor components, the status bar.
 *
 * This is the one place that depends on how the IDE lays out Swing components. In the Islands UI
 * neighbouring islands touch each other, and the visible gap is their own rounded border, so
 * a gap is the line where two islands meet, and the strip under the toolbar is the top islands' border.
 * If anything here breaks, every decoration of the frame overlay is switched off.
 */
internal object FrameGeometry {
    /** Islands closer than this are neighbours. */
    private const val TOUCH_DISTANCE = 12

    /** Shorter shared edges are not worth a garland. */
    private const val MIN_LENGTH = 48

    /** The tallest thing in the status bar is a 16 px icon. */
    private const val STATUS_ICON = 16

    fun isIslandsUi(): Boolean = JBUI.getInt("Islands", 0) != 0

    /** How round the islands' corners are, user pixels; 0 without the Islands UI. */
    fun islandArc(): Int = if (isIslandsUi()) JBUI.scale(JBUI.getInt("Island.arc", 20)) else 0

    /** The docked and the sliding tool windows showing in [root], and the editors as one more docked island. */
    private class Windows(val docked: List<JComponent>, val sliding: List<JComponent>, val editors: JComponent?) {
        val anchors: List<JComponent> get() = docked + sliding
    }

    private fun windows(project: Project, root: JRootPane): Windows {
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
        val editors = FileEditorManagerEx.getInstanceEx(project).component
            ?.takeIf { it.isShowing && SwingUtilities.getRootPane(it) === root }
            ?.also { docked += it }
        return Windows(docked, sliding, editors)
    }

    /**
     * The rounded body of the editors' island, root pane coordinates, or `null` without the Islands UI.
     * The line numbers reach into the island's border, where the garland hangs; things over them stop here.
     */
    fun editorBody(project: Project, root: JRootPane): Rectangle? {
        if (!isIslandsUi()) return null
        val windows = windows(project, root)
        val editors = windows.editors ?: return null
        return body(boundsIn(islandOf(editors, windows.anchors), root))
    }

    /**
     * The rounded body of the island holding [window] (a tool window's [com.intellij.openapi.wm.ToolWindow.getComponent]),
     * root pane coordinates, or `null` without the Islands UI.
     */
    fun toolWindowBody(project: Project, root: JRootPane, window: JComponent): Rectangle? {
        if (!isIslandsUi() || !window.isShowing || SwingUtilities.getRootPane(window) !== root) return null
        return body(boundsIn(islandOf(window, windows(project, root).anchors), root))
    }

    /**
     * Where a scene at the bottom of [pane] stands, root pane coordinates: the snowman's drift, the village
     * and Santa's yard stand on one line, the bottom of their islands' bodies. A pane reaching down to within
     * [TOUCH_DISTANCE] of its island's bottom (a scroll bar or a padding may be under it) stands there too;
     * a pane ending higher, like an editor split above another one, stands on its own bottom.
     */
    fun ground(pane: Rectangle, body: Rectangle?): Int {
        val bottom = pane.y + pane.height
        if (body == null) return bottom
        val bodyBottom = body.y + body.height
        return if (bodyBottom - bottom <= JBUI.scale(TOUCH_DISTANCE)) bodyBottom else bottom
    }

    private fun body(island: Rectangle): Rectangle {
        val border = JBUI.scale(JBUI.getInt("Island.borderWidth", 6))
        return Rectangle(island.x + border, island.y + border, island.width - 2 * border, island.height - 2 * border)
    }

    fun find(project: Project, root: JRootPane): FrameLayout? {
        if (!isIslandsUi()) return null

        val windows = windows(project, root)
        val docked = windows.docked
        val sliding = windows.sliding
        val toolWindows = ToolWindowManager.getInstance(project)
        val anchors = windows.anchors
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

        return FrameLayout(
            vertical, horizontal, topEdges(islands, touch), statusBand(project, root, islands), occluders,
            projectPanel(project, root, toolWindows, anchors),
            commitPanel(root, toolWindows, anchors),
            bottom = root.height,
            rightPanels = toolWindowPanels(ToolWindowAnchor.RIGHT, root, toolWindows, anchors),
            bottomPanels = toolWindowPanels(ToolWindowAnchor.BOTTOM, root, toolWindows, anchors),
        )
    }

    /** The visible tool windows docked at [anchor], in this frame. */
    private fun toolWindowPanels(anchor: ToolWindowAnchor, root: JRootPane, toolWindows: ToolWindowManager, anchors: List<JComponent>): List<RightPanel> {
        val arc = islandArc()
        return toolWindows.toolWindowIds.mapNotNull { id ->
            val window = toolWindows.getToolWindow(id) ?: return@mapNotNull null
            if (window.anchor != anchor) return@mapNotNull null
            val component = dockedWindow(toolWindows, id, root) ?: return@mapNotNull null
            val body = body(boundsIn(islandOf(component, anchors), root))
            val content = boundsIn(component, root).intersection(body)
            if (content.isEmpty) null else RightPanel(id, body, arc, content)
        }
    }

    /**
     * The docked Project tool window showing its tree.
     * The tree comes from [ProjectView]; the island's shape from the Islands theme (`Island.borderWidth`,
     * `Island.arc`), so things can sit in its rounded bottom corners.
     */
    private fun projectPanel(project: Project, root: JRootPane, toolWindows: ToolWindowManager, anchors: List<JComponent>): ProjectPanel? {
        val component = dockedWindow(toolWindows, ToolWindowId.PROJECT_VIEW, root) ?: return null
        val tree = ProjectView.getInstance(project).currentProjectViewPane?.tree ?: return null
        if (!tree.isShowing || !SwingUtilities.isDescendingFrom(tree, component)) return null
        return treePanel(component, tree, root, anchors)
    }

    /**
     * The docked Commit tool window showing its tree of changes. The tree's class lives in the VCS
     * module, which the plugin doesn't depend on, so it is the biggest tree showing in the window;
     * its scroll pane ends at the splitter above the commit message.
     */
    private fun commitPanel(root: JRootPane, toolWindows: ToolWindowManager, anchors: List<JComponent>): ProjectPanel? {
        val component = dockedWindow(toolWindows, ToolWindowId.COMMIT, root) ?: return null
        val tree = UIUtil.uiTraverser(component).filter(JTree::class.java)
            .filter { it.isShowing && SwingUtilities.getAncestorOfClass(JScrollPane::class.java, it) != null }
            .maxByOrNull { it.visibleRect.height } ?: return null
        return treePanel(component, tree, root, anchors)
    }

    private fun dockedWindow(toolWindows: ToolWindowManager, id: String, root: JRootPane): JComponent? {
        val window = toolWindows.getToolWindow(id) ?: return null
        if (!window.isVisible || window.type != ToolWindowType.DOCKED) return null
        val component = window.component
        if (!component.isShowing || SwingUtilities.getRootPane(component) !== root) return null
        return component
    }

    /** The island holding [component], and where the scroll pane of its [tree] is inside it. */
    private fun treePanel(component: JComponent, tree: JTree, root: JRootPane, anchors: List<JComponent>): ProjectPanel? {
        val scroll = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane ?: return null
        val body = body(boundsIn(islandOf(component, anchors), root))
        val arc = islandArc()
        val content = boundsIn(scroll, root).intersection(body)
        if (content.isEmpty) return null
        return ProjectPanel(body, arc, content)
    }

    /**
     * The top edge of the top row of islands, one line per run of islands that touch each other.
     * Islands start right under the toolbar, so this is where things hang from the toolbar.
     */
    internal fun topEdges(islands: List<Rectangle>, touch: Int): List<Rectangle> {
        if (islands.isEmpty()) return emptyList()
        val top = islands.minOf { it.y }
        val row = islands.filter { it.y <= top + touch }.sortedBy { it.x }
        val edges = ArrayList<Rectangle>()
        var left = row.first().x
        var right = row.first().x + row.first().width
        for (island in row.drop(1)) {
            if (island.x - right <= touch) {
                right = max(right, island.x + island.width)
            }
            else {
                edges += Rectangle(left, top, right - left, 0)
                left = island.x
                right = island.x + island.width
            }
        }
        edges += Rectangle(left, top, right - left, 0)
        return edges
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
    internal fun popupsOver(root: JRootPane): List<Rectangle> {
        val half = root.width.toLong() * root.height / 2
        return root.layeredPane.components
            .filter { it.isVisible && it !== root.contentPane && it.width.toLong() * it.height < half }
            .map { boundsIn(it, root) }
            // The frame header with the toolbar lives there too, across the whole top: the icicles grow into it.
            .filterNot { it.y <= 0 && it.x <= 0 && it.width >= root.width }
    }

    private fun boundsIn(component: Component, root: JRootPane): Rectangle =
        SwingUtilities.convertRectangle(component.parent ?: return component.bounds, component.bounds, root)
}
