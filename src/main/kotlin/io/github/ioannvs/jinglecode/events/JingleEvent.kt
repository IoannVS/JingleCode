package io.github.ioannvs.jinglecode.events

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/**
 * Something that happened in the IDE that decorations react to. Sources (Git, runs and builds)
 * publish these on the project's message bus; decorations listen and know nothing about the sources.
 */
sealed interface JingleEvent {
    /** The user's commits not pushed yet, in all of the project's repositories; sent after every change. */
    data class UnpushedCommits(val count: Int) : JingleEvent

    /** The user has just made a commit (from the IDE or a terminal). */
    data object Commit : JingleEvent

    /** A push went through (from the IDE or a terminal); [carried] commits were waiting before it. */
    data class Push(val carried: Int) : JingleEvent

    /** A push from the IDE was rejected or failed: nothing left. */
    data object PushRejected : JingleEvent

    /** Git is there, but the project has no Git repositories: there are no commits to count. */
    data object NoRepositories : JingleEvent

    /**
     * Something the user ran or built in the IDE ended by itself, not stopped: any run configuration
     * (tests and Debug too) or the Build action. [succeeded] if it exited with 0 or built without errors.
     */
    data class RunFinished(val succeeded: Boolean) : JingleEvent
}

fun interface JingleEventListener {
    /** Called on the thread that published the event, often a background one. */
    fun onEvent(event: JingleEvent)
}

object JingleEvents {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<JingleEventListener> = Topic(JingleEventListener::class.java, Topic.BroadcastDirection.NONE)

    fun publish(project: Project, event: JingleEvent) {
        if (!project.isDisposed) project.messageBus.syncPublisher(TOPIC).onEvent(event)
    }
}
