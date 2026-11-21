package io.github.ioannvs.jinglecode.yeti

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The yeti's day, kept across restarts so that a restart doesn't bring him back after he was caught, and the
 * meetings with him this season.
 * One for the whole IDE: there is one yeti, whichever project window he shows up in. Not a setting: it
 * stays on this machine.
 */
@Service(Service.Level.APP)
@State(name = "JingleCodeYeti", storages = [Storage("jingle-code-yeti.xml", roamingType = RoamingType.DISABLED)])
internal class YetiDiary : SimplePersistentStateComponent<YetiDiary.State>(State()) {

    class State : BaseState() {
        var date by string()
        var visits by property(0)
        var caught by property(false)
        var notBefore by string()

        /** The season [meetings] were counted in, by the year it starts in ([YetiNote.seasonOf]). */
        var season by property(0)
        var meetings by property(0)
    }

    /** Counts a meeting on [date]; returns which meeting of its season this one is. */
    fun meet(date: LocalDate): Int {
        val season = YetiNote.seasonOf(date)
        if (state.season != season) {
            state.season = season
            state.meetings = 0
        }
        state.meetings++
        return state.meetings
    }

    var day: YetiDay?
        get() {
            val date = state.date?.let(::parseDate) ?: return null
            return YetiDay(date, state.visits, state.caught, state.notBefore?.let(::parseTime))
        }
        set(value) {
            state.date = value?.date?.toString()
            state.visits = value?.visits ?: 0
            state.caught = value?.caught ?: false
            state.notBefore = value?.notBefore?.toString()
        }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    }
    catch (_: DateTimeParseException) {
        null
    }

    private fun parseTime(text: String): LocalDateTime? = try {
        LocalDateTime.parse(text)
    }
    catch (_: DateTimeParseException) {
        null
    }

    companion object {
        @JvmStatic
        fun getInstance(): YetiDiary = service()
    }
}
