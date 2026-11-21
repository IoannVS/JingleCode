package io.github.ioannvs.jinglecode.yeti

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.ioannvs.jinglecode.JingleCodeBundle
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleClock
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.popups.PopupSnow
import java.awt.Point
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import javax.swing.Timer
import kotlin.math.abs
import kotlin.random.Random

/**
 * Brings the yeti to the project's editors now and then ([YetiSchedule]) or when a comment calls him, keeps
 * his one visit going ([EditorYeti]) and, once he is caught, leaves a notification with a footprint.
 *
 * He comes only to someone at the IDE who isn't typing right now: the window is active, there was input in the
 * last minutes, but no typing for a little while; to an editor showing a project file for at least a minute.
 * All methods are called on the EDT.
 */
@Service(Service.Level.PROJECT)
class YetiWatch(private val project: Project) : Disposable {

    /** The word that called him: he comes only if it still stands on its own once the typing stops. */
    private class Call(val editor: Editor, val word: RangeMarker, val at: Long)

    private val gate get() = FeatureGate.getInstance()
    private val random = Random.Default
    private var visit: EditorYeti? = null
    private var call: Call? = null

    /** Since when each editor has been showing, `System.nanoTime`. */
    private val shownSince = WeakHashMap<Editor, Long>()

    private var lastEdit = 0L
    private var lastLook = 0L

    /**
     * Ends the visit once the frames stop coming: the animator stood still (Power Save, presentation mode, animation
     * off, a minimized window), and whoever stood there is gone. Set again on every frame of a visit.
     */
    private val stillness = Timer(PAUSE_MS.toInt()) { visit?.let(::end) }.apply { isRepeats = false }

