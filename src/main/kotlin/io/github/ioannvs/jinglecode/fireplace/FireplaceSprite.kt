package io.github.ioannvs.jinglecode.fireplace

import io.github.ioannvs.jinglecode.pixel.JinglePalette.FIREBOX
import io.github.ioannvs.jinglecode.pixel.JinglePalette.PLANKS_DARK
import io.github.ioannvs.jinglecode.pixel.JinglePalette.STOCKING
import io.github.ioannvs.jinglecode.pixel.JinglePalette.STOCKING_CUFF
import io.github.ioannvs.jinglecode.pixel.JinglePalette.STONE
import io.github.ioannvs.jinglecode.pixel.JinglePalette.TRUNK
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.sprites.drawFire

/** The left part of the mockup's `drawStrip`: the fireplace and the stocking on its mantel, a calm fire. */
internal object FireplaceSprite {
    const val WIDTH = 18
    const val HEIGHT = 12

    fun PixelCanvas.drawFireplace(f: Int) {
        rect(0, 1, 12, 11, STONE)
        rect(0, 0, WIDTH, 1, PLANKS_DARK)
        rect(2, 3, 8, 9, FIREBOX)
        rect(3, 10, 6, 1, TRUNK)
        drawFire(3, 10, 6, 6, f, 2)
        // Stocking
        rect(13, 1, 3, 1, STOCKING_CUFF)
        rect(13, 2, 3, 3, STOCKING)
        rect(13, 5, 4, 2, STOCKING)
    }
}
