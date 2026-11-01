package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import io.github.ioannvs.jinglecode.sprites.drawFirTree
import io.github.ioannvs.jinglecode.sprites.towardsSeam
import java.awt.Color
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Height of the scene, logical pixels, as the mockup's `tsnow` canvas. */
internal const val SCENE_ROWS = 28

/**
 * Mockup `drawTreeSnow` without the parts that come with later events (letters, the hare, cocoa,
 * melting): a few flakes, the drift from edge to edge rising at the side walls, the gifts for the
 * unpushed commits (and the sleigh with its elves when a push comes for them), the mailbox and the
 * snowman. [width] logical pixels wide, [SCENE_ROWS] high, row 0 at the top; the sleigh flies above
 * row 0. [f] is the frame in the loop, [t] the animator tick.
 *
 * At the right wall the snow lies [rightEdge] high, if given, where the editor's village snow goes on
 * (see `SnowSeam`), and a fir tree like the village's stands there, so it reads as one snow line.
 * Without [flakes] the scene has none of its own: the panel snow falls over it instead.
 */
internal fun PixelCanvas.drawProjectScene(
    width: Int,
    f: Int,
    t: Int,
    pile: PileView = PileView(0),
    rightEdge: Int? = null,
    flakes: Boolean = true,
    snowman: SnowmanLook = SnowmanLook.HAT,
) {
    if (flakes) drawSceneFlakes(width, t)
    // The mockup centres its 84 pixels of props in the panel. Where they stand the snow is
    // low and bumpy; the big heaps pile up on both sides.
    val ox = max(4, (width - 84) / 2)
    val drift = drawDrift(width, low = ox - 1..ox + PROPS_WIDTH, rightEdge = rightEdge)
    val treeX = width - 9
    if (rightEdge != null && treeX - 4 > ox + PROPS_WIDTH) drawTreeInSnow(drift, treeX)
    drawMailbox(drift, ox + 69)
    if (snowman != SnowmanLook.AWAY) drawSnowman(drift, ox + 52, f, hat = snowman == SnowmanLook.HAT)
    drawGiftScene(drift, width, ox, pile)
}

/** From the leftmost gift to the mailbox. */
private const val PROPS_WIDTH = 80

/** A fir tree like the village's, its foot in the snow: the drift is laid back over its lowest branches, as in the village. */
private fun PixelCanvas.drawTreeInSnow(drift: DriftSurface, x: Int) {
    val rest = drift.topAt(x)
    drawFirTree(x, rest + 2, scale = 0.85)
    for (column in x - 6..x + 6) {
        val top = drift.topAt(column)
        rect(column, top, 1, 3, JinglePalette.SNOW)
        px(column, top, drift.crest.getOrElse(column) { JinglePalette.WHITE })
    }
}

/** The snow surface: for each column the top row and the colour of its crest. */
internal class DriftSurface(val top: IntArray, val crest: Array<Color>) {
    fun topAt(x: Int): Int = top.getOrElse(x) { SCENE_ROWS }

    /** The highest snow under columns [xs]: what something standing there rests on. */
    fun restOn(xs: IntRange): Int = xs.minOf(::topAt)
}

/**
 * Puts something standing on columns [xs] into the snow: it rests on the highest snow under it
 * and sinks one pixel in. Fills the snow up under it (packed, in shadow), [draw]s it moved by the
 * returned rows, then lays the snow crest over its bottom row in front, so it stands in the drift
 * rather than on it. [bottom] is the row its lowest pixels are drawn at when not moved.
 */
internal fun PixelCanvas.standInSnow(drift: DriftSurface, xs: IntRange, bottom: Int, draw: (dy: Int) -> Unit): Int {
    val rest = drift.restOn(xs)
    for (x in xs) {
        val gap = drift.topAt(x) - (rest + 1)
        if (gap > 0) rect(x, rest + 1, 1, gap, JinglePalette.SNOW)
        px(x, rest + 1, JinglePalette.ICE)
    }
    val dy = rest - bottom
    draw(dy)
    for (x in xs) px(x, rest, drift.crest.getOrElse(x) { JinglePalette.WHITE })
    return dy
}

