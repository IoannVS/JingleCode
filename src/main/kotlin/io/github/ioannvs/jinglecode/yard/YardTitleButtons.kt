package io.github.ioannvs.jinglecode.yard

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
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

/**
 * Puts the yard's button ([ToggleYardDecorAction]) into the header of every tool window at the right, left of
 * its "⋮" menu, as in the Project and Commit panels (Ivan's call).
 *
 * The public `ToolWindow.setTitleActions` replaces a window's own header buttons (Notifications, AI Assistant
 * and others have some), so the button is added to them instead: the header's toolbar is found by its place,
 * and its first group holds the window's title actions. That leans on how the platform builds the header, so
 * it lives here alone, behind its own switch, and goes through [FeatureGate.guard]: if it breaks, only the
 * button is gone. A window that sets its title actions again drops the button; it comes back on the next
 * change of the tool windows.
 */
internal class YardTitleButtons(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) = installLater()

    override fun toolWindowShown(toolWindow: ToolWindow) = installLater()

    private fun installLater() {
        // After the window has built its content and set its own title actions.
        ApplicationManager.getApplication().invokeLater({ install() }, project.disposed)
    }

    private fun install() {
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.YARD_BUTTON)) return
        val action = ActionManager.getInstance().getAction(ToggleYardDecorAction.ID) ?: return
        gate.guard(JingleFeature.YARD_BUTTON, Unit) {
            val manager = ToolWindowManager.getInstance(project)
            for (id in manager.toolWindowIds) {
                val window = manager.getToolWindow(id) ?: continue
                if (window.anchor != ToolWindowAnchor.RIGHT || !window.isVisible) continue
                val toolbar = headerToolbar(window) ?: continue
                val titleActions = titleActions(toolbar) ?: continue
                if (titleActions.containsAction(action)) continue
                titleActions.add(action)
                toolbar.updateActionsAsync()
            }
        }
    }

    /** The toolbar in [window]'s header: the nearest one around its content placed in a tool window title. */
    private fun headerToolbar(window: ToolWindow): ActionToolbar? {
        var container: Container? = window.component
        repeat(MAX_DEPTH) {
            val parent = container?.parent ?: return null
            val toolbar = UIUtil.uiTraverser(parent).filter(JComponent::class.java)
                .filterMap { it as? ActionToolbar }
                .firstOrNull { it.place == ActionPlaces.TOOLWINDOW_TITLE }
            if (toolbar != null) return toolbar
            container = parent
        }
        return null
    }

    /**
     * The group of the window's own title actions. The header toolbar's group gives the tab list, the content's
     * own actions, then this group, then the common group with "⋮" and "hide" last: it is the one before last.
     */
    private fun titleActions(toolbar: ActionToolbar): DefaultActionGroup? {
        val context = DataManager.getInstance().getDataContext(toolbar.component)
        val event = AnActionEvent.createEvent(context, null, ActionPlaces.TOOLWINDOW_TITLE, ActionUiKind.TOOLBAR, null)
        val children = toolbar.actionGroup.getChildren(event)
        if (children.size < 2) return null
        return children[children.size - 2] as? DefaultActionGroup
    }

    private companion object {
        /** From the content up to the window's decorator, which holds the header too. */
        const val MAX_DEPTH = 6
    }
}
