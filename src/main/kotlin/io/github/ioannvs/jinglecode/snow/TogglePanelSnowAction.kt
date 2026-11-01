package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowId
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.overlay.FrameGeometry
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * What the Project and Commit panels show, as their button switches it round, in this order (Ivan's call):
 * nothing, only snow, snow and the snowman's scene (without its own flakes), only the scene (with them).
 * Kept in the settings as their two switches, the panel snow and the drift; both panels share them.
 */
internal enum class PanelDecor(val snow: Boolean, val scene: Boolean) {
    OFF(snow = false, scene = false),
    SNOW(snow = true, scene = false),
    BOTH(snow = true, scene = true),
    SCENE(snow = false, scene = true);

    /** What a click switches to. */
    val next: PanelDecor get() = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The mode the two switches are in. */
        fun of(snow: Boolean, scene: Boolean): PanelDecor = entries.first { it.snow == snow && it.scene == scene }
    }
}

/**
 * The button in the headers of the Project and Commit panels, left of their "⋮" menu, that goes round [PanelDecor]:
 * a garland bulb for nothing, a snowflake for snow, a snowflake with a snowman for both, a snowman for the scene.
 * Like the editor's button, a plain action, just an icon.
 */
internal class TogglePanelSnowAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        // Only where the scene stands.
        val window = e.getData(PlatformDataKeys.TOOL_WINDOW)?.id
        e.presentation.isEnabledAndVisible = FeatureGate.getInstance().isPluginActive &&
            FrameGeometry.isIslandsUi() && (window == ToolWindowId.PROJECT_VIEW || window == ToolWindowId.COMMIT)
        val (icon, text) = when (current()) {
            PanelDecor.BOTH -> SNOW_AND_SNOWMAN to message("action.panel.both")
            PanelDecor.SNOW -> SNOWFLAKE to message("action.panel.snow")
            PanelDecor.SCENE -> SNOWMAN to message("action.panel.scene")
            PanelDecor.OFF -> BULB to message("action.panel.off")
        }
        e.presentation.icon = icon
        e.presentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = JingleCodeSettings.getInstance()
        val next = current().next
        settings.state.panelSnowEnabled = next.snow
        settings.state.driftEnabled = next.scene
        settings.fireChanged()
    }

    private fun current(): PanelDecor = JingleCodeSettings.getInstance().state.let { PanelDecor.of(it.panelSnowEnabled, it.driftEnabled) }
}

private val SNOWFLAKE = IconLoader.getIcon("/icons/snowflake.svg", TogglePanelSnowAction::class.java)
private val SNOWMAN = IconLoader.getIcon("/icons/snowman.svg", TogglePanelSnowAction::class.java)
private val SNOW_AND_SNOWMAN = IconLoader.getIcon("/icons/snow_snowman.svg", TogglePanelSnowAction::class.java)
private val BULB = IconLoader.getIcon("/icons/garland_bulb.svg", TogglePanelSnowAction::class.java)
