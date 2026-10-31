package io.github.ioannvs.jinglecode.garland

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Color
import kotlin.math.sin

/** Bulbs hang every this many logical pixels, as in the mockup. */
private const val STEP = 7

/** Logical pixels the garland reaches to each side of its gap line. */
internal const val GARLAND_REACH = 3

/** Mockup `bulb`: a 2×2 bulb with a glint in the top left pixel. */
private fun PixelCanvas.bulb(x: Int, y: Int, color: Color) {
    rect(x, y, 2, 2, color)
    px(x, y, JinglePalette.glint(color))
}

/** Mockup wire wiggle: `Math.round(Math.sin(y / 6) * 1.2)`. */
private fun wiggle(along: Int): Int = PixelCanvas.jsRound(sin(along / 6.0) * 1.2)

/** How many bulbs hang from [first] on, every [STEP], while there is room before [length]. */
private fun bulbCount(first: Int, length: Int): Int = if (first < length - 2) (length - 3 - first) / STEP + 1 else 0

/** Bulb [i] of [count]: the [flash]'s color while it has one, else the [mode]'s. */
private fun bulbColor(mode: GarlandMode, flash: GarlandFlash?, i: Int, count: Int, t: Int): Color =
    flash?.bulbColor(i, count, t) ?: mode.bulbColor(i, t)

/**
 * Mockup garland in the vertical gap, [length] logical pixels down from the origin,
 * which is the gap's center line (the mockup's `cx`). A [flash] overrides the [mode]'s colors.
 */
internal fun PixelCanvas.drawVerticalGarland(length: Int, mode: GarlandMode, t: Int, flash: GarlandFlash? = null) {
    for (y in 0 until length) px(wiggle(y), y, JinglePalette.FIR)
    val count = bulbCount(3, length)
    var i = 0
    var y = 3
    while (y < length - 2) {
        bulb(wiggle(y) + if (i % 2 != 0) 0 else -1, y, bulbColor(mode, flash, i, count, t))
        y += STEP
        i++
    }
}

/** The same garland turned to lie along a horizontal gap. */
internal fun PixelCanvas.drawHorizontalGarland(length: Int, mode: GarlandMode, t: Int, flash: GarlandFlash? = null) {
    for (x in 0 until length) px(x, wiggle(x), JinglePalette.FIR)
    val count = bulbCount(3, length)
    var i = 0
    var x = 3
    while (x < length - 2) {
        bulb(x, wiggle(x) + if (i % 2 != 0) 0 else -1, bulbColor(mode, flash, i, count, t))
        x += STEP
        i++
    }
}

/**
 * The garland over the status bar from the mockup, without the sag: there is no room for it.
 * Drawn upwards from the origin, which is the top of the status bar icons: the wire is row -3,
 * bulbs hang under it in rows -2..-1. Bulb indices start at 1, as in the mockup.
 */
internal fun PixelCanvas.drawStatusBarGarland(length: Int, mode: GarlandMode, t: Int, flash: GarlandFlash? = null) {
    rect(0, -3, length, 1, JinglePalette.FIR)
    val count = bulbCount(4, length)
    var i = 0
    var x = 4
    while (x < length - 2) {
        // The flash counts from 0, the mode from 1.
        bulb(x, -2, flash?.bulbColor(i, count, t) ?: mode.bulbColor(i + 1, t))
        x += STEP
        i++
    }
}
