package io.github.ioannvs.jinglecode.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.messages.Topic
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import io.github.ioannvs.jinglecode.settings.JingleCodeSettingsListener
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CancellationException
import javax.swing.Timer

/**
 * Decides whether a decoration is shown right now: the plugin is on, it is the season, the decoration
 * is switched on and hasn't broken in this session.
 *
 * Tells [TOPIC] whenever the answer may have changed: after the settings change, at midnight
 * (so the season starts and ends by itself) and when a decoration breaks.
 */
@Service(Service.Level.APP)
class FeatureGate : Disposable {

    private val broken: MutableSet<JingleFeature> = ConcurrentHashMap.newKeySet()
    private val midnight = Timer(0) { fireChanged() }.apply { isRepeats = false }

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(JingleCodeSettingsListener.TOPIC, JingleCodeSettingsListener { fireChanged() })
        scheduleMidnight()
    }

    private val settings: JingleCodeSettings.State get() = JingleCodeSettings.getInstance().state

    /** The plugin is on and it is the season. */
    val isPluginActive: Boolean
        get() = settings.enabled && Season.isOn(settings.seasonMode, JingleClock.today())

    fun isActive(feature: JingleFeature): Boolean =
        isPluginActive && feature.isEnabledIn(settings) && feature !in broken

    /**
     * Runs [block] for [feature]. If it throws, [feature] is off until restart and [fallback] is returned.
     * Everything that leans on the IDE's Swing layout or internal API goes through here.
     */
    fun <T> guard(feature: JingleFeature, fallback: T, block: () -> T): T =
        try {
            block()
        }
        catch (e: Exception) {
            if (e is ControlFlowException || e is CancellationException) throw e
            if (broken.add(feature)) {
                thisLogger().warn("Jingle Code: ${feature.name.lowercase()} failed and is off until restart", e)
                // Not right away: we may be inside painting.
                ApplicationManager.getApplication().invokeLater { fireChanged() }
            }
            fallback
        }

    private fun fireChanged() {
        scheduleMidnight()
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).gateChanged()
    }

    /** A second past the next midnight, by [JingleClock]. */
    private fun scheduleMidnight() {
        val now = JingleClock.now()
        val untilMidnight = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
        midnight.initialDelay = (untilMidnight.toMillis() + 1000).toInt()
        midnight.restart()
    }

    override fun dispose() {
        midnight.stop()
    }

    fun interface Listener {
        fun gateChanged()
    }

    companion object {
        @JvmField
        @Topic.AppLevel
        val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)

        @JvmStatic
        fun getInstance(): FeatureGate = service()
    }
}
