package io.github.ioannvs.jinglecode.yeti

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tells [YetiWatch] about typing in the project's editors: when it was (he doesn't come to someone typing),
 * a change coming to his line (it scares him off), and `yeti` (`йети`) just typed in a comment (it calls him).
 * Only typing calls him: not pasting, not undo, not opening a file that has it already.
 */
internal class YetiTyping(private val project: Project) : DocumentListener {

    private val watch: YetiWatch? get() = if (project.isDisposed) null else project.getServiceIfCreated(YetiWatch::class.java)

    override fun beforeDocumentChange(event: DocumentEvent) {
        if (!ApplicationManager.getApplication().isDispatchThread || !isOurs(event)) return
        watch?.beforeChange(event)
    }

    override fun documentChanged(event: DocumentEvent) {
        if (!ApplicationManager.getApplication().isDispatchThread || !isOurs(event)) return
        val watch = watch ?: return
        watch.edited()
        if (event.newLength !in 1..2) return
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.YETI)) return
        gate.guard(JingleFeature.YETI, Unit) {
            if (UndoManager.getInstance(project).isUndoOrRedoInProgress) return@guard
            val document = event.document
            val end = event.offset + event.newLength
            val lineStart = document.getLineStartOffset(document.getLineNumber(end))
            if (!YetiPhrase.matches(document.immutableCharSequence.subSequence(lineStart, end))) return@guard
            val editors = EditorFactory.getInstance().getEditors(document, project).filter { it.editorKind == EditorKind.MAIN_EDITOR }
            val editor = editors.firstOrNull { it.contentComponent.hasFocus() } ?: editors.firstOrNull() ?: return@guard
            val documents = PsiDocumentManager.getInstance(project)
            documents.performForCommittedDocument(document) {
                if (project.isDisposed || editor.isDisposed || end > document.textLength) return@performForCommittedDocument
                gate.guard(JingleFeature.YETI, Unit) {
                    val element = documents.getPsiFile(document)?.findElementAt(end - 1)
                    if (PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false) != null) watch.called(editor, end - YetiPhrase.LENGTH)
                }
            }
        }
    }

    private fun isOurs(event: DocumentEvent): Boolean =
        !project.isDisposed && EditorFactory.getInstance().getEditors(event.document, project).isNotEmpty()
}

/** Starts the yeti's watch on the EDT (it runs on the animator's frames) and listens to typing for it. */
internal class YetiStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val watch = withContext(Dispatchers.EDT) { if (project.isDisposed) null else YetiWatch.getInstance(project) } ?: return
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(YetiTyping(project), watch)
    }
}
