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

/** How often the snow moves on. More is smoother and costs more repaints of the editor. */
enum class SnowFrameRate(val fps: Int) {
    LOW(15),
    MEDIUM(20),
    HIGH(30);

    val intervalMs: Int get() = 1000 / fps
}
