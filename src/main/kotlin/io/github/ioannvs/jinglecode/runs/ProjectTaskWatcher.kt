package io.github.ioannvs.jinglecode.runs

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEvents

/**
 * Publishes [JingleEvent.RunFinished] for the Build action (Build Project, Build Module, Rebuild),
 * which builds without a run configuration, by itself or through Gradle or Maven. A cancelled build
 * doesn't count. Loaded only in IDEs with `com.intellij.modules.lang` (`jingle-code-lang.xml`).
 */
internal class ProjectTaskWatcher(private val project: Project) : ProjectTaskListener {

    override fun started(context: ProjectTaskContext) = BuildTasks.getInstance(project).started()

    override fun finished(result: ProjectTaskManager.Result) {
        BuildTasks.getInstance(project).finished()
        if (!result.isAborted) JingleEvents.publish(project, JingleEvent.RunFinished(succeeded = !result.hasErrors()))
    }
}
