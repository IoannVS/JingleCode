package io.github.ioannvs.jinglecode.snow

/** How thick the snow is. [NORMAL] is the mockup: 110 flakes over its editor. */
enum class SnowAmount(
    /** Logical pixels of the view per flake. */
    val areaPerFlake: Int,
) {
    FEW(800),
    NORMAL(400),
    MANY(200),
}

/** How fast the snow falls. [NORMAL] is the mockup: 7–16 logical pixels a second. */
enum class SnowSpeed(val factor: Double) {
    SLOW(0.5),
    NORMAL(1.0),
    FAST(2.0),
}

/**
 * How often the snow moves on, milliseconds: 15 times a second. Every step repaints the code and the panels
 * under the flakes; 20 and 30 cost up to half as much CPU again and look no different on the pixel grid.
 */
const val SNOW_INTERVAL_MS = 1000 / 15
