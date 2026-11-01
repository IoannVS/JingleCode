package io.github.ioannvs.jinglecode.yard

import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleClock
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.drift.GiftPile
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.overlay.RightPanel
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import io.github.ioannvs.jinglecode.village.VillageTime
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Santa's yard at the bottom of every tool window docked at the right (Gradle, Maven, Database, Notifications
 * and any other), from edge to edge, in the island's rounded bottom corners: the line of the snowman's drift
 * and the editor's village goes on there. Like the drift, it stands over the window's last rows.
 *
 * Each window has its own [YardScene], by tool window id. Its pixel is the overlay's, the drift's: one style along the line.
 * Things move on the smooth clock ([move]); [paint] only draws.
 */
internal class YardLayer : OverlayLayer {

    override val feature = JingleFeature.SANTA_YARD

    private val scenes = HashMap<String, YardScene>()

    /** The yards showing now, by tool window id. */
    val activeScenes: Collection<YardScene> get() = scenes.values

    /** The overlay's pixel (the garland size setting against the UI font), as the drift's. */
    private fun magnify(scale: OverlayScale): Float = scale.magnify

    /** User pixels per logical pixel, as [PixelCanvas] rounds it at [sysScale]. */
    private fun pixelUser(scale: OverlayScale, sysScale: Float): Double =
        PixelCanvas.pixelSize(sysScale, magnify = magnify(scale)) / sysScale.toDouble()

    /** The right windows tall enough for a yard. */
    private fun panels(layout: FrameLayout, pixelUser: Double): List<RightPanel> =
        layout.rightPanels.filter { it.body.height >= MIN_WINDOW * pixelUser }

    /** Where the strip goes in [panel], root pane coordinates: at the bottom of its body. */
    private fun strip(panel: RightPanel, pixelUser: Double): Rectangle {
        val height = ceil(YardScene.STRIP_HEIGHT * pixelUser).toInt()
        val bottom = panel.body.y + panel.body.height
        return Rectangle(panel.body.x, bottom - height, panel.body.width, height)
    }

