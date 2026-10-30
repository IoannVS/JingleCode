package io.github.ioannvs.jinglecode.settings

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.sprites.drawFire
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JComponent

/**
 * A small animated sample on the settings page: fire, a garland and the palette,
 * drawn through [PixelCanvas] and driven by the project's animator.
 */
internal class PalettePreview : JComponent() {
    var frame: Int = 0

    override fun getPreferredSize(): Dimension =
        Dimension(PixelCanvas.userSize(WIDTH, this, ZOOM), PixelCanvas.userSize(HEIGHT, this, ZOOM))

    override fun getMinimumSize(): Dimension = preferredSize

    override fun paintComponent(g: Graphics) {
        PixelCanvas.paint(g, 0, 0, ZOOM) {
            val f = frame
            rect(0, 0, WIDTH, HEIGHT, JinglePalette.IDE_BACKGROUND)

            // Garland like on the mantel in the mockup: colors shift every second frame.
            rect(2, 3, WIDTH - 4, 1, JinglePalette.FIR)
            for (i in 0 until 15) {
                px(3 + i * 4, 2, JinglePalette.BULBS[(i + f / 2) % 4])
            }

            // Log and fire.
            rect(3, 17, 14, 2, JinglePalette.TRUNK)
            px(3, 17, JinglePalette.LOG_CUT)
            drawFire(4, 17, 12, 11, f, 4)

            // Palette swatches, two rows of ten.
            SWATCHES.forEachIndexed { i, color ->
                rect(22 + (i % 10) * 4, 8 + (i / 10) * 5, 3, 3, color)
            }
            rect(20, 18, WIDTH - 22, 1, JinglePalette.SNOW)
        }
    }

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 20
        const val ZOOM = 3

        val SWATCHES = JinglePalette.FIRE + JinglePalette.BULBS + listOf(
            JinglePalette.SNOW, JinglePalette.ICE, JinglePalette.ICE_TIP, JinglePalette.FIR,
            JinglePalette.FIR_LIGHT, JinglePalette.TRUNK, JinglePalette.GOLD, JinglePalette.SANTA_RED,
            JinglePalette.STOCKING, JinglePalette.REINDEER, JinglePalette.PENGUIN_BLACK,
        )
    }
}
