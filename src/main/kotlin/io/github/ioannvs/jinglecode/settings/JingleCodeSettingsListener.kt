package io.github.ioannvs.jinglecode.settings

import com.intellij.util.messages.Topic

fun interface JingleCodeSettingsListener {
    fun settingsChanged(settings: JingleCodeSettings)

    companion object {
        @JvmField
        @Topic.AppLevel
        val TOPIC: Topic<JingleCodeSettingsListener> =
            Topic(JingleCodeSettingsListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
