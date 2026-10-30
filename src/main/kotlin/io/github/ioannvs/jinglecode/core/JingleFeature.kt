package io.github.ioannvs.jinglecode.core

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/** A decoration with its own switch in the settings. */
enum class JingleFeature(private val switch: (JingleCodeSettings.State) -> Boolean) {
    GARLAND({ it.garlandEnabled });

    fun isEnabledIn(state: JingleCodeSettings.State): Boolean = switch(state)
}
