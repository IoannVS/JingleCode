package io.github.ioannvs.jinglecode.core

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/** A decoration with its own switch in the settings. */
enum class JingleFeature(private val switch: (JingleCodeSettings.State) -> Boolean) {
    GARLAND({ it.garlandEnabled }),
    ICICLES({ it.iciclesEnabled }),
    TAB_ICICLES({ it.tabIciclesEnabled }),
    DRIFT({ it.driftEnabled }),
    VILLAGE({ it.villageEnabled }),
    EDITOR_SNOW({ it.editorSnowEnabled }),
    PANEL_SNOW({ it.panelSnowEnabled }),
    POPUPS({ it.popupSnowEnabled }),
    CONTEXT_MENUS({ it.menuSnowEnabled });

    fun isEnabledIn(state: JingleCodeSettings.State): Boolean = switch(state)
}
