package io.github.ioannvs.jinglecode.yeti

import kotlin.random.Random

/** A line the yeti may stand on, and how much he likes it. */
internal data class YetiLine(val line: Int, val weight: Int)

/**
 * Which line the yeti picks. Any line that passed the filters may do; he only leans towards some:
 * TODO and FIXME (abandoned places), "cold" words (`freeze`, `cold`, `cache`, `снег`...) and comments.
 */
internal object YetiLines {
    const val TODO_WEIGHT = 6
    const val COLD_WEIGHT = 6
    const val COMMENT_WEIGHT = 3
    const val PLAIN_WEIGHT = 1

    private val TODO = Regex("(?<![\\p{L}\\p{N}_])(?:TODO|FIXME)(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

    /** Words split at camel humps too: `coldStart` is `cold` and `Start`, `ICE_AGE` is `ICE` and `AGE`. */
    private val WORD = Regex("\\p{Lu}+(?!\\p{Ll})|\\p{Lu}?\\p{Ll}+")

    /** Short words, whole: as stems they would be inside too many others (`ice` in `service`). */
    private val COLD = setOf("ice", "icy", "cold", "colder", "coldest", "chill", "chilly", "polar", "glacier", "yeti")

    /** Stems: `frozenset`, `snowflake`, `caching`, and Russian words with their endings. */
    private val COLD_STEMS = listOf(
        "freez", "froz", "snow", "cach", "frost", "icicle", "winter",
        "холод", "снег", "снеж", "мороз", "зим", "лёд", "лед", "льд", "кэш", "кеш", "йети",
    )

    fun isCold(text: CharSequence): Boolean = WORD.findAll(text).any { match ->
        val word = match.value.lowercase()
        word in COLD || COLD_STEMS.any { word.startsWith(it) }
    }

    fun hasTodo(text: CharSequence): Boolean = TODO.containsMatchIn(text)

    fun weight(text: CharSequence, inComment: Boolean): Int = when {
        hasTodo(text) -> TODO_WEIGHT
        isCold(text) -> COLD_WEIGHT
        inComment -> COMMENT_WEIGHT
        else -> PLAIN_WEIGHT
    }

    /** One of [candidates] at random, each as likely as its weight; null when there are none. */
    fun pick(candidates: List<YetiLine>, random: Random): YetiLine? {
        val total = candidates.sumOf { it.weight }
        if (total <= 0) return null
        var left = random.nextInt(total)
        for (candidate in candidates) {
            left -= candidate.weight
            if (left < 0) return candidate
        }
        return candidates.last()
    }
}
