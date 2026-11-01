package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider

/**
 * The snowflake button in the editor's inspections widget row, left of the error and warning
 * counters, where Reader Mode puts its button. Only in code editors.
 */
internal class EditorSnowWidgetActionProvider : InspectionWidgetActionProvider {
    override fun createAction(editor: Editor): AnAction? {
        if (editor.project == null || editor.editorKind != EditorKind.MAIN_EDITOR) return null
        return ActionManager.getInstance().getAction(ACTION_ID)
    }

    private companion object {
        const val ACTION_ID = "JingleCode.ToggleEditorSnow"
    }
}
