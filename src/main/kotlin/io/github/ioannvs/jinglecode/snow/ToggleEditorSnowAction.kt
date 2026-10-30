package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * The snowflake in the editor's inspections widget row: snow on and off, the same switch as in the settings.
 *
 * A plain action, not a toggle: a toggle draws a pressed button background, and this is just a snowflake.
 * Snowing shows it bright, not snowing shows it grey.
 */
internal class ToggleEditorSnowAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Out of season, or the whole plugin is off: no snowflake either.
        e.presentation.isEnabledAndVisible = FeatureGate.getInstance().isPluginActive
        e.presentation.icon = if (isSnowing()) SNOWFLAKE else SNOWFLAKE_OFF
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        settings.state.editorSnowEnabled = !settings.state.editorSnowEnabled
        settings.fireChanged()
    }

    private fun isSnowing(): Boolean = JingleCodeSettings.getInstance().state.editorSnowEnabled
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", ToggleEditorSnowAction::class.java)
private val SNOWFLAKE_OFF = IconLoader.getDisabledIcon(SNOWFLAKE)
