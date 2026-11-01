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
    RAILWAY({ it.railwayEnabled }),

    /** Snow falling in the bottom tool windows, over the railway. */
    BOTTOM_SNOW({ it.bottomSnowEnabled }),

    /** The railway's button in the headers of the bottom tool windows; put there by their Swing layout. */
    RAILWAY_BUTTON({ it.railwayButtonEnabled }),

    /** Santa's yard at the bottom of the right tool windows; drawn over their last rows, like the drift. */
    SANTA_YARD({ it.santaYardEnabled }),

    /** Snow falling in the right tool windows, over Santa's yard; without it stars hang over the yard. */
    RIGHT_SNOW({ it.rightSnowEnabled }),

    /** The yard's button in the headers of the right tool windows; put there by their Swing layout. */
    YARD_BUTTON({ it.yardButtonEnabled });

    fun isEnabledIn(state: JingleCodeSettings.State): Boolean = switch(state)
}
