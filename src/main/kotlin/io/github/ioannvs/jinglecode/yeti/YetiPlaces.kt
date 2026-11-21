package io.github.ioannvs.jinglecode.yeti

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.scale.JBUIScale
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.village.VILLAGE_ROWS
import java.awt.Point
import kotlin.math.ceil

/**
 * Where in an editor the yeti can stand: in the free space right after the end of a line's text, never over
 * the text. Content component coordinates. Called on the EDT.
 */
internal object YetiPlaces {
    /** A logical pixel is a tenth of the editor line, like the snow and the village: the yeti sits on their grid. */
    private const val LOGICAL_PER_LINE = 10f

    /** Spaces between the end of the text and the yeti. */
    private const val GAP_CHARS = 2

    fun magnify(editor: Editor): Float = editor.lineHeight / LOGICAL_PER_LINE / JBUIScale.scale(1f)

    /** User pixels per logical pixel. */
    fun pixelUser(editor: Editor): Float {
        val sysScale = JBUIScale.sysScale(editor.contentComponent)
        return PixelCanvas.pixelSize(sysScale, magnify = magnify(editor)) / sysScale
    }

    /**
     * The part of the view where a line is in sight, top to bottom: below the sticky lines, which may cover the
     * top of the view, and above the village, which stands over the code at its bottom.
     */
    fun band(editor: Editor, pixelUser: Float): IntRange {
        val visible = editor.scrollingModel.visibleArea
        val settings = editor.settings
        val sticky = if (settings.areStickyLinesShown()) settings.stickyLinesLimit * editor.lineHeight else 0
        val village = if (FeatureGate.getInstance().isActive(JingleFeature.VILLAGE)) ceil(VILLAGE_ROWS * pixelUser).toInt() else 0
        return (visible.y + sticky) until (visible.y + visible.height - village)
    }

    /**
     * The yeti's top left on [line], [reach] logical pixels wide, or null if he can't stand there: the line is
     * folded away, wraps, has something after its end (inlay hints, inline completion), isn't wholly within
     * [band], or there is no room for him right of its text in the view.
     */
    fun spot(editor: Editor, line: Int, pixelUser: Float, band: IntRange, reach: Int): Point? {
        val document = editor.document
        if (line < 0 || line >= document.lineCount) return null
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        val folding = editor.foldingModel
        if (folding.isOffsetCollapsed(start) || folding.isOffsetCollapsed(end)) return null
        if (editor.offsetToVisualPosition(start).line != editor.offsetToVisualPosition(end).line) return null
        if (editor.inlayModel.getAfterLineEndElementsForLogicalLine(line).isNotEmpty()) return null
        val y = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
        if (y < band.first || y + editor.lineHeight > band.last + 1) return null
        val x = editor.offsetToXY(end).x + GAP_CHARS * spaceWidth(editor)
        val visible = editor.scrollingModel.visibleArea
        if (x < visible.x || x + ceil(reach * pixelUser) > visible.x + visible.width) return null
        return Point(x, y)
    }

    private fun spaceWidth(editor: Editor): Int =
        editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN)).charWidth(' ')
}
