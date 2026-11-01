package io.github.ioannvs.jinglecode.hohoho

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color

/**
 * Mockup `FONT`, 3 by 5: H and O, plus N for `ho ho no` and the Cyrillic Х and Н for `хо-хо-хо`
 * (the Cyrillic О is the Latin one).
 */
private val FONT: Map<Char, List<String>> = mapOf(
    'H' to listOf("101", "101", "111", "101", "101"),
    'O' to listOf("111", "101", "101", "101", "111"),
    'N' to listOf("101", "111", "111", "111", "101"),
    'Х' to listOf("101", "101", "010", "101", "101"),
    'Н' to listOf("101", "101", "111", "101", "101"),
    'О' to listOf("111", "101", "101", "101", "111"),
)

/** Mockup `drawText`: [text] in [FONT], a letter every 4 pixels; unknown letters stay blank. */
internal fun PixelCanvas.drawBanner(text: String, x: Int, y: Int, color: Color) {
    text.forEachIndexed { i, ch ->
        FONT[ch]?.forEachIndexed { ry, row ->
            row.forEachIndexed { rx, bit -> if (bit == '1') px(x + i * 4 + rx, y + ry, color) }
        }
    }
}

/** Mockup `drawTrail`: sparks of fire behind the sleigh at [x], [y], flickering with [gallop]. */
internal fun PixelCanvas.drawTrail(x: Int, y: Int, gallop: Int) {
    for (k in 1..14) {
        if (hash(k, gallop) < 0.45) continue
        px(x - k * 2, y + 6 + PixelCanvas.jsRound((hash(k + 3, gallop) - 0.5) * 3), JinglePalette.FIRE[minOf(2, k / 5)])
    }
}

/**
 * Mockup's night flight: the lead reindeer's nose lights the way ahead, a flickering beam of red.
 * [x], [y] are the sleigh's; [team] reindeer pull it.
 */
internal fun PixelCanvas.drawNoseLight(x: Int, y: Int, team: Int, gallop: Int) {
    val nose = x + 13 + 10 * (team - 1) + 9
    for (k in 1..8) {
        if (hash(k, gallop) <= 0.25) continue
        px(nose + k, y + 1 + PixelCanvas.jsRound((hash(k + 2, gallop) - 0.5) * (k / 3.0)), if (k < 4) JinglePalette.RED_NOSE_LIT else JinglePalette.FIRE[4])
    }
}

/**
 * The stalled sleigh at [x], [y] coughing: dark puffs come out from under Santa one after another,
 * [ms] since it stalled, and drift up and back, paling as they go.
 */
internal fun PixelCanvas.drawCough(x: Int, y: Int, ms: Long) {
    for (j in 0 until PUFFS) {
        val age = ms - j * PUFF_EVERY_MS
        if (age !in 0 until PUFF_LIFE_MS) continue
        val rise = (age / 90).toInt()
        val puffX = x - 2 - j % 2 - rise / 2
        val puffY = y + 2 - rise
        val color = if (age < PUFF_LIFE_MS / 2) JinglePalette.COAL else JinglePalette.SMOKE
        rect(puffX, puffY, 2, 2, color)
        if (age > PUFF_LIFE_MS / 3) px(puffX - 1, puffY + 1, JinglePalette.SMOKE)
    }
}

private const val PUFFS = 5
private const val PUFF_EVERY_MS = 220L
private const val PUFF_LIFE_MS = 900L

/**
 * The dropped gift: red with a golden ribbon and bow, its top left at [x], [y], [sunk] rows of it
 * below the snow line [ground] and hidden; when it has [landedMs], snow splashes up around it.
 */
internal fun PixelCanvas.drawDroppedGift(fall: GiftFall, ground: Int) {
    val size = Flyby.GIFT_SIZE
    val y = fall.y + fall.sunk
    val visible = ground - y
    if (visible > 0) {
        clipped(fall.x - 1, y - 1, size + 2, visible + 1) {
            rect(fall.x, y, size, size, JinglePalette.SANTA_RED)
            rect(fall.x + size / 2, y, 1, size, JinglePalette.GOLD)
            rect(fall.x, y + 2, size, 1, JinglePalette.GOLD)
            px(fall.x + size / 2 - 1, y - 1, JinglePalette.GOLD)
            px(fall.x + size / 2 + 1, y - 1, JinglePalette.GOLD)
        }
    }
    val landed = fall.landedMs ?: return
    // Snow flies up to both sides and falls back, like the icicles' shards.
    val p = landed.toDouble() / (Flyby.SINK_MS + Flyby.PUFF_MS)
    for (k in 0 until 8) {
        val side = (if (k % 2 == 0) -1 else 1) * (1 + hash(k, 31) * 4)
        val height = 2 + hash(k, 37) * 4
        val sx = fall.x + size / 2 + PixelCanvas.jsRound(side * (0.4 + p))
        val sy = ground - 1 - PixelCanvas.jsRound(height * 4 * p * (1 - p))
        px(sx, sy, if (k % 3 == 0) JinglePalette.ICE else JinglePalette.WHITE)
    }
}
