package io.github.ioannvs.jinglecode.popups

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import io.github.ioannvs.jinglecode.core.JingleFeature
import java.awt.Component
import java.awt.Container
import java.awt.Dialog
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JPopupMenu
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * What gets a snow cap, with the mockup's insets in logical pixels (CSS pixels / 3 in
 * `docs/mockup/jingle-code-windows.html`) and cap height.
 */
internal enum class PopupKind(val feature: JingleFeature, val left: Int, val right: Int, val capHeight: Int, val holly: Boolean) {
    /** JBPopup: Search Everywhere, Go to, Recent Files, documentation (`.popup .cap`: 6 px on each side). */
    POPUP(JingleFeature.POPUPS, left = 2, right = 2, capHeight = 4, holly = false),

    /** A notification balloon (`.notif .cap2`: 10 px left, 40 px right, where the holly hangs). */
    NOTIFICATION(JingleFeature.POPUPS, left = 3, right = 13, capHeight = 3, holly = true),

    /** A context menu or submenu (`.menu .cap`: 4 px on each side). */
    MENU(JingleFeature.CONTEXT_MENUS, left = 1, right = 1, capHeight = 4, holly = false),
}

/** A popup and its bounds in the frame's root pane. */
internal data class FoundPopup(val kind: PopupKind, val bounds: Rectangle)

/**
 * Finds the popups, menus and notification balloons over a project frame. This leans on how Swing and the
 * platform show them (owned windows, the layered pane, client properties behind [PopupUtil] and
 * [JBPopupFactory.getParentBalloonFor]), so call it only through `FeatureGate.guard`.
 */
internal object PopupFinder {

    /**
     * Popups of [kinds] over [root], the root pane of [frame]: heavyweight ones in windows the frame owns
     * (and windows those own, for submenus), lightweight ones in the layered pane.
     * Only balloons of [notifications] count as notifications.
     */
    fun find(root: JRootPane, frame: Window, notifications: Collection<Balloon>, kinds: Set<PopupKind>): List<FoundPopup> {
        if (kinds.isEmpty() || !root.isShowing) return emptyList()
        val found = mutableListOf<FoundPopup>()

        fun visit(owner: Window) {
            for (window in owner.ownedWindows) {
                // A dialog has its own glass pane; its popups are not ours.
                if (!window.isShowing || window is Frame || window is Dialog && window.isModal) continue
                val content = (window as? RootPaneContainer)?.rootPane ?: window
                val kind = classify(content, notifications) ?: continue
                if (kind in kinds) found += FoundPopup(kind, boundsOnScreen(window, root))
                visit(window)
            }
        }
        visit(frame)

        val half = root.width.toLong() * root.height / 2
        for (component in root.layeredPane.components) {
            if (!component.isVisible || component === root.contentPane || component.width.toLong() * component.height >= half) continue
            val kind = classify(component, notifications) ?: continue
            if (kind in kinds) {
                val body = if (kind == PopupKind.NOTIFICATION) balloonBody(component) else component
                found += FoundPopup(kind, SwingUtilities.convertRectangle(body.parent, body.bounds, root))
            }
        }
        return found
    }

    /** A balloon paints its shadow around its content: the visible body is its only child. */
    private fun balloonBody(balloon: Component): Component =
        (balloon as? Container)?.components?.singleOrNull { it.isVisible } ?: balloon

    /**
     * Bounds of every popup, menu and balloon in the layered pane, decorated or not: the caps must not cover them.
     * The frame header lives there too and is not one of them.
     */
    fun occludersIn(root: JRootPane): List<Rectangle> {
        val half = root.width.toLong() * root.height / 2
        return root.layeredPane.components
            .filter { it.isVisible && it !== root.contentPane && it.width.toLong() * it.height < half }
            .mapNotNull { component ->
                when (classify(component, emptyList(), anyBalloon = true)) {
                    null -> null
                    PopupKind.NOTIFICATION -> balloonBody(component)
                    else -> component
                }
            }
            .map { SwingUtilities.convertRectangle(it.parent, it.bounds, root) }
    }

    /** [anyBalloon]: every balloon counts as a notification, not only those of [notifications]. */
    private fun classify(component: Component, notifications: Collection<Balloon>, anyBalloon: Boolean = false): PopupKind? {
        if (findMenu(component, MAX_DEPTH) != null) return PopupKind.MENU
        val leaf = firstLeaf(component)
        val balloon = JBPopupFactory.getInstance().getParentBalloonFor(leaf)
        if (balloon != null) return if (anyBalloon || notifications.any { it === balloon }) PopupKind.NOTIFICATION else null
        // PopupUtil looks at the component and then at its window: walk up only as far as the popup itself.
        var c: Component? = leaf
        while (c != null) {
            if (PopupUtil.getPopupContainerFor(c) != null) return PopupKind.POPUP
            if (c === component) break
            c = c.parent
        }
        return null
    }

    private fun findMenu(c: Component, depth: Int): JPopupMenu? {
        if (c is JPopupMenu) return c
        if (depth == 0 || c !is Container) return null
        for (child in c.components) findMenu(child, depth - 1)?.let { return it }
        return null
    }

    private fun firstLeaf(c: Component): Component {
        var leaf = c
        repeat(MAX_DEPTH) {
            val container = leaf as? Container ?: return leaf
            leaf = container.components.firstOrNull() ?: return leaf
        }
        return leaf
    }

    private fun boundsOnScreen(window: Window, root: JRootPane): Rectangle {
        val p = Point(window.locationOnScreen)
        SwingUtilities.convertPointFromScreen(p, root)
        return Rectangle(p.x, p.y, window.width, window.height)
    }

    private const val MAX_DEPTH = 6
}
