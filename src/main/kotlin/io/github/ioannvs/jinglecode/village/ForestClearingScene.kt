package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import kotlin.math.floor
import kotlin.math.sin

/**
 * The mockup's `v4`, "Поляна в лесу": thick forest on both sides in two tones, in the middle a clearing
 * with one cabin, lit windows, thick smoke, firewood, a snowman, a sled. The forest takes up the width.
 */
internal object ForestClearingScene : VillageScene {

    override val elastic = listOf(0..236, 376..460)

    override fun draw(p: VillagePainter) = with(p) {
        val h = VILLAGE_ROWS
        val r = MockupRandom(seed * 404 + 4)
        sky()
        val hf = ground { x -> h - 7 - bump(x, 305, 60, 2) + sin(x / 12) * 0.6 }
        fun g(x: Int) = jsRound(hf(m(x))) + 1
        // The back row of the forest, darker
        var x = 0
        while (x < width) {
            val at = back(x)
            if (at <= 250 || at >= 360) pine(x, hf(x) - 2, 14 + floor(r() * 10).toInt(), r, far = true)
            x += 4 + floor(r() * 4).toInt()
        }
        x = 2
        while (x < width) {
            val at = back(x)
            if (at <= 240 || at >= 372) {
                val th = 10 + floor(r() * 13).toInt() + if (at !in 60.0..420.0) 4 else 0
                pine(x, hf(x) + 1, th, r)
            }
            x += 6 + floor(r() * 7).toInt()
        }
        birch(m(246), hf(m(246)) + 1, 15, r)
        val cabin = HouseSpec.random(HouseType.IZBA, r).copy(lit = 0.9, chimney = true, smokeHeavy = true, deco = Deco.GARLAND)
        drawHouse(m(296), g(305), cabin)
        woodpile(m(278), g(282))
        snowman(m(330), g(332))
        sled(m(340), g(343))
        ground(hf)
    }
}
