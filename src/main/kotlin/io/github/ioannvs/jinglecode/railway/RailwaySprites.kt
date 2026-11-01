package io.github.ioannvs.jinglecode.railway

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import java.awt.Color

/*
 * The railway mockup's sprites (`docs/mockup/jingle-code-railway.html`, block "BOTTOM: railway strip"),
 * row for row. One sprite pixel is one logical pixel, the pixel of the yard and the snowman's drift: the mockup
 * draws them at `P = 2` of its CSS pixels, so its coordinates are halved here.
 */

/** The mockup's `C`: what each letter of a sprite paints. */
private val C: Map<Char, Color> = mapOf(
    'K' to JinglePalette.COAL,
    'k' to JinglePalette.CLOTH_DARK,
    'R' to JinglePalette.SANTA_RED,
    'r' to JinglePalette.WALL_RED,
    'B' to JinglePalette.WALL_BLUE,
    'b' to JinglePalette.CAR_BLUE_DARK,
    'Y' to JinglePalette.LAMP,
    'G' to JinglePalette.GOLD,
    'W' to JinglePalette.FAR_SNOW,
    'w' to JinglePalette.SNOW_SHADE,
    's' to JinglePalette.MOUNTAIN_SNOW_SHADE,
    'N' to JinglePalette.TRUNK,
    'n' to JinglePalette.ROOF_BROWN,
    'c' to JinglePalette.LOG_CUT,
    'F' to JinglePalette.FIR,
    'f' to JinglePalette.FIR_DARK,
    'g' to JinglePalette.STEEL,
    'h' to JinglePalette.ROOF_SLATE,
    'O' to JinglePalette.CARROT,
    'S' to JinglePalette.BATHER,
    'L' to JinglePalette.STAR,
    'M' to JinglePalette.GIFT_PLUM,
    'x' to JinglePalette.PALE_WOOD,
)

/** A sprite of the mockup: rows of letters of [C], `.` is empty. The mockup's `spr`, `wOf`, `hOf`. */
internal class RailSprite(private val rows: List<String>) {
    private val cols = rows.maxOf { it.length }

    /** Width in logical pixels (`wOf`, halved). */
    val w: Int = cols

    /** Height in logical pixels (`hOf`, halved). */
    val h: Int = rows.size

    /** The mockup's `spr(rows, x, y, flip)`: top left at ([x], [y]), mirrored if [flip]. */
    fun draw(canvas: PixelCanvas, x: Double, y: Double, flip: Boolean = false) {
        val left = jsRound(x)
        val top = jsRound(y)
        for ((j, row) in rows.withIndex()) {
            for ((i, ch) in row.withIndex()) {
                if (ch == '.') continue
                val color = C[ch] ?: continue
                val ii = if (flip) cols - 1 - i else i
                canvas.px(left + ii, top + j, color)
            }
        }
    }
}

private fun sprite(vararg rows: String) = RailSprite(rows.toList())

