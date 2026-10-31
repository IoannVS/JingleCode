package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * Puts the panel snow button into the Commit panel's header, left of its "⋮" menu, where the Project panel
 * has it too (Ivan's call). The Commit panel has no action group there and no title actions of its own,
 * so they are set when it is registered (it comes and goes with the VCS setup).
 */
internal class CommitTitleButton : ToolWindowManagerListener {

    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
        if (ToolWindowId.COMMIT !in ids) return
        val window = toolWindowManager.getToolWindow(ToolWindowId.COMMIT) ?: return
        val action = ActionManager.getInstance().getAction(ACTION_ID) ?: return
        window.setTitleActions(listOf(action))
    }

    private companion object {
        const val ACTION_ID = "JingleCode.TogglePanelSnow"
    }
}
