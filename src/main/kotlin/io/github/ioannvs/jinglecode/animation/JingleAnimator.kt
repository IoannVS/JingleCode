package io.github.ioannvs.jinglecode.animation

import com.intellij.ide.PowerSaveMode
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import javax.swing.Timer

/**
 * One animation clock per project: a single [Timer] ticking [FRAME_MS], shared by all decorations.
 *
 * The timer runs only while a subscriber's decoration is active (see [FeatureGate]), animation is
 * enabled in settings and the project window (or a dialog it owns) is active. It pauses in Power Save
 * and Presentation mode. While paused, decorations keep showing their last frame.
 *
 * All methods are called on the EDT.
 */
@Service(Service.Level.PROJECT)
class JingleAnimator(private val project: Project) : Disposable {

    private class Subscriber(val feature: JingleFeature?, val listener: JingleFrameListener)

    private val subscribers = mutableListOf<Subscriber>()
    private val timer = Timer(FRAME_MS) { tick() }
    private var tick = 0L

    /** The frame decorations should draw now, also while paused. */
    var current: JingleFrame = JingleFrame(0, 0)
        private set

    private val focusListener = PropertyChangeListener { updateState() }

    init {
        timer.isRepeats = true
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener { updateState() })
        connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateState() })
        connection.subscribe(FeatureGate.TOPIC, FeatureGate.Listener { updateState() })

        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addPropertyChangeListener(ACTIVE_WINDOW, focusListener)
        Disposer.register(this) { focusManager.removePropertyChangeListener(ACTIVE_WINDOW, focusListener) }
    }

    /**
     * Calls [listener] on every frame until [parentDisposable] is disposed.
     * The timer runs for it only while [feature] is active; `null` means always (the settings preview).
     */
    fun subscribe(parentDisposable: Disposable, feature: JingleFeature?, listener: JingleFrameListener) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val subscriber = Subscriber(feature, listener)
        subscribers += subscriber
        Disposer.register(parentDisposable) {
            subscribers -= subscriber
            updateState()
        }
        updateState()
    }

    private fun tick() {
        tick++
        current = JingleFrame((tick % FRAMES).toInt(), tick)
        for (subscriber in subscribers.toList()) {
            subscriber.listener.onFrame(current)
        }
    }

    private fun updateState() {
        if (shouldRun()) {
            if (!timer.isRunning) timer.start()
        }
        else {
            timer.stop()
        }
    }

    private fun shouldRun(): Boolean {
        if (project.isDisposed || !JingleCodeSettings.getInstance().state.animationEnabled) return false
        val gate = FeatureGate.getInstance()
        if (subscribers.none { it.feature == null || gate.isActive(it.feature) }) return false
        if (PowerSaveMode.isEnabled() || UISettings.getInstance().presentationMode) return false
        return isProjectWindowActive()
    }

    private fun isProjectWindowActive(): Boolean {
        val frame = WindowManager.getInstance().getFrame(project) ?: return false
        var window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        while (window != null) {
            if (window === frame) return true
            window = window.owner
        }
        return false
    }

    override fun dispose() {
        timer.stop()
        subscribers.clear()
    }

    companion object {
        /** Frames in a loop, as in the mockup (`FRAMES`). */
        const val FRAMES = 6

        /** Frame length, as in the mockup (`FRAME_MS`). */
        const val FRAME_MS = 140

        private const val ACTIVE_WINDOW = "activeWindow"

        @JvmStatic
        fun getInstance(project: Project): JingleAnimator = project.service()
    }
}

/**
 * @param index frame in the loop, `0 until FRAMES` (the mockup's `f`)
 * @param tick frames since the project opened, counting only while running
 */
data class JingleFrame(val index: Int, val tick: Long)

fun interface JingleFrameListener {
    fun onFrame(frame: JingleFrame)
}
