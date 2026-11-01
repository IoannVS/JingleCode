package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import java.awt.Color
import kotlin.math.floor

/** The mockup's house types (`randomSpec`). */
enum class HouseType {
    /** Logs, carved window frames, a steep roof. */
    IZBA,

    /** Two storeys, a high roof the snow slides off. */
    TEREM,

    /** Painted walls, a gentle roof, lots of snow. */
    COTTAGE,

    /** A bathhouse: a shed roof, thick smoke. */
    BANYA,

    /** Red boards, a gate with a cross brace. */
    BARN,

    /** A dome with a cross, the scene's main point. */
    CHURCH,

    /** A windmill with turning sails. */
    MILL,

    /** A roof down to the ground, one window. */
    AFRAME,
}

/** House walls, light and dark (the mockup's `P.logs`, `P.planks`, ...). */
enum class Wall(val light: Color, val dark: Color) {
    LOGS(JinglePalette.LOG, JinglePalette.LOG_DARK),
    PLANKS(JinglePalette.PLANKS, JinglePalette.PLANKS_DARK),
    RED(JinglePalette.WALL_RED, JinglePalette.WALL_RED_DARK),
    BLUE(JinglePalette.WALL_BLUE, JinglePalette.WALL_BLUE_DARK),
    YELLOW(JinglePalette.WALL_YELLOW, JinglePalette.WALL_YELLOW_DARK),
    STONE(JinglePalette.STONE, JinglePalette.STONE_DARK),
}

/** The mockup's `P.roofs`. */
internal object Roofs {
    val RED: Color = JinglePalette.WALL_RED
    val TEAL: Color = JinglePalette.GIFT_TEAL
    val PLUM: Color = JinglePalette.GIFT_PLUM
    val BROWN: Color = JinglePalette.ROOF_BROWN
    val GREEN: Color = JinglePalette.ROOF_GREEN
    val SLATE: Color = JinglePalette.ROOF_SLATE
}

/** What hangs on a house: lights under the eave, a wreath on the door, or nothing. */
enum class Deco { GARLAND, WREATH, NONE }

/**
 * A house as the mockup's generator puts it together: walls [w] by [h], the [wind] side (−1 left,
 * +1 right) where the snow piles up, [snow] 1–3 thick, a [pitch] for the roof (the snow slides off a
 * steep one), the [lit] value that decides about windows and smoke, and the rest.
 */
data class HouseSpec(
    val type: HouseType,
    val wind: Int,
    val snow: Int,
    val icicles: Boolean,
    val drift: Boolean,
    val lit: Double,
    val deco: Deco,
    val w: Int,
    val h: Int,
    val wall: Wall,
    val roof: Color,
    val pitch: Double,
    val chimney: Boolean,
    val carved: Boolean = false,
    val wins: Int,
    val storeys: Int = 1,
    val smokeHeavy: Boolean = false,
) {
    companion object {
        /**
         * The mockup's `randomSpec(type, r)`: draws the same numbers from [r] in the same order, so the
         * same seed builds the same house.
         */
        fun random(type: HouseType, r: () -> Double): HouseSpec {
            fun <T> pick(options: List<T>): T = options[floor(r() * options.size).toInt()]
            fun upTo(n: Int): Int = floor(r() * n).toInt()
            val wind = if (r() > 0.5) 1 else -1
            val snow = 1 + upTo(3)
            val icicles = r() > 0.4
            val drift = r() > 0.3
            val lit = r()
            val deco = pick(listOf(Deco.GARLAND, Deco.WREATH, Deco.NONE, Deco.GARLAND))
            fun base(w: Int, h: Int, wall: Wall, roof: Color, pitch: Double, chimney: Boolean, wins: Int) =
                HouseSpec(type, wind, snow, icicles, drift, lit, deco, w, h, wall, roof, pitch, chimney, wins = wins)
            return when (type) {
                HouseType.IZBA -> {
                    val w = 16 + upTo(5)
                    val h = 9 + upTo(2)
                    val roof = pick(listOf(Roofs.BROWN, Roofs.RED, Roofs.SLATE))
                    base(w, h, Wall.LOGS, roof, 1.0, chimney = r() > 0.2, wins = 2).copy(carved = true)
                }
                HouseType.TEREM -> {
                    val w = 18 + upTo(4)
                    val wall = pick(listOf(Wall.LOGS, Wall.BLUE, Wall.YELLOW))
                    val roof = pick(listOf(Roofs.GREEN, Roofs.RED, Roofs.TEAL))
                    base(w, 18, wall, roof, 1.3, chimney = true, wins = 2).copy(carved = true, storeys = 2)
                }
                HouseType.BANYA ->
                    base(11, 7, Wall.LOGS, Roofs.BROWN, 1.0, chimney = true, wins = 1).copy(smokeHeavy = true, deco = Deco.NONE)
                HouseType.BARN -> {
                    val w = 24 + upTo(4)
                    val roof = pick(listOf(Roofs.SLATE, Roofs.BROWN))
                    base(w, 11, Wall.RED, roof, 0.8, chimney = false, wins = 0)
                }
                HouseType.COTTAGE -> {
                    val w = 14 + upTo(4)
                    val wall = pick(listOf(Wall.YELLOW, Wall.BLUE, Wall.PLANKS, Wall.RED))
                    val roof = pick(listOf(Roofs.RED, Roofs.TEAL, Roofs.PLUM))
                    base(w, 9, wall, roof, 0.6, chimney = r() > 0.3, wins = 2)
                }
                HouseType.AFRAME -> base(12, 3, Wall.PLANKS, Roofs.BROWN, 2.2, chimney = false, wins = 1)
                HouseType.CHURCH -> base(12, 16, pick(listOf(Wall.STONE, Wall.YELLOW)), Roofs.SLATE, 1.0, chimney = false, wins = 1)
                HouseType.MILL -> base(10, 16, Wall.PLANKS, Roofs.BROWN, 1.0, chimney = false, wins = 1).copy(deco = Deco.NONE)
            }
        }
    }
}