/** Mockup: seven flakes falling slowly through the 22 rows above the drift. */
private fun PixelCanvas.drawSceneFlakes(width: Int, t: Int) {
    for (i in 0 until 7) {
        val x = floor(hash(i, 21) * width).toInt()
        val y = floor(hash(i, 22) * 22 + t * (0.25 + hash(i, 23) * 0.2)).toInt() % 22
        px(x, y, JinglePalette.SNOWFLAKE)
    }
}

/** One rounded heap of the drift: centre column, half width, height above the floor. */
private class Mound(val centre: Double, val radius: Double, val height: Double)

/** The snow lies at least this high between the heaps. */
private const val DRIFT_FLOOR = 4

/** Where the props stand the heaps are this much of their height: still bumpy, but low enough not to bury them. */
private const val LOW_SQUEEZE = 0.4

/**
 * The heaps along a drift [width] wide: one every 14–24 pixels, 10–16 wide each way, 6–11 high,
 * and one against the left wall, so the drift fills the panel's rounded corner at the window's edge.
 * At the right wall the height comes from the seam with the editor's village instead.
 * Placed by the mockup's `hash`, so they stay put from frame to frame.
 */
private fun mounds(width: Int): List<Mound> = buildList {
    add(Mound(-2.0, 11.0, 15.0))
    var centre = 6.0
    var i = 0
    while (centre < width + 10) {
        add(Mound(centre, 10 + hash(i, 31) * 6, 6 + hash(i, 32) * 5))
        centre += 14 + hash(i, 33) * 10
        i++
    }
}

/**
 * The drift: heaps of snow, edge to edge (the mockup's wave read flat, so it is heaps now).
 * Over the [low] columns, where the props stand, the heaps are squeezed to [LOW_SQUEEZE] of their height and grow
 * back from there, so the snowman and the gifts don't sink in.
 * The light comes from the top left: tops are white, right slopes and the creases where one heap
 * runs into the next are ice blue. It goes one row past the bottom, so no gap shows under it when
 * the panel's height isn't a whole number of pixels. Returns the surface for the props to stand on.
 */
internal fun PixelCanvas.drawDrift(width: Int, low: IntRange = IntRange.EMPTY, rightEdge: Int? = null): DriftSurface {
    val heaps = mounds(width)
    val top = IntArray(width)
    val owner = arrayOfNulls<Mound>(width)
    for (x in 0 until width) {
        var best = DRIFT_FLOOR.toDouble()
        for (heap in heaps) {
            val d = (x + 0.5 - heap.centre) / heap.radius
            if (abs(d) >= 1) continue
            val h = DRIFT_FLOOR + heap.height * sqrt(1 - d * d)
            if (h > best) {
                best = h
                owner[x] = heap
            }
        }
        if (!low.isEmpty()) {
            // Squeeze the heaps, don't cut them: they keep their bumps, just lower, and grow back
            // to full height a few columns away from the props.
            val away = max(0, max(low.first - x, x - low.last))
            val squeeze = (LOW_SQUEEZE + away * 0.08).coerceAtMost(1.0)
            best = DRIFT_FLOOR + (best - DRIFT_FLOOR) * squeeze
        }
        // At the right wall the snow lies exactly as high on screen as the village snow next door.
        if (rightEdge != null) best = towardsSeam(best, rightEdge, width - 1 - x)
        top[x] = SCENE_ROWS - best.roundToInt()
    }
    val crest = Array(width) { JinglePalette.WHITE }
    for (x in 0 until width) {
        rect(x, top[x], 1, SCENE_ROWS - top[x] + 1, JinglePalette.SNOW)
        val heap = owner[x]
        val shaded = heap != null && x + 0.5 > heap.centre + heap.radius * 0.25
        if (shaded) {
            px(x, top[x] + 1, JinglePalette.ICE)
            if (x + 0.5 > heap.centre + heap.radius * 0.6) px(x, top[x] + 2, JinglePalette.ICE)
        }
        // The crease where this heap runs down into the next one.
        if (x + 1 < width && owner[x + 1] !== heap && top[x + 1] > top[x]) {
            rect(x, top[x] + 1, 1, 3, JinglePalette.ICE)
        }
        if (x > 0 && owner[x - 1] !== heap && top[x - 1] > top[x]) {
            rect(x - 1, top[x - 1], 1, 2, JinglePalette.ICE)
        }
        crest[x] = if (shaded) JinglePalette.SNOW else JinglePalette.WHITE
        px(x, top[x], crest[x])
    }
    return DriftSurface(top, crest)
}

