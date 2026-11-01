package io.github.ioannvs.jinglecode.yard

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowAnchor
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * What the tool windows at the right show, as their button switches it round, in the order of the panels' and
 * the editor's buttons: nothing, only snow, snow and Santa's yard, only the yard (with stars over it).
 * Kept in the settings as two switches, the snow in the right windows and the yard.
 */
internal enum class YardDecor(val snow: Boolean, val yard: Boolean) {
    OFF(snow = false, yard = false),
    SNOW(snow = true, yard = false),
    BOTH(snow = true, yard = true),
    YARD(snow = false, yard = true);

    /** What a click switches to. */
    val next: YardDecor get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The mode the two switches are in. */
        fun of(snow: Boolean, yard: Boolean): YardDecor = entries.first { it.snow == snow && it.yard == yard }
    }
}

/**
 * The button in the headers of the tool windows at the right, left of their "⋮" menu (see [YardTitleButtons]),
 * that goes round [YardDecor]: a garland bulb for nothing, a snowflake for snow, Santa's head in his hat with a
 * snowflake for both, Santa's head for the yard.
 */
internal class ToggleYardDecorAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val gate = FeatureGate.getInstance()
        val window = e.getData(PlatformDataKeys.TOOL_WINDOW)
        e.presentation.isEnabledAndVisible = gate.isActive(JingleFeature.YARD_BUTTON) && FrameGeometry.isIslandsUi() &&
            window?.anchor == ToolWindowAnchor.RIGHT
        val (icon, text) = when (current()) {
            YardDecor.BOTH -> SNOW_AND_SANTA to message("action.yard.both")
            YardDecor.SNOW -> SNOWFLAKE to message("action.yard.snow")
            YardDecor.YARD -> SANTA to message("action.yard.yard")
            YardDecor.OFF -> BULB to message("action.yard.off")
        }
        e.presentation.icon = icon
        e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        val next = current().next
        settings.state.rightSnowEnabled = next.snow
        settings.state.santaYardEnabled = next.yard
        settings.fireChanged()
    }

    private fun current(): YardDecor = JingleCodeSettings.getInstance().state.let { YardDecor.of(it.rightSnowEnabled, it.santaYardEnabled) }

    companion object {
        const val ID = "JingleCode.ToggleYardDecor"
    }
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", ToggleYardDecorAction::class.java)
private val SANTA = IconLoader.getIcon("/icons/santa.svg", ToggleYardDecorAction::class.java)
private val SNOW_AND_SANTA = IconLoader.getIcon("/icons/snow_santa.svg", ToggleYardDecorAction::class.java)
private val BULB = IconLoader.getIcon("/icons/garland_bulb.svg", ToggleYardDecorAction::class.java)
