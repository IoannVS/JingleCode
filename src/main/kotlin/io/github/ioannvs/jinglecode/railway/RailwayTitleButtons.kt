package io.github.ioannvs.jinglecode.railway

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.toolbar.ToolWindowTitleButtons

/** Puts the railway's button ([ToggleRailwayDecorAction]) into the header of every tool window at the bottom. */
internal class RailwayTitleButtons(project: Project) :
    ToolWindowTitleButtons(project, ToolWindowAnchor.BOTTOM, JingleFeature.RAILWAY_BUTTON, ToggleRailwayDecorAction.ID)
