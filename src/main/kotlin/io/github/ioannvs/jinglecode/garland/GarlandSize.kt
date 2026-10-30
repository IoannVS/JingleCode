package io.github.ioannvs.jinglecode.garland

/**
 * Garland size relative to the UI. [NORMAL] is the mockup: 2 px pixels next to a 12 px font,
 * six logical pixels to the font size. At 125 % with the default font the steps are 2, 3 and 4
 * device pixels per logical pixel.
 */
enum class GarlandSize(private val factor: Float) {
    SMALL(2f / 3),
    NORMAL(1f),
    LARGE(4f / 3);

    /** User pixels per logical pixel for a UI font of [fontSize] user pixels. */
    fun pixelUser(fontSize: Float): Float = fontSize / 6f * factor
}
