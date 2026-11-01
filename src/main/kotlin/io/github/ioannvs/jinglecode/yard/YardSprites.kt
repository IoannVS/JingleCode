package io.github.ioannvs.jinglecode.yard

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import java.awt.Color

/*
 * The Santa's yard mockup's own sprites (`docs/mockup/jingle-code-santa-yard.html`, block "sprites"), row for row.
 * One sprite pixel is one logical pixel, the pixel of the snowman's drift: the mockup draws them at `P = 2` of its
 * CSS pixels, the yard is the mockup at half its size. The sleigh, the reindeer and the elves are the drift's.
 */

/** The mockup's `C`: what each letter of a sprite paints (the letters its sprites use). */
private val C: Map<Char, Color> = mapOf(
    'K' to JinglePalette.COAL,
    'R' to JinglePalette.SANTA_RED,
    'G' to JinglePalette.GOLD,
    'W' to JinglePalette.FAR_SNOW,
    'g' to JinglePalette.STEEL,
    'S' to JinglePalette.BATHER,
)

/** A sprite of the mockup: rows of letters of [C], `.` is empty. The mockup's `spr`, `hOf`. */
internal class YardSprite(private val rows: List<String>) {
    private val cols = rows.maxOf { it.length }

    /** Height in logical pixels (`hOf`). */
    val h: Int = rows.size

    /** The mockup's `rows.slice(from, to)`. */
    fun slice(from: Int, to: Int = rows.size) = YardSprite(rows.subList(from, to))

    /** The mockup's `rows.concat(more)`. */
    operator fun plus(more: String) = YardSprite(rows + more)

    /** The mockup's `spr(rows, x, y, flip)`: top left at ([x], [y]), mirrored if [flip]. */
    fun draw(canvas: PixelCanvas, x: Double, y: Double, flip: Boolean = false) {
        val left = jsRound(x)
        val top = jsRound(y)
        for ((j, row) in rows.withIndex()) {
            for ((i, ch) in row.withIndex()) {
                if (ch == '.') continue
                val color = C[ch] ?: continue
                canvas.px(left + if (flip) cols - 1 - i else i, top + j, color)
            }
        }
    }
}

private fun sprite(vararg rows: String) = YardSprite(rows.toList())

internal object YardSprites {
    val SANTA = sprite("....W...", "...RR...", "..RRRR..", ".WWWWWW.", "..SSSS..", ".WSSSSW.", ".WWWWWW.", "RRWWWWRR", "RRRWWRRR", "KKKGKKKK", ".RRRRRR.", ".RR..RR.", ".KK..KK.")
    val FINCH = sprite("..KK..", ".KKKg.", "KRRgg.", ".RRggK", "..RW..", "..K...")
    val FINCHF = sprite("K....K", ".KKKK.", "KRRgg.", ".RRggK", "..RW..", "......")

    /** Santa sitting on the bench: legs folded under the coat. */
    val SANTA_SITTING = SANTA.slice(0, 10) + "RRRRRRKK"

    /** A `?` over Santa's head; the mockup writes it in an 11 px font, here it is pixels, its baseline under the last row. */
    val QUESTION: List<String> = listOf("##.", "..#", ".#.", "...", ".#.")

    /** The mockup's `GIFTC`. */
    val GIFT_COLORS: List<Color> = listOf(JinglePalette.SANTA_RED, JinglePalette.WALL_BLUE, JinglePalette.FIR, JinglePalette.GIFT_PLUM, JinglePalette.GOLD)

    /** Confetti of a burst gift, the mockup's `burst` colours. */
    val CONFETTI: List<Color> = listOf(JinglePalette.SANTA_RED, JinglePalette.LAMP, JinglePalette.WALL_BLUE, JinglePalette.FIR, JinglePalette.PINK, JinglePalette.FAR_SNOW)

}
