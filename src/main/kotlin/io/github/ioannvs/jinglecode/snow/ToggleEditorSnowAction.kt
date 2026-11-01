package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * What the editor shows, as the button switches it round, in this order (Ivan's call): nothing, only
 * snow, snow and the village, only the village (with stars over it, as there is no snow). Kept in the
 * settings as their two switches, "Snow falls in the editor" and the village.
 */
internal enum class EditorDecor(val snow: Boolean, val village: Boolean) {
    OFF(snow = false, village = false),
    SNOW(snow = true, village = false),
    BOTH(snow = true, village = true),
    VILLAGE(snow = false, village = true);

    /** What a click switches to. */
    val next: EditorDecor get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The mode the two switches are in. */
        fun of(snow: Boolean, village: Boolean): EditorDecor = entries.first { it.snow == snow && it.village == village }
    }
}

/**
 * The button in the editor's inspections widget row that goes round [EditorDecor]: a garland bulb for
 * nothing, a snowflake for snow, a snowflake with a house for both, a house for the village. The same
 * switches as in the settings.
 *
 * A plain action, not a toggle: a toggle draws a pressed button background, and this is just an icon.
 */
internal class ToggleEditorSnowAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Out of season, or the whole plugin is off: no button either.
        e.presentation.isEnabledAndVisible = FeatureGate.getInstance().isPluginActive
        val (icon, text) = when (current()) {
            EditorDecor.BOTH -> SNOW_AND_HOUSE to message("action.decor.both")
            EditorDecor.SNOW -> SNOWFLAKE to message("action.decor.snow")
            EditorDecor.VILLAGE -> HOUSE to message("action.decor.village")
            EditorDecor.OFF -> BULB to message("action.decor.off")
        }
        e.presentation.icon = icon
        e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        val next = current().next
        settings.state.editorSnowEnabled = next.snow
        settings.state.villageEnabled = next.village
        settings.fireChanged()
    }

    private fun current(): EditorDecor = JingleCodeSettings.getInstance().state.let { EditorDecor.of(it.editorSnowEnabled, it.villageEnabled) }
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", ToggleEditorSnowAction::class.java)
private val HOUSE = IconLoader.getIcon("/icons/house.svg", ToggleEditorSnowAction::class.java)
private val SNOW_AND_HOUSE = IconLoader.getIcon("/icons/snow_village.svg", ToggleEditorSnowAction::class.java)
private val BULB = IconLoader.getIcon("/icons/garland_bulb.svg", ToggleEditorSnowAction::class.java)
