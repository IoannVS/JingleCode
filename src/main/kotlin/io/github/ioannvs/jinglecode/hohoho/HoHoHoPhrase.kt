package io.github.ioannvs.jinglecode.hohoho

/**
 * What a `// ho ho ho` asked for: how many [hos] (the banner gets longer with them), whether it was
 * [loud] (all capitals: a bigger flight with four reindeer), in Russian ([cyrillic], `хо-хо-хо`), and
 * whether it ended in a "no" ([refused], `ho ho no`: the sleigh stalls and backs off).
 */
internal data class HoHoHoCall(val hos: Int, val loud: Boolean = false, val cyrillic: Boolean = false, val refused: Boolean = false) {
    /** What the banner behind the sleigh says, in the pixel font's letters. */
    val banner: String
        get() {
            val ho = if (cyrillic) "ХО" else "HO"
            val words = List(hos.coerceAtMost(HoHoHoPhrase.MAX_HOS)) { ho } + listOfNotNull(if (refused) (if (cyrillic) "НО" else "NO") else null)
            return words.joinToString(" ")
        }
}

/** Finds `ho ho ho` (or `хо-хо-хо`, `HO HO HO`, `ho ho no`) right where it has just been typed. */
internal object HoHoHoPhrase {
    /** The banner never gets longer than this many "ho". */
    const val MAX_HOS = 8

    /** Only this much of the line before the caret is looked at. */
    const val LOOK_BACK = 120

    // Latin and Cyrillic letters by hand: "хо" is typed with either "о", and case-insensitive matching
    // would also need its Unicode flag. Not after a letter or digit, so "echo ho ho" is only two of them.
    private const val HO = "[hHхХ][oOоО]"
    private const val NO = "[nNнН][oOоО]"
    private val PHRASE = Regex("(?:^|[^\\p{L}\\p{N}_])((?:$HO[\\s,.!\\-]*){2,}(?:$HO|$NO))$")
    private val WORD = Regex("$HO|$NO")

    /**
     * The call if [beforeCaret], the line up to where something was just typed, ends in the phrase:
     * at least three "ho", or at least two and a "no".
     */
    fun match(beforeCaret: CharSequence): HoHoHoCall? {
        val tail = beforeCaret.subSequence(maxOf(0, beforeCaret.length - LOOK_BACK), beforeCaret.length)
        val run = PHRASE.find(tail)?.groupValues?.get(1) ?: return null
        val words = WORD.findAll(run).map { it.value }.toList()
        val refused = words.last()[0] in "nNнН"
        return HoHoHoCall(
            hos = if (refused) words.size - 1 else words.size,
            loud = run.none { it.isLowerCase() },
            cyrillic = run.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC },
            refused = refused,
        )
    }
}
