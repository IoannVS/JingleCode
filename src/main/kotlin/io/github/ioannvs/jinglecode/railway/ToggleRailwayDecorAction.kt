package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowAnchor
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * What the tool windows at the bottom show, as their button switches it round, in the order of the other
 * buttons: nothing, only snow, snow and the railway, only the railway. Kept in the settings as two switches,
 * the snow in the bottom windows and the railway.
 */
internal enum class RailwayDecor(val snow: Boolean, val railway: Boolean) {
    OFF(snow = false, railway = false),
    SNOW(snow = true, railway = false),
    BOTH(snow = true, railway = true),
    RAILWAY(snow = false, railway = true);

    /** What a click switches to. */
    val next: RailwayDecor get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The mode the two switches are in. */
        fun of(snow: Boolean, railway: Boolean): RailwayDecor = entries.first { it.snow == snow && it.railway == railway }
    }
}

/**
 * The button in the headers of the tool windows at the bottom, left of their "⋮" menu (see [RailwayTitleButtons]),
 * that goes round [RailwayDecor]: a garland bulb for nothing, a snowflake for snow, a snowflake with the
 * engine's cab for both, the cab for the railway.
 */
internal class ToggleRailwayDecorAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val window = e.getData(PlatformDataKeys.TOOL_WINDOW)
        e.presentation.isEnabledAndVisible = FeatureGate.getInstance().isActive(JingleFeature.RAILWAY_BUTTON) &&
            window?.anchor == ToolWindowAnchor.BOTTOM
        val (icon, text) = when (current()) {
            RailwayDecor.BOTH -> SNOW_AND_CAB to message("action.railway.both")
            RailwayDecor.SNOW -> SNOWFLAKE to message("action.railway.snow")
            RailwayDecor.RAILWAY -> CAB to message("action.railway.railway")
            RailwayDecor.OFF -> BULB to message("action.railway.off")
        }
        e.presentation.icon = icon
        e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        val next = current().next
        settings.state.bottomSnowEnabled = next.snow
        settings.state.railwayEnabled = next.railway
        settings.fireChanged()
    }

    private fun current(): RailwayDecor =
        JingleCodeSettings.getInstance().state.let { RailwayDecor.of(it.bottomSnowEnabled, it.railwayEnabled) }

    companion object {
        const val ID = "JingleCode.ToggleRailwayDecor"
    }
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", ToggleRailwayDecorAction::class.java)
private val CAB = IconLoader.getIcon("/icons/locomotive.svg", ToggleRailwayDecorAction::class.java)
private val SNOW_AND_CAB = IconLoader.getIcon("/icons/snow_locomotive.svg", ToggleRailwayDecorAction::class.java)
private val BULB = IconLoader.getIcon("/icons/garland_bulb.svg", ToggleRailwayDecorAction::class.java)
