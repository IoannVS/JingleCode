package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import kotlin.math.sin

/**
 * The mockup's `v3`, "Улица в гору": the street climbs from left to right to the chapel on the top,
 * then down to the windmill and the forest. An empty field with birches and a haystack, a cabin with a
 * bathhouse close by at the foot, a well, a tall terem, the top; the back row stands higher up the slope,
 * partly hidden by the front one. The field takes up the width.
 */
internal object UphillStreetScene : VillageScene {

    override val elastic = listOf(0..18, 32..56, 68..90)

    override fun draw(p: VillagePainter) = with(p) {
        val h = VILLAGE_ROWS
        val r = MockupRandom(seed * 303 + 3)
        sky()
        // The street climbs to the chapel, and goes down behind the top
        val hf = ground { x -> h - 5 - 20 * smoothstep(110, 350, x) + 14 * smoothstep(385, 460, x) - bump(x, 40, 35, 2) + sin(x / 8) * 0.6 }
        fun g(x: Int) = jsRound(hf(m(x))) + 1
        fun base(x: Int) = hf(m(x)) + 1
        // Down at the left: an empty field, birches, a haystack, the sled's track
        birch(m(22), base(22), 15, r)
        birch(m(27), base(27), 11, r)
        haystack(m(58), g(62))
        // The back row higher up the slope: drawn first, the front houses cover it
        drawHouse(m(205), g(214) - 5, HouseSpec.random(HouseType.COTTAGE, r))
        drawHouse(m(262), g(270) - 4, HouseSpec.random(HouseType.IZBA, r))
        pine(m(300), hf(m(300)) - 3, 16, r)
        pine(m(309), hf(m(309)) - 3, 22, r)
        // At the foot: a cabin with the bathhouse close by, firewood
        drawHouse(m(118), g(126), HouseSpec.random(HouseType.IZBA, r))
        drawHouse(m(136), g(141), HouseSpec.random(HouseType.BANYA, r))
        woodpile(m(150), g(154))
        fence(m(92), m(116), g(104))
        // Halfway up: a tall terem and a well
        lampPost(m(178), g(178))
        well(m(186), g(189))
        drawHouse(m(218), g(228), HouseSpec.random(HouseType.TEREM, r))
        snowman(m(244), g(246))
        lampPost(m(286), g(286))
        // The top: the chapel among fir trees, the scene's main point
        pine(m(322), base(322), 14, r)
        drawHouse(m(334), g(340), HouseSpec.random(HouseType.CHURCH, r))
        pine(m(352), base(352), 19, r)
        pine(m(360), base(360), 12, r)
        // Behind the top: the windmill in the wind and the forest down the slope
        drawHouse(m(392), g(397), HouseSpec.random(HouseType.MILL, r))
        for ((x, th) in listOf(418 to 20, 428 to 14, 440 to 22, 452 to 16)) pine(m(x), base(x), th, r)
        ground(hf)
        for (x in 70 until 112 step 2) px(m(x), g(x) + 1 + (if (x % 4 != 0) 0 else 1), JinglePalette.SNOW_SHADE)
        sled(m(160), g(163))
    }
}
