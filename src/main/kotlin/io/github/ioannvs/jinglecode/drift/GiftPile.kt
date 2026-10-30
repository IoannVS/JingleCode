package io.github.ioannvs.jinglecode.drift

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
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
    private var counted: Int? = if (isGitEnabled()) null else WITHOUT_GIT

    /** Gifts in the pile now (once the sleigh is gone). */
    val count: Int get() = counted ?: 0

    @Volatile
    private var commitAt = 0L

    @Volatile
    private var delivery: Started? = null

    /** Milliseconds since the newest gift began to fall onto the pile, or [Long.MAX_VALUE]. */
    fun dropAgeMs(): Long = ageMs(commitAt)

    /** The sleigh's visit for the last push, while it lasts. */
    fun delivery(): DeliveryView? {
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
        }
    }

    /** One visit at a time: a second push while the sleigh is out doesn't restart it. */
    private fun start(kind: DeliveryKind, gifts: Int) {
        if (delivery() != null) return
        delivery = Started(kind, System.nanoTime(), gifts.coerceIn(0, MAX_GIFTS))
    }

    private class Started(val kind: DeliveryKind, val startedAt: Long, val gifts: Int)

    companion object {
        /** No Git in this IDE or project: a fixed little pile. */
        const val WITHOUT_GIT = 3

        private fun ageMs(at: Long): Long = if (at == 0L) Long.MAX_VALUE else (System.nanoTime() - at) / 1_000_000

        /** Whether the Git plugin is there to count, asked without touching any of its classes. */
        private fun isGitEnabled(): Boolean =
            try {
                val git = PluginManagerCore.getPlugin(PluginId.getId("Git4Idea"))
                git != null && !PluginManagerCore.isDisabled(git.pluginId)
            }
            catch (_: RuntimeException) {
                false // no plugins loaded, as in unit tests
            }

        @JvmStatic
        fun getInstance(project: Project): GiftPile = project.service()
    }
}

/** Declared in plugin.xml, so it hears events even before anything has asked for the pile. */
internal class GiftPileListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = GiftPile.getInstance(project).onEvent(event)
}
