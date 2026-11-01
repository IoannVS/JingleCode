package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.PixelCanvas.Companion.jsRound

/**
 * A little scene with residents, one of the mockup's `SCENES`: [duration] frames long, drawn by [draw]
 * at frame `t` counted from its start, after everything else in the village. [id] is unique within a style.
 */
internal class Resident(val id: String, val duration: Int, val draw: VillagePainter.(t: Int, k: Spots) -> Unit)

/**
 * What the residents need from their village, the mockup's `k`: [g] is the ground row at a mockup
 * column, like the mockup's `k.g`; the valley also has its pond (centre [pondX] as a mockup column,
 * ice from row [pondY]), ski runs and serpentine road (in the picture's columns, as drawn).
 */
internal class Spots(
    private val ground: VillagePainter.Ground,
    val pondX: Int = 0,
    val pondY: Int = 0,
    val runs: List<List<Pair<Int, Int>>> = emptyList(),
    val road: List<Pair<Int, Int>> = emptyList(),
) {
    fun g(x: Number): Int = jsRound(ground.atMockup(x.toDouble())) + 1
}
