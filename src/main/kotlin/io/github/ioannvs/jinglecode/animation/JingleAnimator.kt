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
import java.awt.AWTEvent
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.beans.PropertyChangeListener
import javax.swing.Timer

/**
 * One animation clock per project, shared by all decorations: frames of [FRAME_MS] for sprite loops
 * ([subscribe]) and, for things that move continuously like falling snow, a smooth [MOTION_MS] clock
 * ([subscribeMotion]), as the mockup moves them on every browser frame.
 *
 * A timer runs only while one of its subscribers' decorations is active (see [FeatureGate]) and animation
 * is enabled in settings. Both pause in Power Save and Presentation mode and while the project window is
 * minimized; while paused, decorations keep
 * showing their last frame. While the project window (or a dialog it owns) is not active, or nobody has
 * touched the keyboard or mouse for [IDLE_MS], everything goes on in slow motion, [SLOWDOWN] times slower:
 * the timers tick that much less often and the motion covers that much less per tick, so the snow still
 * steps pixel by pixel, and the IDE repaints that much less under it.
 *
 * All methods are called on the EDT.
 */
@Service(Service.Level.PROJECT)
class JingleAnimator(private val project: Project) : Disposable {

    private class Subscriber<L>(val feature: JingleFeature?, val listener: L, val intervalMs: () -> Int = { FRAME_MS })

    private val subscribers = mutableListOf<Subscriber<JingleFrameListener>>()
    private val timer = Timer(FRAME_MS) { tick() }
    private var tick = 0L

    private val motionSubscribers = mutableListOf<Subscriber<JingleMotionListener>>()
    private val motionTimer = Timer(MOTION_MS) { move() }
    private var lastMotion = 0L

    /** The frame decorations should draw now, also while paused. */
    var current: JingleFrame = JingleFrame(0, 0)
        private set

    private val focusListener = PropertyChangeListener { updateState() }

    /** How many times slower everything goes now: 1, or [SLOWDOWN] in slow motion. */
    private var slowdown = 1

    private var lastInput = System.nanoTime()
    private var idle = false

    // Only notes the time: it sees every mouse move in the IDE.
    private val inputListener = AWTEventListener {
        lastInput = System.nanoTime()
        if (idle) {
            idle = false
            updateState()
        }
    }

    init {
        timer.isRepeats = true
        motionTimer.isRepeats = true
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener { updateState() })
        connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { updateState() })
        connection.subscribe(FeatureGate.TOPIC, FeatureGate.Listener { updateState() })

        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addPropertyChangeListener(ACTIVE_WINDOW, focusListener)
        Disposer.register(this) { focusManager.removePropertyChangeListener(ACTIVE_WINDOW, focusListener) }

        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(inputListener, INPUT_EVENTS)
        Disposer.register(this) { toolkit.removeAWTEventListener(inputListener) }
    }

    /**
     * Calls [listener] on every frame until [parentDisposable] is disposed.
     * The timer runs for it only while [feature] is active; `null` means always (the settings preview).
     */
    fun subscribe(parentDisposable: Disposable, feature: JingleFeature?, listener: JingleFrameListener) =
        add(subscribers, Subscriber(feature, listener), parentDisposable)

    /**
     * Calls [listener] about every [intervalMs] with the seconds passed since the last call,
     * until [parentDisposable] is disposed. The timer runs for it only while [feature] is active;
     * [intervalMs] is asked again whenever the settings change.
     */
    fun subscribeMotion(
        parentDisposable: Disposable,
        feature: JingleFeature,
        intervalMs: () -> Int = { MOTION_MS },
        listener: JingleMotionListener,
    ) = add(motionSubscribers, Subscriber(feature, listener, intervalMs), parentDisposable)

    private fun <L> add(list: MutableList<Subscriber<L>>, subscriber: Subscriber<L>, parentDisposable: Disposable) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        list += subscriber
        Disposer.register(parentDisposable) {
            list -= subscriber
            updateState()
        }
        updateState()
    }

    /** Goes into slow motion once nobody has touched the IDE for [IDLE_MS]; input brings it back. */
    private fun checkIdle() {
        if (!idle && System.nanoTime() - lastInput > IDLE_MS * 1_000_000L) {
            idle = true
            updateState()
        }
    }

    private fun tick() {
        checkIdle()
        tick++
        current = JingleFrame((tick % FRAMES).toInt(), tick)
        for (subscriber in subscribers.toList()) {
            subscriber.listener.onFrame(current)
        }
    }

    private fun move() {
        checkIdle()
        val now = System.nanoTime()
        // Like the mockup, never jump much further than a step: a late timer slows the snow down instead.
        val maxStep = maxOf(MAX_MOTION_STEP, 2 * motionTimer.delay / 1000.0)
        val dt = ((now - lastMotion) / 1e9).coerceIn(0.0, maxStep) / slowdown
        lastMotion = now
        for (subscriber in motionSubscribers.toList()) {
            subscriber.listener.onMotion(dt)
        }
    }

    private fun updateState() {
        val canRun = canRun()
        val gate = FeatureGate.getInstance()
        fun <L> List<Subscriber<L>>.wanted() = if (canRun) filter { it.feature == null || gate.isActive(it.feature) } else emptyList()
        slowdown = if (idle || !isProjectWindowActive()) SLOWDOWN else 1

        if (subscribers.wanted().isNotEmpty()) {
            timer.delay = FRAME_MS * slowdown
            if (!timer.isRunning) timer.start()
        }
        else {
            timer.stop()
        }
        val moving = motionSubscribers.wanted()
        if (moving.isNotEmpty()) {
            motionTimer.delay = moving.minOf { it.intervalMs() }.coerceAtLeast(MIN_MOTION_MS) * slowdown
            if (!motionTimer.isRunning) {
                lastMotion = System.nanoTime()
                motionTimer.start()
            }
        }
        else {
            motionTimer.stop()
        }
    }

    private fun canRun(): Boolean {
        if (project.isDisposed || !JingleCodeSettings.getInstance().state.animationEnabled) return false
        if (PowerSaveMode.isEnabled() || UISettings.getInstance().presentationMode) return false
        // A minimized window shows nothing: no need to move what nobody sees.
        val frame = WindowManager.getInstance().getFrame(project) ?: return false
        return frame.isShowing && frame.extendedState and Frame.ICONIFIED == 0
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
        motionTimer.stop()
        subscribers.clear()
        motionSubscribers.clear()
    }

    companion object {
        /** Frames in a loop, as in the mockup (`FRAMES`). */
        const val FRAMES = 6

        /** Frame length, as in the mockup (`FRAME_MS`). */
        const val FRAME_MS = 140

        /** Smooth motion, about 30 times a second, unless the subscriber asks otherwise. */
        const val MOTION_MS = 33
        private const val MIN_MOTION_MS = 16

        private const val MAX_MOTION_STEP = 0.05

        private const val ACTIVE_WINDOW = "activeWindow"

        /** How many times slower everything goes outside the active window or while nobody is at the IDE. */
        const val SLOWDOWN = 3

        /** Without keyboard or mouse input for this long, milliseconds, the animation goes into slow motion. */
        const val IDLE_MS = 5 * 60_000L

        private const val INPUT_EVENTS = AWTEvent.KEY_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or
            AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK

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

fun interface JingleMotionListener {
    /** [dt] is the seconds since the last call, at most 50 ms. */
    fun onMotion(dt: Double)
}
