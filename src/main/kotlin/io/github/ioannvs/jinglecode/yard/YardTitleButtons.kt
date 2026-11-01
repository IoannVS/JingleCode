package io.github.ioannvs.jinglecode.yard

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.toolbar.ToolWindowTitleButtons

/** Puts the yard's button ([ToggleYardDecorAction]) into the header of every tool window at the right. */
internal class YardTitleButtons(project: Project) :
    ToolWindowTitleButtons(project, ToolWindowAnchor.RIGHT, JingleFeature.YARD_BUTTON, ToggleYardDecorAction.ID)
