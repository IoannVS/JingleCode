package io.github.ioannvs.jinglecode.pixel

import java.awt.Color

/**
 * Colors from `docs/mockup/jingle-code-mockup.html`, one to one.
 *
 * Decorations are deliberately theme-independent and fully opaque: no [com.intellij.ui.JBColor],
 * no alpha. Add a sprite's own colors here when porting it, named by what they paint.
 */
@Suppress("UseJBColor")
object JinglePalette {
    private fun c(rgb: Int) = Color(rgb)

    /** Flame, hottest first (`FIRE`). */
    @JvmField val FIRE: List<Color> = listOf(c(0xfff7d1), c(0xffd75e), c(0xff9f2e), c(0xf2581f), c(0xa8261a))

    /** Garland bulbs: red, yellow, blue, green (`BULBS`). */
    @JvmField val BULBS: List<Color> = listOf(c(0xff5a5a), c(0xffd75e), c(0x5ab8ff), c(0x7fe08a))

    /** A garland bulb that is off. */
    @JvmField val BULB_OFF = c(0x3a3f47)

    /** Warm white garland and its brighter twinkle. */
    @JvmField val WARM_WHITE = c(0xffe9a8)
    @JvmField val WARM_WHITE_BRIGHT = c(0xfff7d1)

    /**
     * The glint in a bulb's top left pixel. The mockup paints `#ffffffaa` over the bulb;
     * here it is the same mix precomputed as an opaque color.
     */
    @JvmStatic
    fun glint(bulb: Color): Color = glints.getOrPut(bulb) {
        fun mix(ch: Int) = ch + ((255 - ch) * 0xaa + 127) / 255
        Color(mix(bulb.red), mix(bulb.green), mix(bulb.blue))
    }

    private val glints = HashMap<Color, Color>()

    // Snow and ice
    @JvmField val SNOW = c(0xeef3fa)
    @JvmField val ICE = c(0xbcd6f0)
    @JvmField val ICE_TIP = c(0x8fb8e8)
    @JvmField val WHITE = c(0xffffff)
    @JvmField val SNOWFLAKE = c(0xdfe8f2)

    // Snowman and mailbox in the Project panel
    @JvmField val SNOWMAN = c(0xf6f8fb)
    @JvmField val SCARF = c(0xc0392b)
    @JvmField val COAL = c(0x1b1b1f)
    @JvmField val CARROT = c(0xff9f2e)
    @JvmField val STICK = c(0x6b4228)
    @JvmField val MAILBOX = c(0x2f5fa8)
    @JvmField val MAIL_SLOT = c(0x1b2433)

    // Gift wrapping beyond the colors above (`GIFTS`)
    @JvmField val GIFT_TEAL = c(0x2f6f73)
    @JvmField val GIFT_PLUM = c(0x7a3b6e)

    // Santa's little sack in the sleigh (`drawSleigh`)
    @JvmField val SACK = c(0x8a6a3a)

    // Santa and his reindeer (`drawSleigh`, `drawReindeer`)
    @JvmField val SKIN = c(0xf2c7a5)
    @JvmField val REINDEER_LIGHT = c(0x9c6a45)
    @JvmField val ANTLER = c(0xc9a27a)
    @JvmField val NOSE_DARK = c(0x3a2418)
    @JvmField val RED_NOSE = c(0xb02020)
    @JvmField val RED_NOSE_LIT = c(0xff3b3b)
    @JvmField val TAIL = c(0xe9e4dc)

    // Fir trees and wood
    @JvmField val FIR = c(0x2e7d4f)
    @JvmField val FIR_LIGHT = c(0x3f8f5a)
    @JvmField val TRUNK = c(0x6b3e22)
    @JvmField val LOG_CUT = c(0xc99a66)

    // Stocking, Santa, gifts
    @JvmField val STOCKING = c(0xb8322a)
    @JvmField val SANTA_RED = c(0xc0392b)
    @JvmField val GOLD = c(0xd8b04a)

    // Reindeer
    @JvmField val REINDEER = c(0x8a5a3a)

    // Penguin (`PENG_C`)
    @JvmField val PENGUIN_BLACK = c(0x3a4152)

    /** Editor background of the mockup (Darcula/New UI dark), for previews only. */
    @JvmField val IDE_BACKGROUND = c(0x1e1f22)
}
