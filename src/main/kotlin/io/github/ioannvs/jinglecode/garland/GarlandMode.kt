package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.hash
import java.awt.Color

enum class GarlandMode {
    /** Colored bulbs, every fourth one is off and the dark ones move slowly (the mockup). */
    TWINKLE,

    /** Pairs of lit bulbs run along the wire. */
    CHASE,

    /** All bulbs are lit and the colors flow along the wire. */
    FLOW,

    /** Warm white bulbs with an occasional sparkle. */
    WARM_WHITE;

    /** Color of bulb [i] at animation tick [t]. */
    fun bulbColor(i: Int, t: Int): Color {
        val bulbs = JinglePalette.BULBS
        return when (this) {
            TWINKLE -> if ((i + t / 2) % 4 != 0) bulbs[i % 4] else JinglePalette.BULB_OFF
            CHASE -> if (Math.floorMod(i - t, 4) < 2) bulbs[i % 4] else JinglePalette.BULB_OFF
            FLOW -> bulbs[(i + t / 2) % 4]
            WARM_WHITE -> if (hash(i, t / 2) > 0.85) JinglePalette.WARM_WHITE_BRIGHT else JinglePalette.WARM_WHITE
        }
    }
}
