package io.github.ioannvs.jinglecode.railway

/** How long the track stays empty between trains, seconds: at least [minPause], up to [spread] more. */
enum class TrainFrequency(val minPause: Double, val spread: Double) {
    RARE(10.0, 10.0),

    /** The mockup: 2 to 5 seconds. */
    NORMAL(2.0, 3.0),
    OFTEN(0.5, 1.0),
}