    init {
        JingleAnimator.getInstance(project).subscribe(this, JingleFeature.YETI) { onFrame() }
        // Switched off or out of season: he is gone right away.
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { if (!gate.isActive(JingleFeature.YETI)) visit?.let(::end) })
    }

    /** Something is about to change in a document of the project's editors. */
    internal fun beforeChange(event: DocumentEvent) {
        visit?.beforeChange(event)
    }

    internal fun edited() {
        lastEdit = System.nanoTime()
    }

    /**
     * `yeti` (or `йети`) was typed in a comment in [editor], at [start]: he comes as soon as the typing stops, if the
     * word wasn't typed on into another one, and if there is a line for him.
     */
    internal fun called(editor: Editor, start: Int) {
        if (visit != null) return
        dropCall()
        call = Call(editor, editor.document.createRangeMarker(start, start + YetiPhrase.LENGTH), System.nanoTime())
    }

    private fun dropCall() {
        call?.word?.dispose()
        call = null
    }

    private fun onFrame() {
        // The frames keep coming for the other decorations while he is switched off.
        if (!gate.isActive(JingleFeature.YETI)) {
            dropCall()
            return
        }
        val now = System.nanoTime()
        visit?.let { current ->
            if (gate.guard(JingleFeature.YETI, false) { current.step() }) stillness.restart() else end(current)
            return
        }
        // The animator's timer holds no read lock on the EDT: the documents and the PSI are read under one.
        call?.let { called ->
            if (now - called.at > CALL_WAIT_MS * 1_000_000L) dropCall()
            else if (now - lastEdit >= CALL_QUIET_MS * 1_000_000L) gate.guard(JingleFeature.YETI, Unit) { runReadAction { answer(called) } }
            return
        }
        if (now - lastLook < LOOK_EVERY_MS * 1_000_000L) return
        lastLook = now
        gate.guard(JingleFeature.YETI, Unit) { runReadAction { look() } }
    }

    /** The typing has stopped after a call: the word must still be just that word, `yetis` doesn't call him. */
    private fun answer(called: Call) {
        val word = called.word
        if (!word.isValid || !YetiPhrase.standsAlone(word.document.immutableCharSequence, word.startOffset)) dropCall()
        else comeTo(called.editor, called = true)
    }

    /** Whether it is time for a visit, and if so, where. */
    private fun look() {
        val editors = showingEditors()
        if (WindowManager.getInstance().getFrame(project)?.isActive != true) return
        if (JingleAnimator.getInstance(project).msSinceInput > ACTIVE_WITHIN_MS) return
        val now = System.nanoTime()
        if (now - lastEdit < QUIET_MS * 1_000_000L) return
        val diary = YetiDiary.getInstance()
        val time = JingleClock.now()
        val day = YetiSchedule.today(diary.day, time, random)
        if (day != diary.day) diary.day = day
        if (!YetiSchedule.mayCome(day, time)) return
        // An editor out of the way first: one in a split that doesn't have the focus.
        val ready = editors.filter { now - (shownSince[it] ?: now) >= SHOWN_FOR_MS * 1_000_000L }
            .sortedBy { it.contentComponent.hasFocus() }
        if (ready.any { comeTo(it, called = false) }) diary.day = YetiSchedule.afterVisit(day, time, random)
    }

    /** The code editors showing now; notes since when each has been showing. */
    private fun showingEditors(): List<Editor> {
        val editors = FileEditorManager.getInstance(project).selectedEditors
            .filterIsInstance<TextEditor>().map { it.editor }.filter { isForYeti(it, anyFile = false) }
        shownSince.keys.retainAll(editors.toSet())
        val now = System.nanoTime()
        editors.forEach { shownSince.putIfAbsent(it, now) }
        return editors
    }

    /**
     * A code editor of this project, showing, with a file of the project in it, or [anyFile]. A file of the project is
     * an editable file on disk under the project's folder: libraries are in jars or read-only. Not `ProjectFileIndex`:
     * it is a slow operation, which the platform forbids on the EDT.
     */
    private fun isForYeti(editor: Editor, anyFile: Boolean): Boolean {
        if (editor.isDisposed || editor.project != project || editor.editorKind != EditorKind.MAIN_EDITOR) return false
        if (!editor.contentComponent.isShowing) return false
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        if (anyFile) return true
        val base = project.basePath ?: return false
        return file.isInLocalFileSystem && file.isWritable && file.path.startsWith("$base/")
    }

    /**
     * Puts the yeti on a line of [editor]; false if there is no line for him there now. [called] from a comment, he
     * comes to any file, a scratch file too.
     */
    private fun comeTo(editor: Editor, called: Boolean): Boolean {
        if (!isForYeti(editor, anyFile = called)) return false
        val line = pickLine(editor) ?: return false
        val yeti = EditorYeti(editor, line, YetiTimeline.STAY_MS.random(random), { gate.isActive(JingleFeature.YETI) }) { caught(editor, it) }
        Disposer.register(this, yeti)
        visit = yeti
        stillness.restart()
        dropCall()
        return true
    }

    /**
     * A line in the lower part of the view, away from the carets, where he fits after the text; TODO and FIXME,
     * cold words and comments more likely ([YetiLines]).
     */
    private fun pickLine(editor: Editor): Int? {
        val pixelUser = YetiPlaces.pixelUser(editor)
        val band = YetiPlaces.band(editor, pixelUser)
        val visible = editor.scrollingModel.visibleArea
        val top = maxOf(band.first, visible.y + (visible.height * PICK_FROM).toInt())
        if (top >= band.last) return null
        val document = editor.document
        val first = editor.xyToLogicalPosition(Point(0, top)).line
        val last = minOf(editor.xyToLogicalPosition(Point(0, band.last)).line, document.lineCount - 1, first + MAX_LINES)
        val carets = editor.caretModel.allCarets.map { it.logicalPosition.line }
        val psi = PsiDocumentManager.getInstance(project).let { if (it.isCommitted(document)) it.getPsiFile(document) else null }
        val text = document.immutableCharSequence
        val candidates = (first..last)
            .filter { line -> carets.none { abs(it - line) <= CARET_GAP } }
            .filter { line -> YetiPlaces.spot(editor, line, pixelUser, top..band.last, YETI_REACH) != null }
            .map { line ->
                val start = document.getLineStartOffset(line)
                val end = document.getLineEndOffset(line)
                YetiLine(line, YetiLines.weight(text.subSequence(start, end), psi != null && hasComment(psi, text, start, end)))
            }
        return YetiLines.pick(candidates, random)?.line
    }

    /** Whether the line from [start] to [end] begins or ends in a comment. */
    private fun hasComment(psi: PsiFile, text: CharSequence, start: Int, end: Int): Boolean {
        val first = (start until end).firstOrNull { !text[it].isWhitespace() } ?: return false
        val last = (end - 1 downTo start).first { !text[it].isWhitespace() }
        return listOf(first, last).any { PsiTreeUtil.getParentOfType(psi.findElementAt(it), PsiComment::class.java, false) != null }
    }

    /** Where and when he was caught, and which meeting of the season it was. */
    private class Sighting(val file: VirtualFile?, val line: Int, val time: LocalDateTime, val meeting: Int)

    /** He was caught on [line] of [editor]: not again today; the notification comes a moment after he vanished. */
    private fun caught(editor: Editor, line: Int) {
        val diary = YetiDiary.getInstance()
        val time = JingleClock.now()
        diary.day = YetiSchedule.afterCatch(YetiSchedule.today(diary.day, time, random))
        val sighting = Sighting(FileDocumentManager.getInstance().getFile(editor.document), line, time, diary.meet(time.toLocalDate()))
        Timer((YetiTimeline.GONE_MS + NOTIFY_AFTER_GONE_MS).toInt()) { notifyGone(sighting) }.apply { isRepeats = false }.start()
    }

    private fun end(yeti: EditorYeti) {
        if (visit === yeti) {
            visit = null
            stillness.stop()
        }
        Disposer.dispose(yeti)
    }

    /** The notification he leaves: a footprint for an icon and one of [YetiNote], in the IDE's language. */
    private fun notifyGone(sighting: Sighting) {
        if (project.isDisposed || !gate.isActive(JingleFeature.YETI)) return
        gate.guard(JingleFeature.YETI, Unit) {
            val note = YetiNote.pick(random).let { if (it == YetiNote.SEEN && sighting.file == null) YetiNote.MEETING else it }
            val text = when (note) {
                YetiNote.SEEN -> JingleCodeBundle.message(note.key, sighting.time.format(TIME))
                YetiNote.MEETING -> JingleCodeBundle.message(note.key, sighting.meeting)
                else -> JingleCodeBundle.message(note.key)
            }
            val notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(text, NotificationType.INFORMATION)
                .setIcon(YetiPawIcon)
            val file = sighting.file
            if (note == YetiNote.SEEN && file != null) {
                // The place is a link: the file and the line, as in a stack trace.
                @NlsSafe val place = "${file.name}:${sighting.line + 1}"
                notification.addAction(NotificationAction.createSimple(place) {
                    if (file.isValid) OpenFileDescriptor(project, file, sighting.line, 0).navigate(true)
                })
            }
            PopupSnow.getInstance(project).bringsNoLetter(notification)
            notification.notify(project)
        }
    }

    override fun dispose() {
        stillness.stop()
        visit = null
        dropCall()
    }

    companion object {
        /** The plugin's notification group (plugin.xml); its id is its name in the settings, the same in every language. */
        const val NOTIFICATION_GROUP = "Jingle Code"

        /** How often he considers coming. */
        private const val LOOK_EVERY_MS = 5_000L

        /** There was input in the IDE this recently: someone is at it. */
        private const val ACTIVE_WITHIN_MS = 5 * 60_000L

        /** And no typing for this long: they are reading or thinking. */
        private const val QUIET_MS = 25_000L

        /** The editor has been showing for this long. */
        private const val SHOWN_FOR_MS = 60_000L

        /** Called from a comment, he comes once the typing has stopped for this long... */
        private const val CALL_QUIET_MS = 1_500L

        /** ...and gives up after this long if he finds no line. */
        private const val CALL_WAIT_MS = 30_000L

        /** No frame for this long means the animator was paused. */
        private const val PAUSE_MS = 2_000L

        /** The notification comes this long after he vanished, while his footprints still run. */
        private const val NOTIFY_AFTER_GONE_MS = 600L

        /** When he was seen, in a sighting report. */
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** He stands in the lower part of the view, from this far down. */
        private const val PICK_FROM = 0.4

        /** Lines this close to a caret are where someone works: not there. */
        private const val CARET_GAP = 4

        /** At most this many lines are looked at. */
        private const val MAX_LINES = 400

        @JvmStatic
        fun getInstance(project: Project): YetiWatch = project.service()
    }
}
