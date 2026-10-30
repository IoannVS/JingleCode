package io.github.ioannvs.jinglecode.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.Graphics
import java.awt.Graphics2D

/** The village under the snow. */
internal const val Z_VILLAGE = 0

/** The snow over everything. */
internal const val Z_SNOW = 10

/**
 * Everything the plugin paints over the text of one editor, in one place and in a fixed order: one
 * highlighter over the whole document whose renderer paints after the text and calls the parts by
 * their z, lowest first. Separate highlighters would be painted in no order the editor promises.
 * Called on the EDT.
 */
internal class EditorForeground private constructor(private val editor: Editor) {

    fun interface Part {
        fun paint(g: Graphics2D)
    }

    private class Entry(val z: Int, val part: Part)

    private val parts = ArrayList<Entry>()
    private var highlighter: RangeHighlighter? = null

    private val renderer = object : CustomHighlighterRenderer {
        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            for (entry in parts.toList()) {
                val g2 = g.create() as Graphics2D
                try {
                    entry.part.paint(g2)
                }
                finally {
                    g2.dispose()
                }
            }
        }

        // The only non-deprecated way to paint after the text; `isForeground` is up for removal.
        @Suppress("UnstableApiUsage")
        override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT
    }

    /** Adds [part] at [z] until [parentDisposable] is disposed. */
    fun add(z: Int, part: Part, parentDisposable: Disposable) {
        val entry = Entry(z, part)
        parts += entry
        parts.sortBy { it.z }
        if (highlighter == null) {
            highlighter = editor.markupModel.addRangeHighlighter(
                0, editor.document.textLength, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE,
            ).apply {
                isGreedyToLeft = true
                isGreedyToRight = true
                customRenderer = renderer
            }
        }
        Disposer.register(parentDisposable) { remove(entry) }
    }

    private fun remove(entry: Entry) {
        parts -= entry
        if (parts.isNotEmpty()) return
        highlighter?.dispose()
        highlighter = null
        editor.putUserData(KEY, null)
    }

    companion object {
        private val KEY = Key.create<EditorForeground>("JingleCode.EditorForeground")

        fun of(editor: Editor): EditorForeground =
            editor.getUserData(KEY) ?: EditorForeground(editor).also { editor.putUserData(KEY, it) }
    }
}
