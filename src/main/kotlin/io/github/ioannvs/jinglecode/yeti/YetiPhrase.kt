package io.github.ioannvs.jinglecode.yeti

import java.util.regex.Pattern

/** Finds the word `yeti` (or `йети`) right where it has just been typed: it calls the yeti. */
internal object YetiPhrase {
    /** Only this much of the line before the caret is looked at. */
    const val LOOK_BACK = 20

    /** How long the word is: as long in both languages. */
    const val LENGTH = 4

    // Case-insensitive for Cyrillic too, which needs the Unicode flag. Not after a letter or digit.
    private val WORD = Pattern.compile("(?:^|[^\\p{L}\\p{N}_])(?:yeti|йети)$", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE).toRegex()

    /** Whether [beforeCaret], the line up to where something was just typed, ends in the word. */
    fun matches(beforeCaret: CharSequence): Boolean {
        val tail = beforeCaret.subSequence(maxOf(0, beforeCaret.length - LOOK_BACK), beforeCaret.length)
        return WORD.containsMatchIn(tail)
    }

    /** Whether the word at [start] in [text] still stands on its own: not typed on into `yetis` or `йетиный`. */
    fun standsAlone(text: CharSequence, start: Int): Boolean {
        val end = start + LENGTH
        if (start < 0 || end > text.length || !matches(text.subSequence(start, end))) return false
        return end == text.length || !text[end].let { it.isLetterOrDigit() || it == '_' }
    }
}
