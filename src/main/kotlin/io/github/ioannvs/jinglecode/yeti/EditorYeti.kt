package io.github.ioannvs.jinglecode.yeti

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import io.github.ioannvs.jinglecode.editor.EditorForeground
import io.github.ioannvs.jinglecode.editor.Z_YETI
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.floor

/**
 * One visit: the yeti stands at the end of a line of [editor] and moves with it while the text scrolls or
 * changes above it. Hidden in any way (the line scrolls out of sight, under the sticky lines or the village,
 * gets folded or touched, the tab is switched, the window minimized), he is gone for good: he doesn't come
 * back with the line. A press on him and he waves, vanishes in a puff of snow, and his footprints run off along
 * the line, at a walk, a run or in leaps. Called on the EDT.
 */
internal class EditorYeti(
    private val editor: Editor,
    line: Int,
    /** How long he stands if nobody catches him or hides him. */
    private val lastsMs: Long,
    private val isOn: () -> Boolean,
    /** He was caught on this line. */
    private val onCaught: (line: Int) -> Unit,
) : Disposable {

    private val document = editor.document
    private val marker: RangeMarker = document.createRangeMarker(document.getLineStartOffset(line), document.getLineEndOffset(line))
    private val startedAt = System.nanoTime()
    private var caughtAt: Long? = null
    private var trail = TrailKind.WALK
    private var touched = false

    /** Where he was last painted, to clear it when he moves or goes. */
    private var lastArea: Rectangle? = null

    val isCaught: Boolean get() = caughtAt != null

    init {
        EditorForeground.of(editor).add(Z_YETI, ::paint, this)
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mousePressed(event: EditorMouseEvent) = onPress(event)
        }, this)
        repaint()
    }

    /** A change is coming to the document: one on his line (typing there, a new line before it) scares him off. */
    fun beforeChange(event: DocumentEvent) {
        if (event.document !== document || !marker.isValid) return
        if (event.offset <= marker.endOffset && event.offset + event.oldLength >= marker.startOffset) touched = true
    }

    /** Moves him on a frame; false once the visit is over: his footprints melted, he stood long enough, or was hidden. */
    fun step(): Boolean {
        val over = (if (isCaught) scene() == null else standing() == null) || isHidden()
        repaint()
        return !over
    }

    private fun msSince(nanos: Long): Long = (System.nanoTime() - nanos) / 1_000_000L

    private fun standing(): YetiPose? = YetiTimeline.standing(msSince(startedAt), lastsMs)

    private fun scene(): CaughtScene? = caughtAt?.let { YetiTimeline.caught(msSince(it), trail) }

    private fun isHidden(): Boolean {
        if (editor.isDisposed || !editor.contentComponent.isShowing) return true
        val window = SwingUtilities.getWindowAncestor(editor.contentComponent)
        if (window is Frame && window.extendedState and Frame.ICONIFIED != 0) return true
        return touched || spot() == null
    }

    /** Where he stands now, null if his line can't hold him any more. */
    private fun spot(): Point? {
        if (touched || !marker.isValid || editor.isDisposed) return null
        val pixelUser = YetiPlaces.pixelUser(editor)
        val line = document.getLineNumber(marker.startOffset)
        return YetiPlaces.spot(editor, line, pixelUser, YetiPlaces.band(editor, pixelUser), YETI_REACH)
    }

    private fun onPress(event: EditorMouseEvent) {
        if (!isOn() || isCaught || event.isConsumed || !SwingUtilities.isLeftMouseButton(event.mouseEvent)) return
        // A press in the gutter comes in the gutter's coordinates: it is for a breakpoint or a line, not for him.
        if (event.area != EditorMouseEventArea.EDITING_AREA) return
        if (standing() == null) return
        val at = spot() ?: return
        val pixelUser = YetiPlaces.pixelUser(editor)
        val left = at.x - floor(pixelUser).toInt()
        val width = ceil((YETI_W + 2) * pixelUser).toInt()
        if (!Rectangle(left, at.y, width, editor.lineHeight).contains(event.mouseEvent.point)) return
        // The press stays with him: the caret doesn't jump to the end of the line.
        event.consume()
        caughtAt = System.nanoTime()
        trail = TrailKind.entries.random()
        onCaught(document.getLineNumber(marker.startOffset))
        repaint()
    }

    private fun paint(g: Graphics2D) {
        if (!isOn()) return
        val at = spot() ?: return
        val pixelUser = YetiPlaces.pixelUser(editor)
        val rows = floor(editor.lineHeight / pixelUser).toInt()
        val top = rows - YETI_H
        PixelCanvas.paint(g, at.x, at.y, magnify = YetiPlaces.magnify(editor)) {
            // He rises out of the line and sinks back into it: nothing of him or his puff below its bottom or above its top.
            clipped(-PUFF_OUT, 0, PUFF_OUT + reach(), rows) {
                if (!isCaught) {
                    standing()?.let { drawYeti(0, top, it) }
                    return@clipped
                }
                val scene = scene() ?: return@clipped
                scene.prints.forEach { drawLinePrint(it.x, rows, it.upper) }
                scene.yeti?.let { drawYeti(0, top, it) }
                scene.puff?.let { drawPuff(0, top, it) }
            }
        }
    }

    /** How far right of his spot he and his footprints reach, logical pixels. */
    private fun reach(): Int = if (isCaught) maxOf(YETI_REACH, YetiTimeline.reach(trail)) else YETI_REACH

    /** Repaints where he is and where he was. */
    private fun repaint() {
        if (editor.isDisposed) return
        val content = editor.contentComponent
        lastArea?.let(content::repaint)
        val at = spot()
        lastArea = at?.let {
            val pixelUser = YetiPlaces.pixelUser(editor)
            val out = ceil((PUFF_OUT + 1) * pixelUser).toInt()
            Rectangle(it.x - out, it.y, out + ceil((reach() + 1) * pixelUser).toInt(), editor.lineHeight)
        }
        lastArea?.let(content::repaint)
    }

    override fun dispose() {
        if (!editor.isDisposed) lastArea?.let(editor.contentComponent::repaint)
        lastArea = null
        marker.dispose()
    }

    private companion object {
        /** How far left of him the puff may fly, logical pixels: well within the gap after the text. */
        const val PUFF_OUT = 4
    }
}
