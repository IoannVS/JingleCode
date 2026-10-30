package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * The button in the editor's inspections widget row that switches between snow falling in the editor
 * (a snowflake) and stars twinkling over the village (a house): the same switch as "Snow falls in the
 * editor" in the settings.
 *
 * A plain action, not a toggle: a toggle draws a pressed button background, and this is just an icon.
 */
internal class ToggleEditorSnowAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Out of season, or the whole plugin is off: no button either.
        e.presentation.isEnabledAndVisible = FeatureGate.getInstance().isPluginActive
        val snowing = isSnowing()
        e.presentation.icon = if (snowing) SNOWFLAKE else HOUSE
        e.presentation.text = message(if (snowing) "action.snow.to.stars" else "action.stars.to.snow")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        settings.state.editorSnowEnabled = !settings.state.editorSnowEnabled
        settings.fireChanged()
    }

    private fun isSnowing(): Boolean = JingleCodeSettings.getInstance().state.editorSnowEnabled
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", ToggleEditorSnowAction::class.java)
private val HOUSE = IconLoader.getIcon("/icons/house.svg", ToggleEditorSnowAction::class.java)