/** Mockup mailbox, empty: letters come with the pull event. [x] is the box's left edge; the post stands in the snow. */
private fun PixelCanvas.drawMailbox(drift: DriftSurface, x: Int) {
    standInSnow(drift, x + 4..x + 5, bottom = 23) { dy ->
        rect(x + 4, 13 + dy, 2, 11, JinglePalette.STICK)
        rect(x, 8 + dy, 9, 6, JinglePalette.MAILBOX)
        rect(x, 8 + dy, 9, 1, JinglePalette.SNOW)
        rect(x + 1, 10 + dy, 5, 1, JinglePalette.MAIL_SLOT)
    }
}

/**
 * Mockup snowman with a hat, scarf and carrot; the left arm twitches every third frame. He stands
 * in the snow where he is. Lit from the top left like the drift: the right side of each snowball
 * is ice blue, so he reads against the white snow.
 */
private fun PixelCanvas.drawSnowman(drift: DriftSurface, sx: Int, f: Int, hat: Boolean) {
    standInSnow(drift, sx..sx + 8, bottom = 22) { dy -> drawSnowman(sx, dy, f, hat) }
}

/** The snowman alone, moved down by [dy] rows; without [hat] after he lost it chasing Santa. */
internal fun PixelCanvas.drawSnowman(sx: Int, dy: Int, f: Int, hat: Boolean = true) {
    rect(sx, 14 + dy, 9, 8, JinglePalette.SNOWMAN)
    rect(sx + 1, 13 + dy, 7, 10, JinglePalette.SNOWMAN)
    rect(sx + 8, 15 + dy, 1, 6, JinglePalette.ICE)
    rect(sx + 7, 21 + dy, 1, 2, JinglePalette.ICE)
    rect(sx + 2, 7 + dy, 5, 6, JinglePalette.SNOWMAN)
    rect(sx + 1, 8 + dy, 7, 4, JinglePalette.SNOWMAN)
    rect(sx + 7, 9 + dy, 1, 3, JinglePalette.ICE)
    rect(sx + 1, 12 + dy, 7, 1, JinglePalette.SCARF)
    rect(sx + 6, 13 + dy, 2, 3, JinglePalette.SCARF)
    if (hat) {
        rect(sx + 1, 6 + dy, 7, 1, JinglePalette.COAL)
        rect(sx + 2, 2 + dy, 5, 4, JinglePalette.COAL)
    }
    px(sx + 3, 9 + dy, JinglePalette.COAL)
    px(sx + 5, 9 + dy, JinglePalette.COAL)
    px(sx + 6, 10 + dy, JinglePalette.CARROT)
    px(sx + 7, 10 + dy, JinglePalette.CARROT)
    px(sx + 4, 16 + dy, JinglePalette.COAL)
    px(sx + 4, 18 + dy, JinglePalette.COAL)
    val wave = if (f % 3 == 0) -1 else 0
    px(sx - 1, 15 + dy, JinglePalette.STICK)
    px(sx - 2, 14 + dy + wave, JinglePalette.STICK)
    px(sx - 3, 13 + dy + wave, JinglePalette.STICK)
    px(sx + 9, 15 + dy, JinglePalette.STICK)
    px(sx + 10, 14 + dy, JinglePalette.STICK)
    px(sx + 11, 13 + dy, JinglePalette.STICK)
}
