package io.github.ioannvs.jinglecode.sprites

import io.github.ioannvs.jinglecode.pixel.JinglePalette.FIRE
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** Mockup `drawFire`: a flame [w] wide and up to [hMax] tall standing on row [yb]. */
fun PixelCanvas.drawFire(x0: Int, yb: Int, w: Int, hMax: Int, f: Int, seed: Int) {
    val raw = DoubleArray(w) { x ->
        val e = 1 - abs((x + 0.5) / w * 2 - 1).pow(1.6)
        hMax * e * (0.6 + 0.4 * hash(x * 7 + seed, f))
    }
    for (x in 0 until w) {
        val left = raw.getOrElse(x - 1) { raw[x] }
        val right = raw.getOrElse(x + 1) { raw[x] }
        val h = PixelCanvas.jsRound((left + 2 * raw[x] + right) / 4)
        val c = abs((x + 0.5) / w * 2 - 1)
        for (y in 0 until h) {
            val r = y.toDouble() / h + c * 0.35
            val color = when {
                r < 0.3 -> FIRE[0]
                r < 0.5 -> FIRE[1]
                r < 0.72 -> FIRE[2]
                r < 0.92 -> FIRE[3]
                else -> FIRE[4]
            }
            px(x0 + x, yb - 1 - y, color)
        }
        val t = hash(x + seed * 3, f + 11)
        if (h > 3 && t > 0.8) px(x0 + x, yb - 2 - h - floor(t * 3).toInt(), if (t > 0.9) FIRE[4] else FIRE[3])
    }
}
