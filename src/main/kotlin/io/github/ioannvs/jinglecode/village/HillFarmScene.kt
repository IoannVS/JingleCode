package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound
import kotlin.math.sin

/**
 * The mockup's `v1`, "Хутор на холме": a tight group on a hill at the right (terem, cabin, cottage,
 * the chapel above them all, a bathhouse), a lone barn with a fence and a haystack at the left, birches,
 * fir trees in clumps, a lamp, a sled, a snowman. Empty snow between the barn and the hill takes up the width.
 */
internal object HillFarmScene : VillageScene {

    // Not between the barn and the birches or across 152..199: the horse and the snowball fight play there.
    override val elastic = listOf(0..38, 130..147, 217..228, 236..266)

    override val residents = HILL_FARM_RESIDENTS

    override fun draw(p: VillagePainter) = with(p) {
        val h = VILLAGE_ROWS
        val r = MockupRandom(seed * 101 + 1)
        val hf = ground { x -> h - 7 - bump(x, 345, 95, 11) - bump(x, 70, 50, 3) + sin(x / 13) * 0.8 }
        fun g(x: Int) = jsRound(hf(m(x))) + 1
        fun base(x: Int) = hf(m(x)) + 1
        sky()
        // Far fir trees on the hill behind the houses
        for ((x, th) in listOf(300 to 14, 312 to 18, 388 to 16, 400 to 20, 430 to 15, 446 to 22, 456 to 17)) pine(m(x), base(x), th, r)
        // A lone barn with a fence at the left
        drawHouse(m(40), g(52), HouseSpec.random(HouseType.BARN, r))
        fence(m(70), m(96), g(83))
        haystack(m(100), g(104))
        birch(m(150), base(150), 16, r)
        birch(m(156), base(156), 12, r)
        for ((x, th) in listOf(198 to 12, 205 to 17, 212 to 10, 232 to 9)) pine(m(x), base(x), th, r)
        // The main group on the hill
        lampPost(m(270), g(270))
        sled(m(276), g(279))
        drawHouse(m(316), g(326), HouseSpec.random(HouseType.TEREM, r))
        drawHouse(m(290), g(298), HouseSpec.random(HouseType.IZBA, r))
        drawHouse(m(345), g(352), HouseSpec.random(HouseType.COTTAGE, r))
        drawHouse(m(372), g(378), HouseSpec.random(HouseType.CHURCH, r))
        drawHouse(m(412), g(418), HouseSpec.random(HouseType.BANYA, r))
        ground(hf)
        snowman(m(395), g(397))
        Spots(hf)
    }
}
