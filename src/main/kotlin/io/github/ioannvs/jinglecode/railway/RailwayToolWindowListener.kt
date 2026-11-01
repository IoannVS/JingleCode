package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/** Moves the railway strips after tool windows are shown, hidden, moved or resized. */
internal class RailwayToolWindowListener(private val project: Project) : ToolWindowManagerListener {

    override fun stateChanged(toolWindowManager: ToolWindowManager) = refreshLater()

    override fun toolWindowShown(toolWindow: ToolWindow) = refreshLater()

    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) = refreshLater()

    // After the change is laid out: a window just shown isn't showing yet.
    private fun refreshLater() = ApplicationManager.getApplication().invokeLater(
        { RailwayStrips.getInstance(project).refresh() }, project.disposed)
}

/** The strips for the tool windows open when the project opens. */
internal class RailwayStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater({ RailwayStrips.getInstance(project).refresh() }, project.disposed)
    }
}
