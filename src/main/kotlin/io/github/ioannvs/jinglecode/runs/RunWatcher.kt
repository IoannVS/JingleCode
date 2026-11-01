package io.github.ioannvs.jinglecode.runs

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEvents
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds of the Build action going on now (see `ProjectTaskWatcher`, loaded only in IDEs that have
 * the Build action). A process started while one goes on is part of it, as Gradle is for a build
 * delegated to it: the build reports once, for both.
 */
@Service(Service.Level.PROJECT)
internal class BuildTasks {
    private val running = AtomicInteger()

    val isRunning: Boolean get() = running.get() > 0

    fun started() {
        running.incrementAndGet()
    }

    fun finished() {
        running.updateAndGet { maxOf(0, it - 1) }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): BuildTasks = project.service()
    }
}

/**
 * Publishes [JingleEvent.RunStarted] when a run configuration's process starts and [JingleEvent.RunFinished] when it
 * ends by itself, in every IDE: exit code 0 is a success, anything else a failure. Processes the user stopped or
 * detached end with [JingleEvent.RunStopped]; the ones a build started don't count at all (the build itself reports).
 */
internal class RunWatcher(private val project: Project) : ExecutionListener {

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (BuildTasks.getInstance(project).isRunning) handler.putUserData(PART_OF_BUILD, true)
        else JingleEvents.publish(project, JingleEvent.RunStarted)
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val stopped = handler.getUserData(ProcessHandler.TERMINATION_REQUESTED) == true
        val partOfBuild = handler.getUserData(PART_OF_BUILD) == true
        thisLogger().info("Jingle Code: '${env.runProfile.name}' ended, exit code $exitCode, stopped $stopped, part of a build $partOfBuild")
        if (partOfBuild) return
        JingleEvents.publish(project, if (stopped) JingleEvent.RunStopped else JingleEvent.RunFinished(succeeded = exitCode == 0))
    }

    private companion object {
        val PART_OF_BUILD = Key.create<Boolean>("JingleCode.partOfBuild")
    }
}
