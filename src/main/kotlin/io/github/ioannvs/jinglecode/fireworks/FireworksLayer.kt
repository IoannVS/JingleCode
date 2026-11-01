package io.github.ioannvs.jinglecode.fireworks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEventListener
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle

/** A show planned for the toolbar's sky. */
internal class Show(val sky: Sky, val bursts: List<Burst>)

/**
 * The fireworks after a successful run or build ([JingleEvent.RunFinished]): every success starts a new
 * show, different from the last one. Where its bursts go is planned once a show, on the EDT, when the
 * overlay first asks.
 */
@Service(Service.Level.PROJECT)
class Fireworks {
    @Volatile
    private var startedAt = 0L

    /** Counts the shows, so each one is planned differently. */
    @Volatile
    internal var number = 0
        private set

    /** This show's plan, once made; `null` before that or if the toolbar has no room. */
    @Volatile
    internal var show: Show? = null

    @Volatile
    internal var planned = false

    internal fun ageMs(): Long = if (startedAt == 0L) Long.MAX_VALUE else (System.nanoTime() - startedAt) / 1_000_000

    internal val isOn: Boolean get() = ageMs() < FireworksPlan.LONGEST_MS

    internal fun onEvent(event: JingleEvent) {
        if (event !is JingleEvent.RunFinished || !event.succeeded) return
        planned = false
        show = null
        number++
        startedAt = System.nanoTime()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): Fireworks = project.service()
    }
}

/** Declared in plugin.xml, so it hears runs even before the toolbar has asked for fireworks. */
internal class FireworksListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = Fireworks.getInstance(project).onEvent(event)
}

/**
 * Fireworks after a successful run in the empty parts of the main toolbar, between its buttons, as in the
 * mockup's sky (`drawBurst`, "салют в тулбаре"), but different every time (Ivan's call): see [FireworksPlan].
 * Over the toolbar only, never over the code. Only while the garland and icicles react to runs
 * ([JingleFeature.RUN_REACTIONS]).
 */
internal object FireworksLayer : OverlayLayer {

    override val feature = JingleFeature.FIREWORKS

    private fun show(project: Project, layout: FrameLayout, scale: OverlayScale): Show? {
        val fireworks = Fireworks.getInstance(project)
        if (!fireworks.isOn || !FeatureGate.getInstance().isActive(JingleFeature.RUN_REACTIONS)) return null
        if (!fireworks.planned) {
            fireworks.show = FeatureGate.getInstance().guard(feature, null) {
                ToolbarSky.find(project, layout, scale)?.let { Show(it, FireworksPlan.plan(it, scale.pixelUser, fireworks.number)) }
            }
            fireworks.planned = true
        }
        return fireworks.show
    }

    override fun isAnimating(project: Project): Boolean = Fireworks.getInstance(project).isOn

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val show = show(project, layout, scale) ?: return emptyList()
        val age = Fireworks.getInstance(project).ageMs()
        return show.bursts.filter { age >= it.delayMs - 50 && age <= it.delayMs + BURST_MS + 50 }.map {
            // The ring with its crossed ends and a pixel of rounding.
            val half = scale.user(it.radius + 3)
            Rectangle(it.x - half, 0, 2 * half, show.sky.height)
        }
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val show = show(project, layout, scale) ?: return
        val age = Fireworks.getInstance(project).ageMs()
        PixelCanvas.paint(g, 0, 0, magnify = scale.magnify) {
            for (burst in show.bursts) drawBurst(toLogical(burst.x), burst, age - burst.delayMs, show.sky.fit.rows)
        }
    }
}
