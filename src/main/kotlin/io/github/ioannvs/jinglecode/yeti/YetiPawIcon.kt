package io.github.ioannvs.jinglecode.yeti

import com.intellij.util.ui.JBUI
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/** The notification's icon: a footprint in pixels, two user pixels a logical one, like the other 16 px icons. */
internal object YetiPawIcon : Icon {
    private const val SIZE = 16
    private const val PIXELS = SIZE / 2

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        PixelCanvas.paint(g, x, y, magnify = 2f) {
            val at = (PIXELS - PAW) / 2
            drawPaw(at, at + 1)
        }
    }

    override fun getIconWidth(): Int = JBUI.scale(SIZE)

    override fun getIconHeight(): Int = JBUI.scale(SIZE)
}
