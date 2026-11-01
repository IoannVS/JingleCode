package io.github.ioannvs.jinglecode.yard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEventListener
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * What Santa's yards react to, as it comes from the event bus, often on a background thread: runs and builds
 * starting and ending, notifications, pushes. The yards take the events on their own clock ([drain]); all visible yards
 * react to each one, whichever window it came from.
 */
@Service(Service.Level.PROJECT)
internal class YardEvents {

    /** An event and when it came, so the yards can tell it is stale after a pause. */
    class Received(val event: JingleEvent, val atNanos: Long)

    private val queue = ConcurrentLinkedQueue<Received>()
    private val active = AtomicInteger()

    /** Runs and builds going on now: while there are any, gifts come down the chute. */
    val running: Boolean get() = active.get() > 0

    fun onEvent(event: JingleEvent) {
        when (event) {
            JingleEvent.RunStarted -> active.incrementAndGet()
            is JingleEvent.RunFinished, JingleEvent.RunStopped -> active.updateAndGet { maxOf(0, it - 1) }
            JingleEvent.Notification, is JingleEvent.Push, JingleEvent.PushRejected -> Unit
            else -> return
        }
        queue += Received(event, System.nanoTime())
        // Nobody takes them while the yard is off or no window is at the right: keep only the latest few.
        while (queue.size > MAX_QUEUED) queue.poll()
    }

    /** The events since the last call, oldest first. */
    fun drain(): List<Received> = generateSequence { queue.poll() }.toList()

    companion object {
        private const val MAX_QUEUED = 32

        @JvmStatic
        fun getInstance(project: Project): YardEvents = project.service()
    }
}

/** Declared in plugin.xml, so it hears runs and notifications even before a yard has been shown. */
internal class YardEventsListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = YardEvents.getInstance(project).onEvent(event)
}
