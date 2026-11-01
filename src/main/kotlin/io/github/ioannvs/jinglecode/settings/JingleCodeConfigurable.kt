package io.github.ioannvs.jinglecode.settings

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.and
import com.intellij.ui.layout.selected
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.core.SeasonMode
import io.github.ioannvs.jinglecode.garland.GarlandMode
import io.github.ioannvs.jinglecode.garland.GarlandSize
import io.github.ioannvs.jinglecode.railway.TrainFrequency
import io.github.ioannvs.jinglecode.snow.SnowAmount
import io.github.ioannvs.jinglecode.snow.SnowSpeed
import io.github.ioannvs.jinglecode.village.ResidentFrequency
import io.github.ioannvs.jinglecode.village.VillageStyle
import java.awt.event.ItemEvent
import kotlin.reflect.KMutableProperty0

/**
 * Settings → Appearance → Jingle Code. Kept short: one row per place, choices are drop-down lists,
 * and the explanations sit behind the "?" next to each switch.
 */
class JingleCodeConfigurable : BoundSearchableConfigurable(
    message("settings.display.name"),
    "io.github.ioannvs.jinglecode.settings",
) {
    private val settings = JingleCodeSettings.getInstance()

    // The mode list and the switches it sets; [syncing] keeps them from echoing each other.
    private val switches = mutableMapOf<PresetSwitch, JBCheckBox>()
    private var presetBox: ComboBox<JinglePreset>? = null
    private var syncing = false

    override fun createPanel() = panel {
        val state = settings.state
        switches.clear()
        row {
            checkBox(message("settings.enabled")).bindSelected(state::enabled)
            label(message("settings.season"))
            choice(SeasonMode.entries, state::seasonMode) { it.label() }
        }
        row(message("settings.preset")) {
            val box = comboBox(JinglePreset.entries, textListCellRenderer { it?.label() })
            box.component.addItemListener { if (!syncing && it.stateChange == ItemEvent.SELECTED) applyPreset(box.component.item) }
            presetBox = box.component
            contextHelp(message("settings.preset.comment"))
        }
        row {
            switch(PresetSwitch.ANIMATION, message("settings.animation"))
            contextHelp(message("settings.animation.comment"))
            switch(PresetSwitch.FIREPLACE, message("settings.fireplace.enabled"))
            contextHelp(message("settings.fireplace.comment"))
        }
        // Size and snow are shared by every scene, so they are set once, here.
        row(message("settings.size")) {
            choice(GarlandSize.entries, state::garlandSize) { it.label() }
            contextHelp(message("settings.size.comment"))
        }
        row(message("settings.snow")) {
            choice(SnowAmount.entries, state::snowAmount) { it.label() }
            choice(SnowSpeed.entries, state::snowSpeed) { it.label() }
            contextHelp(message("settings.snow.comment"))
        }
        // One row per place: snow first, so the snow boxes line up in a column, then the scene and its options.
        group(message("settings.scenes.group")) {
            row(message("settings.place.editor")) {
                snow(PresetSwitch.EDITOR_SNOW)
                val village = scene(PresetSwitch.VILLAGE, message("settings.village.enabled"), message("settings.village.comment"))
                choice(VillageStyle.entries, state::villageStyle) { it.label() }.enabledIf(village)
                val residents = switch(PresetSwitch.RESIDENTS, message("settings.residents.enabled"))
                    .enabledIf(village).component.selected
                contextHelp(message("settings.residents.comment"))
                choice(ResidentFrequency.entries, state::residentFrequency) { it.label() }.enabledIf(village and residents)
            }
            row(message("settings.place.panels")) {
                snow(PresetSwitch.PANEL_SNOW)
                scene(PresetSwitch.DRIFT, message("settings.drift.enabled"), message("settings.drift.comment"))
            }
            row(message("settings.place.bottom")) {
                snow(PresetSwitch.BOTTOM_SNOW)
                val railway = scene(PresetSwitch.RAILWAY, message("settings.railway.enabled"), message("settings.railway.comment"))
                label(message("settings.railway.frequency")).enabledIf(railway)
                choice(TrainFrequency.entries, state::trainFrequency) { it.label() }.enabledIf(railway)
                switch(PresetSwitch.RAILWAY_RESIDENTS, message("settings.railway.residents")).enabledIf(railway)
                switch(PresetSwitch.RAILWAY_POLES, message("settings.railway.poles")).enabledIf(railway)
            }
            row(message("settings.place.right")) {
                snow(PresetSwitch.RIGHT_SNOW)
                val yard = scene(PresetSwitch.YARD, message("settings.yard.enabled"), message("settings.yard.comment"))
                switch(PresetSwitch.YARD_REACTIONS, message("settings.yard.reactions")).enabledIf(yard)
                contextHelp(message("settings.yard.reactions.comment"))
                switch(PresetSwitch.YARD_LETTERS, message("settings.yard.letters")).enabledIf(yard)
                contextHelp(message("settings.yard.letters.comment"))
            }
            row(message("settings.buttons")) {
                switch(PresetSwitch.RAILWAY_BUTTON, message("settings.buttons.bottom"))
                switch(PresetSwitch.YARD_BUTTON, message("settings.buttons.right"))
                contextHelp(message("settings.buttons.comment"))
            }
        }
        group(message("settings.window.group")) {
            row(message("settings.garland")) {
                val garland = switch(PresetSwitch.GARLAND, message("settings.garland.enabled"))
                contextHelp(message("settings.garland.comment"))
                choice(GarlandMode.entries, state::garlandMode) { it.label() }.enabledIf(garland.component.selected)
            }
            row(message("settings.icicles")) {
                switch(PresetSwitch.ICICLES, message("settings.icicles.enabled"))
                contextHelp(message("settings.icicles.comment"))
                switch(PresetSwitch.TAB_ICICLES, message("settings.tabicicles.enabled"))
                contextHelp(message("settings.tabicicles.comment"))
            }
            row(message("settings.caps")) {
                switch(PresetSwitch.POPUP_SNOW, message("settings.popups.enabled"))
                contextHelp(message("settings.popups.comment"))
                switch(PresetSwitch.MENU_SNOW, message("settings.menus.enabled"))
                contextHelp(message("settings.menus.comment"))
            }
            row(message("settings.runs")) {
                val reactions = switch(PresetSwitch.RUN_REACTIONS, message("settings.run.reactions.enabled"))
                contextHelp(message("settings.run.reactions.comment"))
                switch(PresetSwitch.FIREWORKS, message("settings.fireworks.enabled"))
                    .enabledIf(reactions.component.selected)
                contextHelp(message("settings.fireworks.comment"))
            }
        }
        syncPreset()
    }

    /** A check box that a mode sets; flipping it by hand re-reads the mode. */
    private fun Row.switch(switch: PresetSwitch, text: String): Cell<JBCheckBox> {
        val box = checkBox(text).bindSelected(switch.property(settings.state))
        box.component.addItemListener { if (!syncing) syncPreset() }
        switches[switch] = box.component
        return box
    }

    private fun Row.snow(switch: PresetSwitch): Cell<JBCheckBox> = switch(switch, message("settings.place.snow"))

    /** The scene switch of a place, with its explanation behind the "?"; returns whether it is on. */
    private fun Row.scene(switch: PresetSwitch, text: String, help: String) = switch(switch, text).also { contextHelp(help) }.component.selected

    private fun applyPreset(preset: JinglePreset) {
        syncing = true
        try {
            switches.forEach { (switch, box) -> preset.value(switch)?.let { box.isSelected = it } }
        } finally {
            syncing = false
        }
    }

    private fun syncPreset() {
        val box = presetBox ?: return
        syncing = true
        try {
            box.item = JinglePreset.detect { switches[it]?.isSelected ?: it.property(settings.state).get() }
        } finally {
            syncing = false
        }
    }

    /** A drop-down list rather than a column of radio buttons, so the page stays short. */
    private fun <T : Any> Row.choice(items: List<T>, property: KMutableProperty0<T>, text: (T) -> String): Cell<ComboBox<T>> =
        comboBox(items, textListCellRenderer { it?.let(text) }).bindItem(property.toNullableProperty())

    override fun reset() {
        super.reset()
        syncPreset()
    }

    override fun apply() {
        super.apply()
        settings.fireChanged()
    }
}

