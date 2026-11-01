package io.github.ioannvs.jinglecode.hohoho

import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.drift.DriftLayer
import io.github.ioannvs.jinglecode.drift.drawSleigh
import io.github.ioannvs.jinglecode.overlay.FrameLayout
import io.github.ioannvs.jinglecode.overlay.OverlayLayer
import io.github.ioannvs.jinglecode.overlay.OverlayScale
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics2D
import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Santa flying across the whole window after `// ho ho ho`, over everything, the code too (Ivan's call):
 * the classic flight ([Flyby]) with the gift he drops into the drift, or the snowman stealing his
 * sleigh ([Heist]). Only the areas around what flies are repainted.
 */
internal object HoHoHoLayer : OverlayLayer {

    override val feature = JingleFeature.HO_HO_HO

    /** Logical pixels around everything that flies, repainted with it. */
    private const val MARGIN = 3

    /** How far above the sleigh's y its banner, Santa's hat and the coughed smoke reach. */
    private const val ABOVE = 14

    /** How far below it the reindeer's legs and the trail reach. */
    private const val BELOW = 8

    /** How far the snow of a burst flies from where it burst. */
    private const val SPLASH = 7

    /** The snow line in the drift where the gift lands, rows from the scene's top (the pile's gifts rest about there). */
    private const val GROUND_ROW = 22

    /** Where the gift lands across the drift: right of the pile and the snowman. */
    private const val DROP_AT = 0.7

    /** The flight now in logical pixels of [pixel] user pixels each. */
    private class Scene(val now: FlightNow, val width: Int, val height: Int, val target: DropTarget?) {
        val sleigh = if (now.kind == FlightKind.CLASSIC) Flyby.sleigh(now.call, now.ageMs, width, height) else null
        val gift = if (now.kind == FlightKind.CLASSIC) target?.let { Flyby.gift(now.call, now.ageMs, width, height, it) } else null
        val heist = if (now.kind == FlightKind.HEIST) Heist.frame(now.call, now.seed, now.ageMs, width, height) else null
    }

    private fun scene(now: FlightNow, layout: FrameLayout, scale: OverlayScale, pixel: Float): Scene {
        val target = if (FeatureGate.getInstance().isActive(JingleFeature.DRIFT)) {
            DriftLayer.scene(layout, scale)?.let { drift ->
                DropTarget(floor((drift.x + drift.width * DROP_AT) / pixel).toInt(), floor((drift.y + scale.user(GROUND_ROW)) / pixel).toInt())
            }
        }
        else null
        return Scene(now, floor(layout.width / pixel).toInt(), floor(layout.bottom / pixel).toInt(), target)
    }

    override fun isAnimating(project: Project): Boolean = SantaFlights.getInstance(project).current() != null

    override fun dirtyRects(project: Project, layout: FrameLayout, scale: OverlayScale): List<Rectangle> {
        val flights = SantaFlights.getInstance(project)
        val now = flights.current() ?: return emptyList()
        val call = now.call
        // Until the first paint has rounded it, the pixel is about this; the margin covers the difference.
        val pixel = flights.pixelUser.takeIf { it > 0 } ?: (scale.pixelUser * Flyby.magnify(call))
        val scene = scene(now, layout, scale, pixel)
        fun user(left: Int, top: Int, right: Int, bottom: Int): Rectangle {
            val x = floor((left - MARGIN) * pixel).toInt()
            val y = floor((top - MARGIN) * pixel).toInt()
            return Rectangle(x, y, ceil((right + MARGIN) * pixel).toInt() - x, ceil((bottom + MARGIN) * pixel).toInt() - y)
        }
        return buildList {
            scene.sleigh?.let { add(user(it.x - Flyby.back(call), it.y - ABOVE, it.x + Flyby.front(call), it.y + BELOW)) }
            val target = scene.target
            scene.gift?.let { if (target != null) add(user(it.x - 6, minOf(it.y, target.ground - 8), it.x + Flyby.GIFT_SIZE + 6, target.ground + 2)) }
            scene.heist?.let { heist ->
                heist.sleigh?.let { add(user(it.x - Flyby.back(call), it.y - ABOVE, it.x + Flyby.front(call), it.y + BELOW)) }
                heist.rider?.let { add(user(it.x - Flyby.back(call), it.y - ABOVE, it.x + 10, it.y + BELOW)) }
                for (flying in heist.flying) add(user(flying.x - SPLASH, flying.y - SPLASH, flying.x + SPLASH, flying.y + SPLASH))
            }
        }
    }

    override fun paint(g: Graphics2D, project: Project, layout: FrameLayout, scale: OverlayScale, tick: Long) {
        val flights = SantaFlights.getInstance(project)
        val now = flights.current() ?: return
        val magnify = scale.magnify * Flyby.magnify(now.call)
        // The pixel as the canvas rounds it on this screen, so the repainted area follows the sleigh exactly.
        val sysScale = maxOf(g.transform.scaleX, g.transform.scaleY).toFloat().takeIf { it > 0 } ?: 1f
        val pixel = PixelCanvas.pixelSize(sysScale, magnify = magnify) / sysScale
        flights.pixelUser = pixel
        val scene = scene(now, layout, scale, pixel)
        PixelCanvas.paint(g, 0, 0, magnify = magnify) {
            val target = scene.target
            scene.sleigh?.let { drawClassic(now, it) }
            if (target != null) scene.gift?.let { drawDroppedGift(it, target.ground) }
            scene.heist?.let { drawHeist(now, it) }
        }
    }

    private fun gallop(now: FlightNow): Int = (now.ageMs / Flyby.GALLOP_MS).toInt()

    /** Mockup: the banner blinks between the two hottest colours of the fire. */
    private fun PixelCanvas.drawShout(now: FlightNow, x: Int, y: Int) {
        val blink = if ((now.ageMs / 200) % 2 == 1L) JinglePalette.FIRE[0] else JinglePalette.FIRE[1]
        drawBanner(now.call.banner, x - 2 - 4 * now.call.banner.length, y - 8, blink)
    }

    private fun PixelCanvas.drawClassic(now: FlightNow, pose: SleighPose) {
        val call = now.call
        val gallop = gallop(now)
        // A stalled sleigh has no sparks behind it, only smoke.
        if (pose.smokeMs == null) drawTrail(pose.x, pose.y, gallop)
        drawSleigh(pose.x, pose.y, gallop, team = Flyby.team(call), noses = now.night)
        drawShout(now, pose.x, pose.y)
        if (now.night) drawNoseLight(pose.x, pose.y, Flyby.team(call), gallop)
        pose.smokeMs?.let { drawCough(pose.x, pose.y, it) }
    }

    /** The stolen sleigh with whoever drives it, Santa chasing it and shouting, and the snowballs between them. */
    private fun PixelCanvas.drawHeist(now: FlightNow, heist: HeistFrame) {
        val call = now.call
        val gallop = gallop(now)
        heist.sleigh?.let { pose ->
            drawTrail(pose.x, pose.y, gallop)
            drawSleigh(pose.x, pose.y, gallop, santa = heist.driver == Driver.SANTA, team = Flyby.team(call), noses = now.night)
            if (heist.driver == Driver.SNOWMAN) {
                val hat = now.ageMs < Heist.hitMs || Heist.end(now.seed) == HeistEnd.SNOWMAN_WINS
                drawSleighSnowman(pose.x, pose.y, hat, heist.snowmanThrows)
            }
            if (heist.driver == Driver.SANTA) drawShout(now, pose.x, pose.y)
            if (now.night) drawNoseLight(pose.x, pose.y, Flyby.team(call), gallop)
        }
        heist.rider?.let { rider ->
            drawRider(rider, gallop + 1, now.night, now.ageMs - Heist.hitMs)
            if (rider.santa) drawShout(now, rider.x, rider.y)
        }
        heist.flying.forEachIndexed { i, flying -> drawFlying(flying, now.seed + i) }
    }
}
