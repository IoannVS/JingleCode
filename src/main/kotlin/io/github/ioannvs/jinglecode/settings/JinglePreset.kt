package io.github.ioannvs.jinglecode.settings

import kotlin.reflect.KMutableProperty0

/**
 * The plugin-wide modes on top of the settings page. A mode is not stored: it is read back from
 * the switches, so changing any of them by hand turns the page to [CUSTOM].
 */
enum class JinglePreset {
    /** Still decorations only: no animation and no scenes. */
    MINIMAL,

    /** Animation and every scene, but no little life scenes. */
    NORMAL,

    /** Everything on. */
    MAXIMUM,

    /** The switches match no mode. Choosing it changes nothing. */
    CUSTOM;

    /** What this mode sets [switch] to; null for [CUSTOM]. */
    fun value(switch: PresetSwitch): Boolean? = when (this) {
        MINIMAL -> switch.minimal
        NORMAL -> switch.normal
        MAXIMUM -> true
        CUSTOM -> null
    }

    companion object {
        val MODES = listOf(MINIMAL, NORMAL, MAXIMUM)

        fun detect(isOn: (PresetSwitch) -> Boolean): JinglePreset =
            MODES.firstOrNull { mode -> PresetSwitch.entries.all { isOn(it) == mode.value(it) } } ?: CUSTOM
    }
}

/** The on/off settings a mode sets. Sizes, styles and frequencies stay as they are. */
enum class PresetSwitch(
    val property: (JingleCodeSettings.State) -> KMutableProperty0<Boolean>,
    val minimal: Boolean,
    val normal: Boolean,
) {
    ANIMATION({ it::animationEnabled }, minimal = false, normal = true),
    FIREPLACE({ it::fireplaceEnabled }, minimal = true, normal = true),
    EDITOR_SNOW({ it::editorSnowEnabled }, minimal = false, normal = true),
    VILLAGE({ it::villageEnabled }, minimal = false, normal = true),
    RESIDENTS({ it::residentsEnabled }, minimal = false, normal = false),
    PANEL_SNOW({ it::panelSnowEnabled }, minimal = false, normal = true),
    DRIFT({ it::driftEnabled }, minimal = false, normal = true),
    BOTTOM_SNOW({ it::bottomSnowEnabled }, minimal = false, normal = true),
    RAILWAY({ it::railwayEnabled }, minimal = false, normal = true),
    RAILWAY_RESIDENTS({ it::railwayResidents }, minimal = false, normal = false),
    RAILWAY_POLES({ it::railwayPoles }, minimal = false, normal = true),
    RIGHT_SNOW({ it::rightSnowEnabled }, minimal = false, normal = true),
    YARD({ it::santaYardEnabled }, minimal = false, normal = true),
    YARD_REACTIONS({ it::santaYardReactions }, minimal = false, normal = true),
    YARD_LETTERS({ it::santaYardLetters }, minimal = false, normal = true),
    RAILWAY_BUTTON({ it::railwayButtonEnabled }, minimal = false, normal = true),
    YARD_BUTTON({ it::yardButtonEnabled }, minimal = false, normal = true),
    GARLAND({ it::garlandEnabled }, minimal = true, normal = true),
    ICICLES({ it::iciclesEnabled }, minimal = true, normal = true),
    TAB_ICICLES({ it::tabIciclesEnabled }, minimal = false, normal = true),
    POPUP_SNOW({ it::popupSnowEnabled }, minimal = false, normal = true),
    MENU_SNOW({ it::menuSnowEnabled }, minimal = false, normal = true),
    RUN_REACTIONS({ it::runReactionsEnabled }, minimal = false, normal = true),
    FIREWORKS({ it::fireworksEnabled }, minimal = false, normal = true),
    EASTER_EGGS({ it::easterEggsEnabled }, minimal = false, normal = true),
}