internal object RailSprites {
    val STEAM = sprite("..............kk..", "WWWWW.........KK..", "RRRRR....G....KK..", "RYYYR....G...KKKK.", "RYYYRBBBBBBBBBBBBL", "RRRRRBBBBBBBBBBBBB", "RRRRRbbbbbbbbbbbbb", "KKKKKKKKKKKKKKKKKg", ".KhK..KhhK..KhhK.g", "..K....KK....KK...")
    val DIESEL = sprite(".wwwwwwwwwwwwwwwwwwww.", "FFFFFFFFFFFFFFFFFFFFFF", "FYYFFFFFFFFFFFFFFFFYYF", "FYYFFFFFFFFFFFFFFFFYYF", "GGGGGGGGGGGGGGGGGGGGGG", "FFFFFFFFFFFFFFFFFFFFFF", "KKKKKKKKKKKKKKKKKKKKKK", ".KhKKhK........KhKKhK.")
    val TRAM = sprite(".......K.K........", "........K.........", ".wwwwwwwwwwwwwwww.", "RRRRRRRRRRRRRRRRRR", "RYYRYYRYYRYYRYYRYR", "RYYRYYRYYRYYRYYRYR", "xxxxxxxxxxxxxxxxxx", "RRRRRRRRRRRRRRRRRR", "KKKKKKKKKKKKKKKKKK", "..KhK........KhK..")
    val PLOW = sprite(".wwwwwwwwwwwwwww......", "RRRRRRRRRRRRRRRR.KKKK.", "RYYRRRRRRRRRRRRRKhgghK", "RYYRRRRRRRRRRRRRKgKKgK", "RRRRRRRRRRRRRRRRKhgghK", "GGGGGGGGGGGGGGGG.KKKK.", "KKKKKKKKKKKKKKKKKKKK..", ".KhK.KhK.....KhK.KhK..")
    val PASS = sprite(".wwwwwwwwwwwwwwwwww.", "BBBBBBBBBBBBBBBBBBBB", "BYYBYYBYYBYYBYYBYYBB", "BYYBYYBYYBYYBYYBYYBB", "BBBBBBBBBBBBBBBBBBBB", "bbbbbbbbbbbbbbbbbbbb", "KKKKKKKKKKKKKKKKKKKK", "..KhK..........KhK..")
    val FIRS = sprite("..W.....W.....W...", ".fFf...fWf...fFf..", ".fWf..fFFFf..fWf..", "fFFFf.fWFFf.fFFFf.", "fFWFf.fFFWf.fWFFf.", "NNNNNNNNNNNNNNNNNN", "KKKKKKKKKKKKKKKKKK", "..KhK........KhK..")
    val LOGS = sprite("..wwwwwwwwwwwwww..", ".NcNNcNNcNNcNNcN..", "NcNNcNNcNNcNNcNNcN", "NNcNNcNNcNNcNNcNN.", "NNNNNNNNNNNNNNNNNN", "KKKKKKKKKKKKKKKKKK", "..KhK........KhK..")
    val GIFTS = sprite("..R.....G.....R...", "BBRBB.RRGRR.GGRGG.", "BBRBB.RRGRR.GGRGG.", "BBRBB.RRGRR.GGRGG.", "NNNNNNNNNNNNNNNNNN", "KKKKKKKKKKKKKKKKKK", "..KhK........KhK..")
    val HAND = sprite("...R.....R..", "..RRR...RRR.", "..SSS...SSS.", "..WWW.KgWWW.", "..BBB.g.BBB.", "NNNNNNNNNNNN", "KKKKKKKKKKKK", ".KhK....KhK.")
    val SLEIGH = sprite("......NN..............", "....NNNN.....RR.......", ".NNNNNNN.....SS.......", "NNNNNNNN....BBBB.R...R", "N.NNNNN.....RRRRRR..RR", "..N...N.....RRRRRRRRR.", "..N...N....GGGGGGGGGG.")
    val SKIER = sprite("..R.", "..S.", "gBBg", "gBB.", "g.B.", "gB.B", "GGGGGG")
    val KID = sprite("..R.......", ".SS.......", ".BB.g.....", ".BB..g....", "B..B.gNNNN", "......G..G")
    val DOGS = sprite("....R.................", ".g..S..g....g.........", "ggg.BB.ggg..ggg..g..g.", "ggggBB.gggg.gggg.gggggg", "g..gNNNN.g.g.g.g..g.g.", "....G..G..............")
    val FOX = sprite("O.......O.", "OO.....OO.", "OOOOOOOWO.", ".OOOOOOO..", "OO.O.O.O..")
    val HARE = sprite("..W.W.", "..WWW.", ".WWWWW", "WWWWW.", ".W..W.")
    val LAMP = sprite(".LL.", ".KK.", "..K.", "..K.", "..K.", "..K.", "..K.", "..K.", "..K.", "..K.", ".KKK.")
    /** A bullfinch sitting, looking right (the mockup's `FINCH`). */
    val FINCH = sprite("..KK..", ".KKKg.", "KRRgg.", ".RRggK", "..RW..", "..K...")

    /**
     * A bullfinch flying right, wings up and wings down; not in the mockup, where birds fly off without flapping.
     * Black tail and white rump behind, red breast, black cap; the body is on the same row in both frames.
     */
    val FINCH_WINGS_UP = sprite("..gg..", "...gg.", "KWRRKK", ".RRRg.", "......", "......")
    val FINCH_WINGS_DOWN = sprite("......", "......", "KWRRKK", ".RRgg.", "..gg..", ".g....")

    /** Gliding between wing beats, wings folded, as finches fly: a few beats, a short glide. */
    val FINCH_GLIDE = sprite("......", "......", "KWggKK", ".RRRR.", "......", "......")
    val BENCH = sprite("NNNNNN", "N....N")

    /** The mockup's `TRAINS`, in turn. */
    val TRAINS: List<List<RailSprite>> = listOf(
        listOf(STEAM, PASS, PASS, PASS),
        listOf(STEAM, FIRS, LOGS, FIRS),
        listOf(DIESEL, LOGS, GIFTS, FIRS),
        listOf(TRAM),
        listOf(PLOW),
        listOf(HAND),
        listOf(DIESEL, PASS, PASS),
        listOf(STEAM, GIFTS, GIFTS),
    )

    /** The mockup's `WALKERS`: who walks the path and how fast, logical pixels a second (the mockup's, halved). */
    val WALKERS: List<Pair<RailSprite, Double>> = listOf(
        SLEIGH to 11.0, SKIER to 13.0, KID to 9.0, DOGS to 17.0, FOX to 20.0, HARE to 23.0,
    )
}
