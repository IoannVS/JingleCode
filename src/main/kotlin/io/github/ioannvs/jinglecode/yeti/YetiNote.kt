package io.github.ioannvs.jinglecode.yeti

import java.time.LocalDate
import kotlin.random.Random

/**
 * What the notification says after he is caught, picked at random every time: where and when he was seen (a
 * sighting report, its place a link), which meeting of the season this was, or a question. The three kinds are
 * as likely; the question is one of three, the last a nod to `// ho ho ho`.
 */
internal enum class YetiNote(val key: String) {
    SEEN("yeti.seen"),
    MEETING("yeti.meeting"),
    WAS_IT("yeti.question.was.it"),
    DO_THEY_EXIST("yeti.question.exist"),
    HO_HO_HO("yeti.question.hohoho");

    companion object {
        private val QUESTIONS = listOf(WAS_IT, DO_THEY_EXIST, HO_HO_HO)

        fun pick(random: Random): YetiNote = when (random.nextInt(3)) {
            0 -> SEEN
            1 -> MEETING
            else -> QUESTIONS.random(random)
        }

        /**
         * The season a day belongs to, by the year it starts in: December and the January after it are one season.
         * Meetings are counted per season.
         */
        fun seasonOf(date: LocalDate): Int = if (date.monthValue >= 7) date.year else date.year - 1
    }
}
