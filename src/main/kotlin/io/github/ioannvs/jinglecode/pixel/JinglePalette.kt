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

    /** Garland bulbs while a run fails: reds only, the bulb red and two shades around it. */
    @JvmField val ALARM_REDS: List<Color> = listOf(c(0xff5a5a), c(0xd62828), c(0xff8a7a))

    /** The golden wave along the garland after a successful run: its crest and its sides. */
    @JvmField val GOLD_CREST = c(0xfff3b0)
    @JvmField val GOLD_WAVE = c(0xffc531)

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

    // The village behind the editor (`docs/mockup/jingle-code-village.html`, its `P` and the colours in its code)
    @JvmField val STAR = c(0xffe9a8)
    @JvmField val STAR_COLD = c(0xcfe0ff)
    @JvmField val STAR_DIM = c(0x9c8f68)
    @JvmField val STAR_COLD_DIM = c(0x7d8aa3)
    @JvmField val SNOW_SHADE = c(0xc3d2e6)
    @JvmField val SNOW_SPECK = c(0xdde6f1)
    @JvmField val SNOW_FAR = c(0xdfe7f2)
    @JvmField val ROOF_ICE = c(0xa9cdf0)
    @JvmField val SMOKE = c(0x8d96a8)
    @JvmField val LOG = c(0x8a5a3a)
    @JvmField val LOG_DARK = c(0x74482d)
    @JvmField val PLANKS = c(0x9c6a45)
    @JvmField val PLANKS_DARK = c(0x7b4f31)
    @JvmField val WALL_RED = c(0xa8322a)
    @JvmField val WALL_RED_DARK = c(0x8a2620)
    @JvmField val WALL_BLUE = c(0x2f5f8a)
    @JvmField val WALL_BLUE_DARK = c(0x264d70)
    @JvmField val WALL_YELLOW = c(0xc9a04a)
    @JvmField val WALL_YELLOW_DARK = c(0xa8843a)
    @JvmField val STONE = c(0x7d756d)
    @JvmField val STONE_DARK = c(0x655e57)
    @JvmField val ROOF_BROWN = c(0x5a3a24)
    @JvmField val ROOF_GREEN = c(0x2e6b45)
    @JvmField val ROOF_SLATE = c(0x4a5568)
    @JvmField val WINDOW_FRAME = c(0x4a2c1c)
    @JvmField val WINDOW_LIT = c(0xffcf5e)
    @JvmField val WINDOW_GLOW = c(0xfff2b8)
    @JvmField val WINDOW_DARK = c(0x1b2433)
    @JvmField val DOOR = c(0x4a2c1c)
    @JvmField val BARN_GATE = c(0x6a1e18)
    @JvmField val PALE_WOOD = c(0xd9c9a8)
    @JvmField val BIRCH = c(0xe8e4dc)
    @JvmField val BIRCH_MARK = c(0x2a2420)
    @JvmField val BIRCH_TWIG = c(0x8a7a6a)
    @JvmField val FIR_FAR = c(0x1f3a33)
    @JvmField val HAY = c(0xa8843a)
    @JvmField val HAY_LIGHT = c(0xb8954a)
    @JvmField val LAMP = c(0xffd75e)
    @JvmField val LAMP_OFF = c(0x555b66)
    @JvmField val LAMP_GLOW = c(0xa57a2c)
    @JvmField val LAMP_POST = c(0x2b2b2b)
    @JvmField val MOUNTAIN = c(0x4b5a74)
    @JvmField val MOUNTAIN_SHADE = c(0x3d4a63)
    @JvmField val MOUNTAIN_SNOW = c(0xd7e2f0)
    @JvmField val MOUNTAIN_SNOW_SHADE = c(0xa9b9d2)
    @JvmField val FAR_HOUSE = c(0x2a2230)
    @JvmField val FAR_SNOW = c(0xe8eef7)
    @JvmField val ROAD = c(0xc3cfe0)
    @JvmField val PISTE = c(0xe3ebf6)
    @JvmField val LIFT = c(0x1d2433)
    @JvmField val FLAG_LIGHT = c(0xd84a3f)
    @JvmField val SKIER_PINK = c(0xff7fd0)
    @JvmField val POND_EDGE = c(0xc8e0f7)
    @JvmField val FACE = c(0xf3d2b3)

    // Village residents (`person`, `dog`, `cat`, ... in the village mockup)
    @JvmField val CLOTH_DARK = c(0x2b2b33)
    @JvmField val HAT_WHITE = c(0xe8eef7)
    @JvmField val COAT_OCHRE = c(0xb8862b)
    @JvmField val COAT_ORANGE = c(0xd8743a)
    @JvmField val STEEL = c(0x9aa4b2)
    @JvmField val PUFF = c(0xd6e2f0)
    @JvmField val STEAM = c(0xdfe6ef)
    @JvmField val BATHER = c(0xf3c2a0)
    @JvmField val DOG = c(0x7a5230)
    @JvmField val DOG_DARK = c(0x4a3020)
    @JvmField val CAT = c(0x3a3036)
    @JvmField val BULLFINCH = c(0xd8433a)
    @JvmField val HARE = c(0xcfd6e0)
    @JvmField val HARE_DARK = c(0x4a4a52)
    @JvmField val FOX = c(0xd9692b)
    @JvmField val FOX_DARK = c(0x3a2a22)
    @JvmField val MOOSE = c(0x4a3020)
    @JvmField val MOOSE_LEG = c(0x3a2418)
    @JvmField val MOOSE_ANTLER = c(0xc9b48a)
    @JvmField val HORSE = c(0x6b4428)
    @JvmField val HORSE_DARK = c(0x3a2418)

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

    // Fireplace in the status bar (`drawStrip`)
    @JvmField val FIREBOX = c(0x120a08)
    @JvmField val STOCKING_CUFF = c(0xe9e4dc)

    // Stocking, Santa, gifts
    @JvmField val STOCKING = c(0xb8322a)
    @JvmField val SANTA_RED = c(0xc0392b)
    @JvmField val GOLD = c(0xd8b04a)

    // Holly on notifications (`holly` in `docs/mockup/jingle-code-windows.html`); its leaves are FIR and FIR_LIGHT
    @JvmField val HOLLY_BERRY = c(0xd8433a)
    @JvmField val HOLLY_BERRY_DARK = c(0xc0392b)
    @JvmField val HOLLY_BERRY_GLINT = c(0xff9a9a)

    // Reindeer
    @JvmField val REINDEER = c(0x8a5a3a)

    // Penguin (`PENG_C`)
    @JvmField val PENGUIN_BLACK = c(0x3a4152)

    // The railway in the bottom tool windows (`docs/mockup/jingle-code-railway.html`, its `C` and `strip()`);
    // the rest of its `C` is the village palette above
    @JvmField val CAR_BLUE_DARK = c(0x1d3f60)
    @JvmField val FIR_DARK = c(0x1f5a3a)
    @JvmField val SIGNAL_RED = c(0xff4a3a)
    @JvmField val SIGNAL_RED_OFF = c(0x2a2230)

    /** Locomotive smoke; the mockup melts it with alpha, the one translucent thing on the railway besides the lights. */
    @JvmField val RAIL_SMOKE = c(0xc8d2e1)

    // Santa's yard in the right tool windows (`docs/mockup/jingle-code-santa-yard.html`, the colours in its code
    // beyond its `C`); the rest is the village palette above
    @JvmField val FAR_FIR_TIP = c(0x9fb0c8)
    @JvmField val CHIMNEY_BAND = c(0x7d2a22)
    @JvmField val WINDOW_FLICKER = c(0xffb347)
    @JvmField val GATE = c(0x4a2e1c)
    @JvmField val CHUTE_SHADE = c(0x8a5a34)
    @JvmField val STALL = c(0x22160e)
    @JvmField val HAY_SHADE = c(0xc9b088)
    @JvmField val PINK = c(0xe98fa8)
    @JvmField val SACK_SHADE = c(0xa8845a)
    @JvmField val LETTER_YELLOW = c(0xf3e2a8)

    /** Editor background of the mockup (Darcula/New UI dark), for previews only. */
    @JvmField val IDE_BACKGROUND = c(0x1e1f22)
}
