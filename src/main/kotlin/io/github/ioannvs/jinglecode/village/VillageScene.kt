package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.abs
import kotlin.math.floor

/**
 * A composition of the village, one of the mockup's `v1`…`v4`, drawn by [draw] into a picture
 * [VillagePainter.width] wide and [VILLAGE_ROWS] high, row 0 at the top. Coordinates in the scenes are
 * the mockup's; [VillagePainter.m] takes them to the picture.
 */
internal interface VillageScene {
    /** Stretches of empty snow, mockup columns, that grow and shrink with the editor's width. */
    val elastic: List<IntRange>

    /** Its residents' scenes, the mockup's `SCENES` for this style. */
    val residents: List<Resident>

    /** Draws the scene and says where its residents walk. */
    fun draw(p: VillagePainter): Spots
}

/**
 * Draws the village of [style] (not [VillageStyle.RANDOM]) at animator [frame], with the [residents]
 * scenes that run (id to the frame within the scene, see [ResidentScheduler]), and returns where its
 * houses are. See [VillagePainter] for the rest.
 */
internal fun PixelCanvas.drawVillage(
    style: VillageStyle,
    width: Int,
    seed: Int,
    frame: Int,
    evening: Boolean,
    stars: Boolean,
    leftEdge: Int? = null,
    stoked: Set<Int> = emptySet(),
    residents: Map<String, Int> = emptyMap(),
): List<HouseBox> {
    val scene = style.scene
    val painter = VillagePainter(this, width, seed, frame, evening, stars, leftEdge, scene.elastic, stoked)
    val spots = scene.draw(painter)
    // After everything else, in the list's order, as the mockup's `runScenes`.
    for (resident in scene.residents) {
        val t = residents[resident.id] ?: continue
        resident.draw(painter, t, spots)
    }
    return painter.houses
}

/** The mockup's `bump`: a smooth hill [h] high, [w] to each side of column [c]. */
internal fun bump(x: Double, c: Int, w: Int, h: Int): Double {
    val d = (x - c) / w
    return if (abs(d) < 1) h * (1 - d * d) * (1 - d * d) else 0.0
}

/** The mockup's `ss`: smoothstep from [a] to [b]. */
internal fun smoothstep(a: Int, b: Int, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/**
 * The mockup's `sky`: stars across the whole width (the mockup starts them further right in `v1` and
 * `v4`, Ivan: they should span the whole sky), more in the evening; in the evening the moon.
 * Each star twinkles on its own ([starLook]): the mockup's one-in-nine white flash read as still
 * (Ivan). Two more changes, both Ivan's calls from before: stars only when [VillagePainter.stars]
 * allows (not next to falling snow, not on a light editor), and the moon is the crescent shape of
 * [drawMoon], not a disc with the editor's background painted over it. The stars
 * take their places from their own noise, not from the scene's `r()`: in the mockup they use up a
 * different count of numbers on different frames, and the houses after them would change every frame.
 */
internal fun VillagePainter.sky() {
    if (stars) {
        // About twice the mockup's 34 and 6 per its width (Ivan: too few, then three times too many).
        val count = (if (evening) 60 else 12) * width / MOCKUP_WIDTH
        for (i in 0 until count) {
            val x = (hash(i + seed, 1) * width).toInt()
            val y = (hash(i + seed, 2) * 26).toInt()
            val warm = hash(i + seed, 3) > 0.5
            when (starLook(i + seed, frame)) {
                StarLook.OUT -> Unit
                StarLook.DIM -> px(x, y, if (warm) JinglePalette.STAR_DIM else JinglePalette.STAR_COLD_DIM)
                StarLook.SHINE -> px(x, y, if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD)
                StarLook.FLASH -> {
                    px(x, y, JinglePalette.WHITE)
                    px(x - 1, y, if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD)
                    px(x + 1, y, if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD)
                    px(x, y - 1, if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD)
                    px(x, y + 1, if (warm) JinglePalette.STAR else JinglePalette.STAR_COLD)
                }
            }
        }
    }
    if (evening) drawMoon(width - 35, 3)
}

/** How a star looks on a frame. */
internal enum class StarLook { OUT, DIM, SHINE, FLASH }

/**
 * Star [star] at animator [frame]: it may change every fourth frame (560 ms), each star by its own
 * noise, so the sky never stands still but stays calm (Ivan: every other frame was too busy): it
 * mostly shines, now and then dims, seldom goes out for a moment or flashes white with a little cross.
 */
internal fun starLook(star: Int, frame: Int): StarLook {
    val v = hash(star * 7 + 1, frame / 4 + star * 13)
    return when {
        v < 0.03 -> StarLook.OUT
        v < 0.15 -> StarLook.DIM
        v < 0.985 -> StarLook.SHINE
        else -> StarLook.FLASH
    }
}

/**
 * A crescent moon, horns to the left: a disc with a disc cut out of its left side. Drawn as a shape,
 * not by painting the editor's background over a disc, so it reads on any background.
 */
internal fun VillagePainter.drawMoon(x: Int, y: Int) {
    val r = 4.5
    for (dy in 0 until 9) for (dx in 0 until 9) {
        val cx = dx - 4.0
        val cy = dy - 4.0
        val inDisc = cx * cx + cy * cy <= r * r
        val inCut = (cx + 2.6) * (cx + 2.6) + (cy + 0.6) * (cy + 0.6) <= 3.9 * 3.9
        if (inDisc && !inCut) px(x + dx, y + dy, JinglePalette.STAR)
    }
}

/** The mockup's `ground`: snow from the line [hf] (of picture columns) down, a white crest, shade where it drops, specks. */
internal fun VillagePainter.ground(hf: (Int) -> Double) {
    val h = VILLAGE_ROWS
    for (x in 0 until width) {
        val t = jsRound(hf(x))
        val tn = jsRound(hf(x + 1))
        rect(x, t, 1, h - t, JinglePalette.SNOW)
        px(x, t, JinglePalette.WHITE)
        if (tn > t) px(x, t + 1, JinglePalette.SNOW_SHADE)
        if (hash(x, 3) > 0.82) px(x, t + 3 + floor(hash(x, 4) * (h - t - 4)), JinglePalette.SNOW_SPECK)
    }
}
