package io.github.ioannvs.jinglecode.hohoho

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleClock
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.drift.SnowmanState
import kotlin.random.Random

/** A flight going on: what was called, which flight it is, whether it is night, and how long ago it took off. */
internal data class FlightNow(val call: HoHoHoCall, val kind: FlightKind, val seed: Int, val night: Boolean, val ageMs: Long) {
    val lastsMs: Long get() = lastsMs(call, kind)
}

internal fun lastsMs(call: HoHoHoCall, kind: FlightKind): Long = when (kind) {
    FlightKind.CLASSIC -> Flyby.lastsMs(call)
    FlightKind.HEIST -> Heist.LASTS_MS
}

/**
 * Santa's flight across the window after `// ho ho ho`: one at a time, calls during it are ignored.
 * Which flight comes is picked at random every time; `ho ho no` is always the classic one, stalling.
 */
@Service(Service.Level.PROJECT)
class SantaFlights(private val project: Project) : Disposable {
    private class Started(val call: HoHoHoCall, val kind: FlightKind, val seed: Int, val night: Boolean, val at: Long)

    @Volatile
    private var flight: Started? = null

    /** User pixels per logical pixel of the flight as it was last painted; 0 before that. */
    @Volatile
    internal var pixelUser: Float = 0f

    internal fun current(): FlightNow? {
        val started = flight ?: return null
        val age = (System.nanoTime() - started.at) / 1_000_000
        return FlightNow(started.call, started.kind, started.seed, started.night, age).takeIf { age < it.lastsMs }
    }

    internal fun start(call: HoHoHoCall, night: Boolean) {
        if (current() != null) return
        val kind = if (call.refused) FlightKind.CLASSIC else FlightKind.entries.random()
        // Small seeds: the hash is a sine.
        val seed = Random.nextInt(1, 2000)
        if (kind == FlightKind.HEIST) {
            // The drift's snowman is the thief: he is gone while the chase goes on, and comes back without his hat if he lost it.
            val hatless = if (Heist.end(seed) == HeistEnd.SNOWMAN_WINS) 0L else HATLESS_MS
            SnowmanState.getInstance(project).goAway(Heist.LASTS_MS, hatless)
        }
        pixelUser = 0f
        flight = Started(call, kind, seed, night, System.nanoTime())
    }

    override fun dispose() = Unit

    companion object {
        /** How long the snowman stays without his hat after he lost it. */
        private const val HATLESS_MS = 60_000L

        @JvmStatic
        fun getInstance(project: Project): SantaFlights = project.service()
    }
}

/**
 * Sends Santa when `ho ho ho` has just been typed in a comment of [project]'s editors. Only typing
 * counts: not pasting, not undo, not opening a file that has it already.
 */
internal class HoHoHoTyping(private val project: Project) : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        if (event.newLength !in 1..2 || project.isDisposed) return
        val gate = FeatureGate.getInstance()
        if (!gate.isActive(JingleFeature.HO_HO_HO)) return
        gate.guard(JingleFeature.HO_HO_HO, Unit) {
            val document = event.document
            if (EditorFactory.getInstance().getEditors(document, project).isEmpty()) return@guard
            if (UndoManager.getInstance(project).isUndoOrRedoInProgress) return@guard
            val end = event.offset + event.newLength
            val lineStart = document.getLineStartOffset(document.getLineNumber(end))
            val call = HoHoHoPhrase.match(document.immutableCharSequence.subSequence(lineStart, end)) ?: return@guard
            val flights = SantaFlights.getInstance(project)
            if (flights.current() != null) return@guard
            val documents = PsiDocumentManager.getInstance(project)
            documents.performForCommittedDocument(document) {
                if (project.isDisposed || end > document.textLength) return@performForCommittedDocument
                gate.guard(JingleFeature.HO_HO_HO, Unit) {
                    val element = documents.getPsiFile(document)?.findElementAt(end - 1)
                    if (PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false) != null) {
                        flights.start(call, Flyby.isNight(JingleClock.now()))
                    }
                }
            }
        }
    }
}

/** Listens to typing in the project's editors for `// ho ho ho`. */
internal class HoHoHoStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(HoHoHoTyping(project), SantaFlights.getInstance(project))
    }
}
