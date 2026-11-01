package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Color

/** The pile now: [count] gifts, the newest one falling for [dropAgeMs] after a commit, the sleigh's [delivery] if it is here. */
data class PileView(val count: Int, val dropAgeMs: Long = Long.MAX_VALUE, val delivery: DeliveryView? = null)

/**
 * Mockup `GIFTS`: where each gift sits, its size, wrapping and ribbon, and the gift it lies on
 * ([on], or `-1` on the snow). There is room for nine.
 */
private class Gift(val x: Int, val y: Int, val w: Int, val h: Int, val wrap: Color, val ribbon: Color, val on: Int = -1)

private val GIFTS = listOf(
    Gift(10, 15, 9, 8, JinglePalette.SANTA_RED, JinglePalette.GOLD),
    Gift(21, 18, 6, 5, JinglePalette.GIFT_TEAL, JinglePalette.SNOW),
    Gift(2, 17, 7, 6, JinglePalette.GIFT_PLUM, JinglePalette.BULBS[1]),
    Gift(28, 16, 8, 7, JinglePalette.GOLD, JinglePalette.SANTA_RED),
    Gift(13, 9, 6, 6, JinglePalette.FIR, JinglePalette.BULBS[0], on = 0),
    Gift(37, 19, 5, 4, JinglePalette.BULBS[2], JinglePalette.WHITE),
    Gift(4, 11, 5, 6, JinglePalette.CARROT, JinglePalette.FIR, on = 2),
    Gift(23, 12, 6, 6, JinglePalette.SANTA_RED, JinglePalette.WHITE, on = 1),
    Gift(30, 10, 5, 6, JinglePalette.BULBS[3], JinglePalette.GIFT_PLUM, on = 3),
)

/** The most gifts the pile shows. */
internal val MAX_GIFTS: Int = GIFTS.size

/** Mockup: the newest gift falls 22 pixels in half a second, faster and faster. */
internal fun giftDrop(dropAgeMs: Long): Int {
    if (dropAgeMs !in 0 until 500) return 0
    val p = dropAgeMs / 500.0
    return -PixelCanvas.jsRound((1 - p * p) * 22)
}

/**
 * Where each gift's top left corner is with the pile at [ox] on this [drift]: the ones at the bottom
 * stand in the snow where they are, the ones on top move with the gift they lie on.
 */
private fun pilePlaces(drift: DriftSurface, ox: Int): List<At> {
    val moved = IntArray(GIFTS.size)
    return GIFTS.mapIndexed { i, gift ->
        val x = ox + gift.x
        moved[i] = if (gift.on >= 0) moved[gift.on] else drift.restOn(x until x + gift.w) - (gift.y + gift.h - 1)
        At(x, gift.y + moved[i])
    }
}

/**
 * The gifts by the snowman: a pile of [PileView.count] (at most nine), or, with none, the prints they
 * left in the snow. A push brings the sleigh: it hangs over the pile, two elves run in, toss the
 * gifts up into it and jump in after them, and off it goes; a rejected push brings it back to unload.
 * [ox] is where the mockup's props start.
 */
internal fun PixelCanvas.drawGiftScene(drift: DriftSurface, width: Int, ox: Int, pile: PileView) {
    val places = pilePlaces(drift, ox)
    val visit = pile.delivery
    val shown = (visit?.let(Delivery::inPile) ?: pile.count).coerceIn(0, GIFTS.size)

    if (shown == 0) drawPrints(drift, ox)
    for (i in 0 until shown) {
        val gift = GIFTS[i]
        val at = places[i]
        val falling = if (visit == null && i == shown - 1) giftDrop(pile.dropAgeMs) else 0
        if (gift.on < 0 && falling == 0) {
            standInSnow(drift, at.x until at.x + gift.w, at.y + gift.h - 1) { dy -> drawGift(gift, at.x, at.y + dy) }
        }
        else {
            drawGift(gift, at.x, at.y + falling)
        }
    }

    if (visit == null) return
    val hover = At(ox + 15, 0)
    val bed = At(hover.x + SLEIGH_BED.x, hover.y + SLEIGH_BED.y)
    for ((index, at) in Delivery.flying(visit, places, bed)) drawGift(GIFTS[index], at.x, at.y)

    val spots = listOf(At(ox + 43, drift.restOn(ox + 43..ox + 47)), At(ox - 1, drift.restOn(ox - 1..ox + 3)))
    val seat = At(hover.x + 6, hover.y + 1)
    val entry = At(width + 2, drift.restOn(width - 6 until width))
    for ((at, pose, step) in Delivery.elves(visit, spots, seat, entry)) drawElf(at.x, at.y, pose, step)

    val sleigh = Delivery.sleigh(visit, width, hover) ?: return
    drawSleigh(sleigh.x, sleigh.y, (visit.ageMs / 120).toInt(), Delivery.inSleigh(visit), Delivery.elvesRiding(visit))
}

/** Mockup gifts: a box with a ribbon across and down the middle and a bow on top. */
private fun PixelCanvas.drawGift(gift: Gift, x: Int, y: Int) {
    val middle = x + gift.w / 2
    rect(x, y, gift.w, gift.h, gift.wrap)
    rect(middle, y, 1, gift.h, gift.ribbon)
    rect(x, y + 2, gift.w, 1, gift.ribbon)
    px(middle - 1, y - 1, gift.ribbon)
    px(middle + 1, y - 1, gift.ribbon)
}

/**
 * Where the bottom gifts stood, the snow is pressed down: a dent two rows deep, its rim in the
 * shadow colour of ice and its floor lighter, so the prints read on white snow.
 */
private fun PixelCanvas.drawPrints(drift: DriftSurface, ox: Int) {
    for (gift in GIFTS) {
        if (gift.on >= 0) continue
        val x = ox + gift.x
        val rest = drift.restOn(x until x + gift.w)
        rect(x, rest, gift.w, 2, JinglePalette.ICE)
        rect(x, rest, gift.w, 1, JinglePalette.ICE_TIP)
        px(x, rest + 1, JinglePalette.ICE_TIP)
        px(x + gift.w - 1, rest + 1, JinglePalette.ICE_TIP)
    }
}