private fun JinglePreset.label(): String = when (this) {
    JinglePreset.MINIMAL -> message("settings.preset.minimal")
    JinglePreset.NORMAL -> message("settings.preset.normal")
    JinglePreset.MAXIMUM -> message("settings.preset.maximum")
    JinglePreset.CUSTOM -> message("settings.preset.custom")
}

// Explicit keys rather than ones built from enum names: the IDE checks them, and a renamed constant keeps its text.
private fun SeasonMode.label(): String = when (this) {
    SeasonMode.AUTO -> message("settings.season.auto")
    SeasonMode.ALWAYS -> message("settings.season.always")
    SeasonMode.OFF -> message("settings.season.off")
}

private fun GarlandMode.label(): String = when (this) {
    GarlandMode.TWINKLE -> message("settings.garland.mode.twinkle")
    GarlandMode.CHASE -> message("settings.garland.mode.chase")
    GarlandMode.FLOW -> message("settings.garland.mode.flow")
    GarlandMode.WARM_WHITE -> message("settings.garland.mode.warm_white")
}

private fun SnowAmount.label(): String = when (this) {
    SnowAmount.FEW -> message("settings.snow.amount.few")
    SnowAmount.NORMAL -> message("settings.snow.amount.normal")
    SnowAmount.MANY -> message("settings.snow.amount.many")
}

private fun SnowSpeed.label(): String = when (this) {
    SnowSpeed.SLOW -> message("settings.snow.speed.slow")
    SnowSpeed.NORMAL -> message("settings.snow.speed.normal")
    SnowSpeed.FAST -> message("settings.snow.speed.fast")
}

private fun VillageStyle.label(): String = when (this) {
    VillageStyle.HILL_FARM -> message("settings.village.style.hill_farm")
    VillageStyle.VALLEY -> message("settings.village.style.valley")
    VillageStyle.UPHILL_STREET -> message("settings.village.style.uphill_street")
    VillageStyle.FOREST_CLEARING -> message("settings.village.style.forest_clearing")
    VillageStyle.RANDOM -> message("settings.village.style.random")
}

private fun ResidentFrequency.label(): String = when (this) {
    ResidentFrequency.RARE -> message("settings.residents.frequency.rare")
    ResidentFrequency.NORMAL -> message("settings.residents.frequency.normal")
    ResidentFrequency.OFTEN -> message("settings.residents.frequency.often")
}

private fun TrainFrequency.label(): String = when (this) {
    TrainFrequency.RARE -> message("settings.railway.frequency.rare")
    TrainFrequency.NORMAL -> message("settings.railway.frequency.normal")
    TrainFrequency.OFTEN -> message("settings.railway.frequency.often")
}

private fun GarlandSize.label(): String = when (this) {
    GarlandSize.SMALL -> message("settings.size.small")
    GarlandSize.NORMAL -> message("settings.size.normal")
    GarlandSize.LARGE -> message("settings.size.large")
}