    /** One column more than fits, so the snow reaches the right wall; the clip cuts the rest. */
    private fun logicalWidth(panel: RightPanel, pixelUser: Double): Int = floor(panel.body.width / pixelUser).toInt() + 1

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val pixelUser = pixelUser(scale, JBUIScale.sysScale())
        return panels(layout, pixelUser).map { strip(it, pixelUser) }
    }

    /** Whether runs or builds go on, as the reactions show it: a new yard starts loading gifts at once then. */
    private var running = false

    /** The day the sacks were last emptied. */
    private var day: LocalDate? = null

    /**
     * Takes [project]'s events, then moves every yard [dt] seconds on; forgets the ones of windows that are gone.
     * Returns whether there is a yard to repaint.
     */
    fun move(project: Project, layout: FrameLayout, scale: OverlayScale, sysScale: Float, dt: Double): Boolean {
        val pixelUser = pixelUser(scale, sysScale)
        val panels = panels(layout, pixelUser)
        scenes.keys.retainAll(panels.mapTo(HashSet()) { it.id })
        val visible = panels.map(::scene)
        react(YardEvents.getInstance(project), GiftPile.getInstance(project), visible)
        for (panel in panels) scene(panel).step(dt, logicalWidth(panel, pixelUser))
        flyToPile(GiftPile.getInstance(project), visible)
        return panels.isNotEmpty()
    }

    /** Gifts the sleigh brings back from the snowman's pile, from the last push. */
    private var cargo = 0

    /**
     * The sleigh between the yard and the snowman's pile, after a push: it leaves the yard to the right, then the
     * pile's visit begins and it flies in there from the left; when the visit is over it comes back to the yard
     * from the left, with the gifts it took. While a yard shows, the pile waits for its sleigh ([GiftPile.yardIsHere]).
     */
    private fun flyToPile(pile: GiftPile, yards: List<YardScene>) {
        if (yards.isEmpty() || !JingleCodeSettings.getInstance().state.santaYardReactions) return
        pile.yardIsHere()
        if (yards.any(YardScene::takeDeparted)) pile.releaseFromYard()
        if (!pile.sleighOut) yards.filter(YardScene::away).forEach { it.comeBack(cargo) }
    }

    /** A push: every yard's sleigh goes for the gifts; if none can, the pile's visit doesn't wait. */
    private fun fetch(pile: GiftPile, yards: List<YardScene>, carried: Int) {
        cargo = carried
        val leaving = yards.map(YardScene::fetch).any { it }
        if (!leaving) pile.releaseFromYard()
    }

    /**
     * All visible yards react to each event (the spec: whichever window it came from). A task going on loads gifts;
     * a success sends Santa off in the sleigh, a failure bursts a gift; a notification brings a letter.
     * Events that waited out a pause (the IDE in the background) only set whether a task goes on: no show for them.
     * A push sends the sleigh to the snowman's pile, see [flyToPile].
     * Without reactions in the settings the yards just live their lives; without letters the bullfinch stays home.
     */
    private fun react(events: YardEvents, pile: GiftPile, yards: List<YardScene>) {
        val state = JingleCodeSettings.getInstance().state
        val today = JingleClock.today()
        if (day != today) {
            if (day != null) yards.forEach(YardScene::emptySack)
            day = today
        }
        val now = System.nanoTime()
        for (received in events.drain()) {
            val fresh = now - received.atNanos < STALE_NANOS
            when (val event = received.event) {
                is JingleEvent.RunFinished -> if (state.santaYardReactions && fresh) yards.forEach { it.taskFinished(event.succeeded) }
                JingleEvent.Notification -> if (state.santaYardLetters && fresh) yards.forEach(YardScene::letter)
                is JingleEvent.Push -> if (state.santaYardReactions && fresh) fetch(pile, yards, event.carried) else pile.releaseFromYard()
                // The sleigh takes the gifts back to the pile and comes home empty.
                JingleEvent.PushRejected -> if (state.santaYardReactions && fresh) fetch(pile, yards, 0) else pile.releaseFromYard()
                else -> Unit
            }
        }
        running = state.santaYardReactions && events.running
        for (yard in yards) if (running) yard.taskStarted() else yard.taskStopped()
    }

    /** Each window its own yard, the same one every time it is opened; a task may be going on already. */
    private fun scene(panel: RightPanel): YardScene = scenes.getOrPut(panel.id) {
        YardScene(panel.id.hashCode()).also { if (running) it.taskStarted() }
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val sysScale = maxOf(g.transform.scaleX, g.transform.scaleY).toFloat().takeIf { it > 0 } ?: 1f
        val pixelUser = pixelUser(scale, sysScale)
        val now = JingleClock.now()
        val time = now.toLocalTime()
        // Stars when no snow falls in the window, only on a dark background, as over the village.
        val stars = !FeatureGate.getInstance().isActive(JingleFeature.RIGHT_SNOW) && ColorUtil.isDark(UIUtil.getPanelBackground())
        val day = VillageTime.at(now)
        val sky = if (stars) YardSky((tick % TICK_WRAP).toInt(), day.seed, day.evening) else null
        for (panel in panels(layout, pixelUser)) {
            val strip = strip(panel, pixelUser)
            val body = panel.body
            val g2 = g.create() as Graphics2D
            try {
                g2.clip(RoundRectangle2D.Float(body.x.toFloat(), body.y.toFloat(), body.width.toFloat(), body.height.toFloat(), panel.arc.toFloat(), panel.arc.toFloat()))
                g2.clip(strip)
                val width = logicalWidth(panel, pixelUser)
                PixelCanvas.paint(g2, strip.x, strip.y, magnify = magnify(scale)) {
                    scene(panel).paint(this, width, time, sky)
                }
            }
            finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        /** Lower windows get no yard (logical pixels, the whole window body). */
        const val MIN_WINDOW = 60

        /** Keeps the frame an Int. */
        const val TICK_WRAP = 1_201_200L

        /** An event older than this waited out a pause: too late for a show. */
        const val STALE_NANOS = 3_000_000_000L
    }
}
