package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import kotlin.math.floor

/*
 * The mockup's trees and props (`pine`, `birch`, `fence`, `lampPost`, `sled`, `haystack`, `well`,
 * `woodpile`, `snowman`), pixel for pixel. Each stands on row [gy] or [base] at column x of the picture.
 */

/** The mockup's `pine`: [h] high, snow on random branches from [r]; a [far] one is one dark tone, without snow. */
internal fun VillagePainter.pine(x: Int, base: Double, h: Int, r: () -> Double, far: Boolean = false) {
    val dark = if (far) JinglePalette.FIR_FAR else JinglePalette.FIR
    val light = if (far) JinglePalette.FIR_FAR else JinglePalette.FIR_LIGHT
    for (i in 0 until h) {
        val hw = floor(i / 2.4).toInt() - if (i % 6 == 5) 1 else 0
        rect(x - hw, base - h + i, hw * 2 + 1, 1, if (i % 5 < 2) light else dark)
    }
    rect(x, base, 1, 2, JinglePalette.TRUNK)
    if (far) return
    var i = 2
    while (i < h) {
        val hw = floor(i / 2.4).toInt()
        val side = if (r() > 0.5) 1 else -1
        val len = 1 + floor(r() * (hw + 1)).toInt()
        rect(if (side > 0) x else x - len + 1, base - h + i, len, 1, JinglePalette.SNOW)
        i += 4 + floor(r() * 2).toInt()
    }
    px(x, base - h, JinglePalette.SNOW)
}

/** The mockup's `birch`: a white trunk [h] high with dark marks, twigs with a bit of snow. */
internal fun VillagePainter.birch(x: Int, base: Double, h: Int, r: () -> Double) {
    rect(x, base - h, 1, h + 1, JinglePalette.BIRCH)
    var y = base - h + 2
    while (y < base) {
        if (r() > 0.4) px(x, y, JinglePalette.BIRCH_MARK)
        y += 3
    }
    repeat(4) {
        val ty = base - h + 2 + floor(r() * (h - 4))
        val d = if (r() > 0.5) 1 else -1
        px(x + d, ty, JinglePalette.BIRCH_TWIG)
        px(x + d * 2, ty - 1, JinglePalette.BIRCH_TWIG)
        if (r() > 0.5) px(x + d * 2, ty - 2, JinglePalette.SNOW)
    }
}

/** The mockup's `fence` from column [x0] to [x1]: posts every third column with snow caps, a rail under snow. */
internal fun VillagePainter.fence(x0: Int, x1: Int, gy: Int) {
    for (x in x0..x1 step 3) {
        rect(x, gy - 5, 1, 5, JinglePalette.PLANKS_DARK)
        px(x, gy - 6, JinglePalette.SNOW)
    }
    rect(x0, gy - 4, x1 - x0 + 1, 1, JinglePalette.PLANKS)
    rect(x0, gy - 5, x1 - x0 + 1, 1, JinglePalette.SNOW)
}

/** The mockup's `lampPost`: lit with a glow to the sides in the evening. */
internal fun VillagePainter.lampPost(x: Int, gy: Int) {
    rect(x, gy - 10, 1, 10, JinglePalette.LAMP_POST)
    rect(x - 1, gy - 12, 3, 2, if (evening) JinglePalette.LAMP else JinglePalette.LAMP_OFF)
    px(x, gy - 13, JinglePalette.SNOW)
    if (evening) {
        px(x - 2, gy - 11, JinglePalette.LAMP_GLOW)
        px(x + 2, gy - 11, JinglePalette.LAMP_GLOW)
    }
}

/** The mockup's `sled`. */
internal fun VillagePainter.sled(x: Int, gy: Int) {
    rect(x, gy - 3, 6, 1, JinglePalette.SANTA_RED)
    rect(x, gy - 1, 7, 1, JinglePalette.TRUNK)
    px(x + 7, gy - 2, JinglePalette.TRUNK)
    px(x + 1, gy - 2, JinglePalette.TRUNK)
    px(x + 5, gy - 2, JinglePalette.TRUNK)
}

/** The mockup's `haystack` with a snow cap. */
internal fun VillagePainter.haystack(x: Int, gy: Int) {
    for (i in 0 until 7) rect(x + i / 2, gy - 1 - i, 9 - i / 2 * 2, 1, if (i % 2 == 1) JinglePalette.HAY_LIGHT else JinglePalette.HAY)
    rect(x + 2, gy - 8, 5, 2, JinglePalette.SNOW)
    px(x + 4, gy - 9, JinglePalette.SNOW)
}

/** The mockup's `well`: a stone ring, posts, a little roof under snow, the bucket. */
internal fun VillagePainter.well(x: Int, gy: Int) {
    rect(x, gy - 4, 7, 4, JinglePalette.STONE)
    rect(x, gy - 4, 7, 1, JinglePalette.SNOW)
    rect(x + 1, gy - 10, 1, 6, JinglePalette.TRUNK)
    rect(x + 5, gy - 10, 1, 6, JinglePalette.TRUNK)
    for (r in 0 until 3) rect(x - 1 + r, gy - 11 - r, 9 - r * 2, 1, Roofs.BROWN)
    rect(x + 1, gy - 14, 5, 1, JinglePalette.SNOW)
    px(x + 3, gy - 7, JinglePalette.LAMP_POST)
}

/** The mockup's `woodpile`: logs stacked in three rows, cut ends showing, snow on top. */
internal fun VillagePainter.woodpile(x: Int, gy: Int) {
    for (r in 0 until 3) for (i in 0 until 4 - r) {
        rect(x + i * 2 + r, gy - 2 - r * 2, 2, 2, JinglePalette.LOG)
        px(x + i * 2 + r, gy - 2 - r * 2, JinglePalette.LOG_CUT)
    }
    rect(x + 2, gy - 7, 4, 1, JinglePalette.SNOW)
}

/** The mockup's `snowman`: a hat, a carrot, a scarf. */
internal fun VillagePainter.snowman(x: Int, gy: Int) {
    rect(x, gy - 5, 5, 5, JinglePalette.WHITE)
    rect(x + 1, gy - 9, 3, 4, JinglePalette.WHITE)
    rect(x, gy - 10, 5, 1, JinglePalette.COAL)
    rect(x + 1, gy - 12, 3, 2, JinglePalette.COAL)
    px(x + 3, gy - 7, JinglePalette.CARROT)
    rect(x, gy - 6, 5, 1, JinglePalette.SCARF)
}
