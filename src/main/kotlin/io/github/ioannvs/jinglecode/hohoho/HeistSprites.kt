package io.github.ioannvs.jinglecode.hohoho

import io.github.ioannvs.jinglecode.drift.drawReindeer
import io.github.ioannvs.jinglecode.icicles.drawShards
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas

/**
 * The drift's snowman small enough to sit where Santa sits in `drawSleigh` at [x], [y]: a black hat
 * (unless he lost it), a head with a carrot looking ahead, the red scarf, and a stick arm on the reins.
 * When he [throws], the other stick arm is up behind him.
 */
internal fun PixelCanvas.drawSleighSnowman(x: Int, y: Int, hat: Boolean, throws: Boolean) {
    if (hat) drawSnowmanHat(x + 1, y - 7)
    drawSnowmanHead(x + 2, y - 4)
    rect(x + 2, y - 1, 3, 1, JinglePalette.SCARF)
    px(x + 1, y, JinglePalette.SCARF)
    rect(x + 1, y, 5, 2, JinglePalette.SNOWMAN)
    px(x + 5, y + 1, JinglePalette.ICE)
    px(x + 6, y, JinglePalette.STICK)
    px(x + 7, y - 1, JinglePalette.STICK)
    if (throws) {
        px(x, y - 1, JinglePalette.STICK)
        px(x - 1, y - 2, JinglePalette.STICK)
    }
}

/** The small snowman's hat, 5 by 3, its brim on the bottom row; its top left at [x], [y]. */
private fun PixelCanvas.drawSnowmanHat(x: Int, y: Int) {
    rect(x + 1, y, 3, 2, JinglePalette.COAL)
    rect(x, y + 2, 5, 1, JinglePalette.COAL)
}

/** The small snowman's head, 3 by 3 and a carrot to the right; its top left at [x], [y]. */
private fun PixelCanvas.drawSnowmanHead(x: Int, y: Int) {
    rect(x, y, 3, 3, JinglePalette.SNOWMAN)
    px(x + 2, y + 2, JinglePalette.ICE)
    px(x + 2, y + 1, JinglePalette.COAL)
    px(x + 3, y + 1, JinglePalette.CARROT)
}

/**
 * Santa on a reindeer: the reindeer as in `drawReindeer`, with Santa on its back, red with a white
 * beard and pom. His arm holds on ([Arm.REST]), is up to throw, or shakes a fist ([ms] times it).
 * Without [RiderPose.santa] the reindeer runs on alone. With [noses] its nose glows.
 */
internal fun PixelCanvas.drawRider(pose: RiderPose, gallop: Int, noses: Boolean, ms: Long) {
    val x = pose.x
    val y = pose.y
    drawReindeer(x, y, gallop % 2, lead = noses, blink = noses)
    if (!pose.santa) return
    rect(x + 2, y + 1, 3, 2, JinglePalette.SANTA_RED)
    px(x + 3, y + 3, JinglePalette.COAL)
    px(x + 4, y, JinglePalette.SKIN)
    px(x + 4, y + 1, JinglePalette.WHITE)
    px(x + 3, y - 1, JinglePalette.SANTA_RED)
    px(x + 4, y - 1, JinglePalette.SANTA_RED)
    px(x + 2, y - 2, JinglePalette.WHITE)
    when (pose.arm) {
        Arm.REST -> px(x + 5, y + 1, JinglePalette.SANTA_RED)
        Arm.THROW -> {
            px(x + 5, y, JinglePalette.SANTA_RED)
            px(x + 5, y - 1, JinglePalette.SKIN)
        }
        Arm.FIST -> {
            val up = if ((ms / 150) % 2 == 0L) 1 else 0
            px(x + 5, y - up, JinglePalette.SANTA_RED)
            px(x + 5, y - 1 - up, JinglePalette.SKIN)
        }
    }
}

/** A snowball or a piece of the snowman in the air, or the snow it burst into ([Flying.splashMs]). */
internal fun PixelCanvas.drawFlying(flying: Flying, seed: Int) {
    val splash = flying.splashMs
    if (splash != null) {
        drawShards(flying.x, flying.y + 1, if (flying.piece == Piece.SNOWBALL) 2 else 4, splash, seed)
        return
    }
    val x = flying.x
    val y = flying.y
    when (flying.piece) {
        Piece.SNOWBALL -> {
            rect(x, y, 2, 2, JinglePalette.WHITE)
            px(x + 1, y + 1, JinglePalette.ICE)
        }
        Piece.HAT -> drawSnowmanHat(x, y)
        Piece.HEAD -> drawSnowmanHead(x, y)
        Piece.BODY -> {
            rect(x, y, 5, 3, JinglePalette.SNOWMAN)
            rect(x + 1, y - 1, 3, 1, JinglePalette.SCARF)
            px(x + 4, y + 2, JinglePalette.ICE)
        }
        // The whole small snowman tumbling down without his hat, as he sat in the sleigh.
        Piece.SNOWMAN -> {
            drawSnowmanHead(x + 2, y)
            rect(x + 2, y + 3, 3, 1, JinglePalette.SCARF)
            rect(x + 1, y + 4, 5, 3, JinglePalette.SNOWMAN)
            px(x + 5, y + 6, JinglePalette.ICE)
        }
    }
}
