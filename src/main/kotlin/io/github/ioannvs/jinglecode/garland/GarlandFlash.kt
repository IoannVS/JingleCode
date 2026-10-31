package io.github.ioannvs.jinglecode.garland

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEventListener
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color
import kotlin.math.floor

/** A short override of the garland's own colors, for an event, over any [GarlandMode]. */
internal sealed interface GarlandFlash {
    /** Color of bulb [i] of the line's [count] at animation tick [t], or `null` to keep the mode's own. */
    fun bulbColor(i: Int, count: Int, t: Int): Color?

    /**
     * A run failed: every bulb blinks in reds only, a few shades of them, and now and then goes dark
     * for a frame. [salt] keeps neighbouring lines from blinking alike.
     */
    class Alarm(private val salt: Int) : GarlandFlash {
        override fun bulbColor(i: Int, count: Int, t: Int): Color {
            val bulb = i + 31 * salt
            if (hash(bulb, t + 101) < OFF_SHARE) return JinglePalette.BULB_OFF
            val reds = JinglePalette.ALARM_REDS
            return reds[floor(hash(bulb, t + 202) * reds.size).toInt().coerceIn(0, reds.lastIndex)]
        }

        private companion object {
            const val OFF_SHARE = 0.2
        }
    }

    /**
     * A run succeeded, [progress] from 0 to 1. A bright crest runs along the line from its start to its
     * end and leaves the bulbs behind it gold, so the whole garland fills with gold; then it all sparkles
     * for a moment, and the bulbs go back to their own colors from the start of the line on.
     */
    class Wave(private val progress: Double) : GarlandFlash {
        override fun bulbColor(i: Int, count: Int, t: Int): Color? = when {
            progress < FILL -> {
                // From before the first bulb to past the last one.
                val crest = progress / FILL * (count + 2 * CREST) - CREST
                when {
                    i > crest + CREST -> null
                    i >= crest - CREST -> JinglePalette.GOLD_CREST
                    else -> JinglePalette.GOLD_WAVE
                }
            }
            progress < SPARKLE -> if (hash(i, t + 303) < 0.3) JinglePalette.GOLD_CREST else JinglePalette.GOLD_WAVE
            else -> if (i < (progress - SPARKLE) / (1 - SPARKLE) * (count + 1)) null else JinglePalette.GOLD_WAVE
        }

        private companion object {
            /** Bulbs from the middle of the crest to either end of it. */
            const val CREST = 1.5

            /** Where filling ends and sparkling ends, as parts of the wave. */
            const val FILL = 0.5
            const val SPARKLE = 0.8
        }
    }
}

/**
 * The garland's reactions to runs and builds ([JingleEvent.RunFinished]): red lights while the icicles
 * fall after a failure, a golden wave after a success. Every run counts; the newer one wins.
 */
@Service(Service.Level.PROJECT)
class RunLights {
    @Volatile
    private var failedAt = 0L

    @Volatile
    private var succeededAt = 0L

    /** What the garland's line [salt] shows now instead of its own colors, if anything. */
    internal fun flash(salt: Int): GarlandFlash? {
        val failed = ageMs(failedAt)
        val succeeded = ageMs(succeededAt)
        return when {
            failed < ALARM_MS && failed <= succeeded -> GarlandFlash.Alarm(salt)
            succeeded < WAVE_MS -> GarlandFlash.Wave(succeeded.toDouble() / WAVE_MS)
            else -> null
        }
    }

    /** The wave moves smoothly; the red blinks on frames. */
    internal val isWaving: Boolean get() = flash(0) is GarlandFlash.Wave

    internal fun onEvent(event: JingleEvent) {
        if (event !is JingleEvent.RunFinished) return
        if (event.succeeded) succeededAt = System.nanoTime() else failedAt = System.nanoTime()
    }

    companion object {
        /** About as long as the icicles fall and break. */
        const val ALARM_MS = 2000L

        const val WAVE_MS = 2400L

        private fun ageMs(at: Long): Long = if (at == 0L) Long.MAX_VALUE else (System.nanoTime() - at) / 1_000_000

        @JvmStatic
        fun getInstance(project: Project): RunLights = project.service()
    }
}

/** Declared in plugin.xml, so it hears runs even before the garland has asked for the lights. */
internal class RunLightsListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = RunLights.getInstance(project).onEvent(event)
}
