package io.github.ioannvs.jinglecode.village

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/** Hands code editors to their project's [EditorVillageManager]. */
internal class EditorVillageListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        onEdt { if (!project.isDisposed) EditorVillageManager.getInstance(project).attach(editor) }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        onEdt { if (!project.isDisposed) project.getServiceIfCreated(EditorVillageManager::class.java)?.detach(editor) }
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeLater(action)
    }
}
