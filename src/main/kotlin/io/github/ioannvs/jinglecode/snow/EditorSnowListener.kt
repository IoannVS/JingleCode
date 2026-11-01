package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

/** Hands code editors to their project's [EditorSnowManager]. */
internal class EditorSnowListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        onEdt { if (!project.isDisposed) EditorSnowManager.getInstance(project).attach(editor) }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        onEdt { if (!project.isDisposed) project.getServiceIfCreated(EditorSnowManager::class.java)?.detach(editor) }
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeLater(action)
    }
}
