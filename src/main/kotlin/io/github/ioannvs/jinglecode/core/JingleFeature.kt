package io.github.ioannvs.jinglecode.core

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings

/** A decoration with its own switch in the settings. */
enum class JingleFeature(private val switch: (JingleCodeSettings.State) -> Boolean) {
    FIREPLACE({ it.fireplaceEnabled }),
    GARLAND({ it.garlandEnabled }),
    ICICLES({ it.iciclesEnabled }),
    TAB_ICICLES({ it.tabIciclesEnabled }),

    /** The garland and the icicles react to runs and builds: red lights and falling icicles, a golden wave. */
    RUN_REACTIONS({ it.runReactionsEnabled }),

    /** Fireworks in the main toolbar after a successful run; finds the toolbar's gaps from its Swing layout. */
    FIREWORKS({ it.fireworksEnabled }),

    DRIFT({ it.driftEnabled }),
    VILLAGE({ it.villageEnabled }),
    EDITOR_SNOW({ it.editorSnowEnabled }),
    PANEL_SNOW({ it.panelSnowEnabled }),
    POPUPS({ it.popupSnowEnabled }),
    CONTEXT_MENUS({ it.menuSnowEnabled }),

    /** The railway at the bottom of the bottom tool windows; reserves room there with a border on their content. */
    RAILWAY({ it.railwayEnabled });

    fun isEnabledIn(state: JingleCodeSettings.State): Boolean = switch(state)
}
