package io.github.ioannvs.jinglecode.snow

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Graphics
import kotlin.math.ceil

/**
 * Snow over one editor, on top of the code. Public API only: painted by a foreground
 * [CustomHighlighterRenderer] on a highlighter over the whole document, so popups and completion
 * stay above it. Called on the EDT.
 */
internal class EditorSnow(private val editor: Editor, private val isOn: () -> Boolean) : Disposable {

    private val field = SnowField()
    private var magnify = 1f

    private val renderer = object : CustomHighlighterRenderer {
        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) = paintSnow(g)

        // The only non-deprecated way to paint after the text; `isForeground` is up for removal.
        @Suppress("UnstableApiUsage")
        override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT
    }

    private val highlighter: RangeHighlighter = editor.markupModel.addRangeHighlighter(
        0, editor.document.textLength, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE,
    ).apply {
        isGreedyToLeft = true
        isGreedyToRight = true
        customRenderer = renderer
    }

    val isShowing: Boolean get() = !editor.isDisposed && editor.contentComponent.isShowing

    /**
     * Moves the snow by [dt] seconds and repaints the visible part of the editor,
     * unless no flake has reached the next pixel yet.
     */
    fun move(dt: Double, time: Double, amount: SnowAmount, speed: SnowSpeed) {
        val visible = editor.scrollingModel.visibleArea
        if (visible.isEmpty) return
        val pixelUser = updateScale()
        val width = ceil(visible.width / pixelUser).toInt()
        val height = ceil(visible.height / pixelUser).toInt()
        val moved = field.step(width, height, dt, time, amount.areaPerFlake, speed.factor)
        if (moved) repaint()
    }

    fun repaint() {
        if (!editor.isDisposed) editor.contentComponent.repaint(editor.scrollingModel.visibleArea)
    }

    /**
     * A logical pixel is a tenth of the editor line, as in the mockup (20 px lines, 2 px pixels),
     * so the snow follows the editor font and zoom. Returns user pixels per logical pixel.
     */
    private fun updateScale(): Float {
        val sysScale = JBUIScale.sysScale(editor.contentComponent)
        magnify = editor.lineHeight / LOGICAL_PER_LINE / JBUIScale.scale(1f)
        return PixelCanvas.pixelSize(sysScale, magnify = magnify) / sysScale
    }

    private fun paintSnow(g: Graphics) {
        if (!isOn() || field.flakes.isEmpty()) return
        // White snow is lost on a light editor: there it is ice blue.
        val color = if (ColorUtil.isDark(editor.colorsScheme.defaultBackground)) JinglePalette.SNOW else JinglePalette.ICE
        // The view's corner now, not at the last frame: the snow stays put while scrolling.
        val visible = editor.scrollingModel.visibleArea
        PixelCanvas.paint(g, visible.x, visible.y, magnify = magnify) {
            for (flake in field.flakes) {
                px(PixelCanvas.jsRound(flake.x), PixelCanvas.jsRound(flake.y), color)
            }
        }
    }

    override fun dispose() {
        highlighter.dispose()
        repaint()
    }

    private companion object {
        const val LOGICAL_PER_LINE = 10f
    }
}
