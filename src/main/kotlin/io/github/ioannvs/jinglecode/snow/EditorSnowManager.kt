package io.github.ioannvs.jinglecode.snow

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
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/**
 * Snow in the project's code editors (not consoles, diffs or previews), driven by the project animator.
 * Editors come from [EditorSnowListener]; the ones already open when the plugin loads are picked up here.
 * All methods are called on the EDT.
 */
@Service(Service.Level.PROJECT)
class EditorSnowManager(private val project: Project) : Disposable {

    private val snows = LinkedHashMap<Editor, EditorSnow>()
    private val gate get() = FeatureGate.getInstance()
    private val isOn: () -> Boolean = { gate.isActive(JingleFeature.EDITOR_SNOW) }

    /** Seconds of snowfall so far, counting only while it moves; sways the flakes. */
    private var time = 0.0

    init {
        val settings = JingleCodeSettings.getInstance()
        JingleAnimator.getInstance(project).subscribeMotion(
            this, JingleFeature.EDITOR_SNOW, intervalMs = { settings.state.snowFrameRate.intervalMs },
        ) { dt ->
            time += dt
            gate.guard(JingleFeature.EDITOR_SNOW, Unit) {
                if (isOn()) {
                    val state = settings.state
                    snows.values.filter { it.isShowing }.forEach { it.move(dt, time, state.snowAmount, state.snowSpeed) }
                }
            }
        }
        // Switched off or out of season: wipe the snow off right away.
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(FeatureGate.TOPIC, FeatureGate.Listener { snows.values.forEach(EditorSnow::repaint) })
        EditorFactory.getInstance().allEditors.forEach(::attach)
    }

    internal fun attach(editor: Editor) {
        if (editor.isDisposed || editor.project != project || editor.editorKind != EditorKind.MAIN_EDITOR) return
        if (editor in snows) return
        gate.guard(JingleFeature.EDITOR_SNOW, Unit) {
            val snow = EditorSnow(editor, isOn)
            Disposer.register(this, snow)
            snows[editor] = snow
        }
    }

    internal fun detach(editor: Editor) {
        snows.remove(editor)?.let(Disposer::dispose)
    }

    override fun dispose() {
        snows.clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): EditorSnowManager = project.service()
    }
}
