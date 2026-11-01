package io.github.ioannvs.jinglecode.runs

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEvents

/**
 * Publishes [JingleEvent.RunFinished] for the Build action (Build Project, Build Module, Rebuild),
 * which builds without a run configuration, by itself or through Gradle or Maven, and [JingleEvent.RunStarted] when
 * it starts. A cancelled build ends with [JingleEvent.RunStopped]. Loaded only in IDEs with `com.intellij.modules.lang` (`jingle-code-lang.xml`).
 */
internal class ProjectTaskWatcher(private val project: Project) : ProjectTaskListener {

    override fun started(context: ProjectTaskContext) {
        BuildTasks.getInstance(project).started()
        JingleEvents.publish(project, JingleEvent.RunStarted)
    }

    override fun finished(result: ProjectTaskManager.Result) {
        BuildTasks.getInstance(project).finished()
        JingleEvents.publish(project, if (result.isAborted) JingleEvent.RunStopped else JingleEvent.RunFinished(succeeded = !result.hasErrors()))
    }
}
