package io.github.ioannvs.jinglecode.drift

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.events.CommitSource
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEventListener

/**
 * The pile of gifts in the Project panel's drift: one gift for each of the user's commits not pushed
 * yet. A push sends the sleigh: elves load the gifts and ride off with them. A rejected push brings
 * the sleigh back, and the elves unload.
 *
 * With Git the pile is empty until Git has counted, so a wrong pile doesn't flash up at startup.
 * Without Git in the IDE, or in a project without Git repositories, it has [WITHOUT_GIT] gifts.
 */
@Service(Service.Level.PROJECT)
class GiftPile {
    /** `null` while Git hasn't counted yet. */
    @Volatile
    private var counted: Int? = if (CommitSource.isPresent()) null else WITHOUT_GIT

    /** Gifts in the pile now (once the sleigh is gone). */
    val count: Int get() = counted ?: 0

    @Volatile
    private var commitAt = 0L

    @Volatile
    private var delivery: Started? = null

    /**
     * A visit waiting for the sleigh to come from Santa's yard: when the yard shows, the sleigh first leaves it
     * to the right and only then flies in here from the left. [releaseFromYard] starts it; so does a timeout.
     */
    @Volatile
    private var pending: Started? = null

    /** When Santa's yard last said it shows and will send the sleigh, [System.nanoTime]. */
    @Volatile
    private var yardSeenAt = 0L

    /** Gifts the pile shows: while the sleigh is on its way for them, the ones it comes for are still there. */
    val shown: Int get() = pending?.takeIf { it.kind == DeliveryKind.DELIVER }?.let { maxOf(count, it.gifts) } ?: count

    /** Whether the sleigh is out for this pile: on its way from the yard, or here. */
    val sleighOut: Boolean get() = pending != null || delivery() != null

    /** Santa's yard shows and reacts: call on every step, so a push waits for its sleigh. */
    fun yardIsHere() {
        yardSeenAt = System.nanoTime()
    }

    /** The yard's sleigh has left it (or can't come): the waiting visit starts now. */
    fun releaseFromYard() {
        val waiting = pending ?: return
        pending = null
        delivery = Started(waiting.kind, System.nanoTime(), waiting.gifts)
    }

    /** Milliseconds since the newest gift began to fall onto the pile, or [Long.MAX_VALUE]. */
    fun dropAgeMs(): Long = ageMs(commitAt)

    /** The sleigh's visit for the last push, while it lasts. */
    fun delivery(): DeliveryView? {
        pending?.let { if (ageMs(it.startedAt) > PENDING_MAX_MS) releaseFromYard() }
        val current = delivery ?: return null
        val view = DeliveryView(current.kind, ageMs(current.startedAt), current.gifts)
        return if (view.ageMs <= Delivery.durationMs(view.kind, view.gifts)) view else null
    }

    internal fun onEvent(event: JingleEvent) {
        when (event) {
            is JingleEvent.UnpushedCommits -> counted = event.count
            JingleEvent.Commit -> commitAt = System.nanoTime()
            is JingleEvent.Push -> start(DeliveryKind.DELIVER, event.carried)
            JingleEvent.PushRejected -> start(DeliveryKind.RETURN, count)
            JingleEvent.NoRepositories -> counted = WITHOUT_GIT
            is JingleEvent.RunFinished, JingleEvent.RunStarted, JingleEvent.RunStopped, JingleEvent.Notification -> Unit
        }
    }

    /**
     * One visit at a time: a second push while the sleigh is out doesn't restart it. With Santa's yard showing the
     * visit waits until the yard's sleigh has left ([releaseFromYard]).
     */
    private fun start(kind: DeliveryKind, gifts: Int) {
        if (sleighOut) return
        val visit = Started(kind, System.nanoTime(), gifts.coerceIn(0, MAX_GIFTS))
        if (System.nanoTime() - yardSeenAt < YARD_FRESH_NS) pending = visit else delivery = visit
    }

    private class Started(val kind: DeliveryKind, val startedAt: Long, val gifts: Int)

    companion object {
        /** No Git in this IDE or project: a fixed little pile. */
        const val WITHOUT_GIT = 3

        /** The yard steps about 30 times a second; a yard not heard from for this long is gone or paused. */
        private const val YARD_FRESH_NS = 1_000_000_000L

        /** A visit doesn't wait for the yard's sleigh longer than this (Santa walks to it, it rides off). */
        private const val PENDING_MAX_MS = 12_000L

        private fun ageMs(at: Long): Long = if (at == 0L) Long.MAX_VALUE else (System.nanoTime() - at) / 1_000_000

        @JvmStatic
        fun getInstance(project: Project): GiftPile = project.service()
    }
}

/** Declared in plugin.xml, so it hears events even before anything has asked for the pile. */
internal class GiftPileListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = GiftPile.getInstance(project).onEvent(event)
}
