package io.github.ioannvs.jinglecode.village

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleClock
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import io.github.ioannvs.jinglecode.settings.JingleCodeSettingsListener
import javax.swing.Timer

/**
 * The village in the project's code editors (not consoles, diffs or previews). Things move in it on
 * every animator frame (smoke, sails, the lift, skiers), so each frame repaints its strip; with the
 * animation paused a minute timer still catches the evening and the next day. A change in the settings
 * (the style among them) shows right away.
 * All methods are called on the EDT.
 */
@Service(Service.Level.PROJECT)
class EditorVillageManager(private val project: Project) : Disposable {

    private val villages = LinkedHashMap<Editor, EditorVillage>()
    private val gate get() = FeatureGate.getInstance()
    private val isOn: () -> Boolean = { gate.isActive(JingleFeature.VILLAGE) }
    private val time: () -> VillageTime = { VillageTime.at(JingleClock.now()) }
    private val style: () -> VillageStyle = { JingleCodeSettings.getInstance().state.villageStyle }
    private val residents: () -> ResidentFrequency? = {
        JingleCodeSettings.getInstance().state.let { if (it.residentsEnabled) it.residentFrequency else null }
    }

    /** The animator frame, kept by the subscription below; it stands still while the animation is paused. */
    private var frame = 0

    /** Snow falling in the editor or stars over the village: the snowflake button switches between them. */
    private val showStars: () -> Boolean = { !gate.isActive(JingleFeature.EDITOR_SNOW) }
    private val clock = Timer(60_000) { if (isOn()) villages.values.forEach(EditorVillage::refresh) }

    init {
        clock.start()
        JingleAnimator.getInstance(project).subscribe(this, JingleFeature.VILLAGE) {
            frame = (it.tick % TICK_WRAP).toInt()
            villages.values.forEach(EditorVillage::repaintStrip)
        }
        // Switched on or off, out of season, another style: show it right away.
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(FeatureGate.TOPIC, FeatureGate.Listener { villages.values.forEach(EditorVillage::repaint) })
        connection.subscribe(JingleCodeSettingsListener.TOPIC, JingleCodeSettingsListener { villages.values.forEach(EditorVillage::repaint) })
        EditorFactory.getInstance().allEditors.forEach(::attach)
    }

    internal fun attach(editor: Editor) {
        if (editor.isDisposed || editor.project != project || editor.editorKind != EditorKind.MAIN_EDITOR) return
        if (editor in villages) return
        gate.guard(JingleFeature.VILLAGE, Unit) {
            val village = EditorVillage(editor, isOn, time, style, { frame }, showStars, residents)
            Disposer.register(this, village)
            villages[editor] = village
        }
    }

    internal fun detach(editor: Editor) {
        villages.remove(editor)?.let(Disposer::dispose)
    }

    override fun dispose() {
        clock.stop()
        villages.clear()
    }

    companion object {
        /** Keeps the frame an Int; divisible by the periods the scenes use. */
        private const val TICK_WRAP = 1_201_200L

        @JvmStatic
        fun getInstance(project: Project): EditorVillageManager = project.service()
    }
}
