package io.github.ioannvs.jinglecode.toolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.ui.UIUtil
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import java.awt.Container
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Puts the action [actionId] into the header of every tool window docked at [anchor], left of its "⋮" menu,
 * as in the Project and Commit panels (Ivan's call): Santa's yard's button at the right, the railway's at the bottom.
 *
 * The public `ToolWindow.setTitleActions` replaces a window's own header buttons (Notifications, AI Assistant
 * and others have some), so the button is added to them instead: the header's toolbar is found by its place,
 * and its first group holds the window's title actions. That leans on how the platform builds the header, so
 * it lives here alone, behind its own switch ([feature]), and goes through [FeatureGate.guard]: if it breaks,
 * only the button is gone. A window that sets its title actions again drops the button; it comes back on the
 * next change of the tool windows.
 */
internal abstract class ToolWindowTitleButtons(
    private val project: Project,
    private val anchor: ToolWindowAnchor,
    private val feature: JingleFeature,
    private val actionId: String,
) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) = installLater()

    override fun toolWindowShown(toolWindow: ToolWindow) = installLater()

    private fun installLater() {
        // After the window has built its content and set its own title actions.
        ApplicationManager.getApplication().invokeLater({ install() }, project.disposed)
    }

    private fun install() {
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(feature)) return
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        gate.guard(feature, Unit) {
            val manager = ToolWindowManager.getInstance(project)
            for (id in manager.toolWindowIds) {
                val window = manager.getToolWindow(id) ?: continue
                if (window.anchor != anchor || !window.isVisible) continue
                val toolbar = headerToolbar(window) ?: continue
                val titleActions = titleActions(children(toolbar)) ?: continue
                if (titleActions.containsAction(action)) continue
                titleActions.add(action)
                toolbar.updateActionsAsync()
            }
        }
    }

    /**
     * The toolbar in [window]'s header: the nearest one around its content placed in a tool window title whose
     * group starts with the tab list ("TabList"). Windows with tabs (Terminal, Git) have another toolbar in the
     * same place, the row of their tabs' own buttons, and content may have some too: those are not it.
     */
    private fun headerToolbar(window: ToolWindow): ActionToolbar? {
        val content = window.component
        val tabList = ActionManager.getInstance().getAction(TAB_LIST) ?: return null
        var container: Container? = content
        repeat(MAX_DEPTH) {
            val parent = container?.parent ?: return null
            val toolbar = UIUtil.uiTraverser(parent).filter(JComponent::class.java)
                .filterMap { it as? ActionToolbar }
                .firstOrNull {
                    it.place == ActionPlaces.TOOLWINDOW_TITLE && !SwingUtilities.isDescendingFrom(it.component, content) &&
                        children(it).firstOrNull() === tabList
                }
            if (toolbar != null) return toolbar
            container = parent
        }
        return null
    }

    /** What [toolbar]'s group holds now. */
    private fun children(toolbar: ActionToolbar): Array<AnAction> {
        val context = DataManager.getInstance().getDataContext(toolbar.component)
        val event = AnActionEvent.createEvent(context, null, ActionPlaces.TOOLWINDOW_TITLE, ActionUiKind.TOOLBAR, null)
        return toolbar.actionGroup.getChildren(event)
    }

    /**
     * The group of the window's own title actions among the header toolbar's [children]: the tab list, the
     * content's own actions, then this group, then the common group with "⋮" and "hide" last: the one before last.
     */
    private fun titleActions(children: Array<AnAction>): DefaultActionGroup? {
        if (children.size < 2) return null
        return children[children.size - 2] as? DefaultActionGroup
    }

    private companion object {
        /** From the content up to the window's decorator, which holds the header too. */
        const val MAX_DEPTH = 6

        /** The platform's tab list action, first in every tool window header. */
        const val TAB_LIST = "TabList"
    }
}
