package io.github.ioannvs.jinglecode.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.plus
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import io.github.ioannvs.jinglecode.JingleCodeBundle.message
import io.github.ioannvs.jinglecode.animation.JingleAnimator
import io.github.ioannvs.jinglecode.core.SeasonMode
import io.github.ioannvs.jinglecode.garland.GarlandMode
import io.github.ioannvs.jinglecode.garland.GarlandSize
import io.github.ioannvs.jinglecode.snow.SnowAmount
import io.github.ioannvs.jinglecode.snow.SnowFrameRate
import io.github.ioannvs.jinglecode.snow.SnowSpeed
import io.github.ioannvs.jinglecode.village.ResidentFrequency
import io.github.ioannvs.jinglecode.village.VillageStyle
import kotlin.reflect.KMutableProperty0

/**
 * Settings → Appearance → Jingle Code. Kept short: choices are drop-down lists,
 * and the explanations sit behind the "?" next to each switch.
 */
class JingleCodeConfigurable : BoundSearchableConfigurable(
    message("settings.display.name"),
    "io.github.ioannvs.jinglecode.settings",
) {
    private val settings = JingleCodeSettings.getInstance()
    private var uiDisposable: Disposable? = null

    override fun createPanel() = panel {
        val state = settings.state
        row {
            panel {
                row {
                    checkBox(message("settings.enabled")).bindSelected(state::enabled)
                }
                row(message("settings.season")) {
                    choice(SeasonMode.entries, state::seasonMode) { it.label() }
                }
                row {
                    checkBox(message("settings.animation")).bindSelected(state::animationEnabled)
                    contextHelp(message("settings.animation.comment"))
                }
                row {
                    checkBox(message("settings.fireplace.enabled")).bindSelected(state::fireplaceEnabled)
                    contextHelp(message("settings.fireplace.comment"))
                }
            }.align(AlignY.TOP)
            cell(createPreview()).align(AlignX.RIGHT + AlignY.TOP)
        }
        group(message("settings.garland.group")) {
            lateinit var garland: Cell<JBCheckBox>
            row {
                garland = checkBox(message("settings.garland.enabled")).bindSelected(state::garlandEnabled)
                contextHelp(message("settings.garland.comment"))
            }
            indent {
                row {
                    label(message("settings.garland.mode"))
                    choice(GarlandMode.entries, state::garlandMode) { it.label() }
                    label(message("settings.garland.size"))
                    choice(GarlandSize.entries, state::garlandSize) { it.label() }
                }
            }.enabledIf(garland.component.selected)
            row {
                checkBox(message("settings.icicles.enabled")).bindSelected(state::iciclesEnabled)
                contextHelp(message("settings.icicles.comment"))
            }
            row {
                checkBox(message("settings.tabicicles.enabled")).bindSelected(state::tabIciclesEnabled)
                contextHelp(message("settings.tabicicles.comment"))
            }
            lateinit var reactions: Cell<JBCheckBox>
            row {
                reactions = checkBox(message("settings.run.reactions.enabled")).bindSelected(state::runReactionsEnabled)
                contextHelp(message("settings.run.reactions.comment"))
            }
            indent {
                row {
                    checkBox(message("settings.fireworks.enabled")).bindSelected(state::fireworksEnabled)
                    contextHelp(message("settings.fireworks.comment"))
                }
            }.enabledIf(reactions.component.selected)
        }
        group(message("settings.editor.group")) {
            lateinit var snow: Cell<JBCheckBox>
            row {
                snow = checkBox(message("settings.snow.enabled")).bindSelected(state::editorSnowEnabled)
                contextHelp(message("settings.snow.comment"))
            }
            indent {
                row {
                    label(message("settings.snow.amount"))
                    choice(SnowAmount.entries, state::snowAmount) { it.label() }
                    label(message("settings.snow.speed"))
                    choice(SnowSpeed.entries, state::snowSpeed) { it.label() }
                    label(message("settings.snow.frame.rate"))
                    choice(SnowFrameRate.entries, state::snowFrameRate) { it.label() }
                }
            }.enabledIf(snow.component.selected)
            lateinit var village: Cell<JBCheckBox>
            row {
                village = checkBox(message("settings.village.enabled")).bindSelected(state::villageEnabled)
                contextHelp(message("settings.village.comment"))
            }
            indent {
                row(message("settings.village.style")) {
                    choice(VillageStyle.entries, state::villageStyle) { it.label() }
                }
                row {
                    val residents = checkBox(message("settings.residents.enabled")).bindSelected(state::residentsEnabled)
                    contextHelp(message("settings.residents.comment"))
                    val residentsOn = residents.component.selected
                    label(message("settings.residents.frequency")).enabledIf(residentsOn)
                    choice(ResidentFrequency.entries, state::residentFrequency) { it.label() }.enabledIf(residentsOn)
                }
            }.enabledIf(village.component.selected)
        }
        group(message("settings.drift.group")) {
            row {
                checkBox(message("settings.drift.enabled")).bindSelected(state::driftEnabled)
                contextHelp(message("settings.drift.comment"))
            }
            row {
                checkBox(message("settings.panel.snow.enabled")).bindSelected(state::panelSnowEnabled)
                contextHelp(message("settings.panel.snow.comment"))
            }
        }
        group(message("settings.popups.group")) {
            row {
                checkBox(message("settings.popups.enabled")).bindSelected(state::popupSnowEnabled)
                contextHelp(message("settings.popups.comment"))
            }
            row {
                checkBox(message("settings.menus.enabled")).bindSelected(state::menuSnowEnabled)
                contextHelp(message("settings.menus.comment"))
            }
        }
    }

    /** A drop-down list rather than a column of radio buttons, so the page stays short. */
    private fun <T : Any> Row.choice(items: List<T>, property: KMutableProperty0<T>, text: (T) -> String): Cell<ComboBox<T>> =
        comboBox(items, textListCellRenderer<T?> { it?.let(text) }).bindItem(property.toNullableProperty())

    private fun createPreview(): PalettePreview {
        val disposable = Disposer.newDisposable("Jingle Code settings preview")
        uiDisposable = disposable
        val preview = PalettePreview()
        // Settings may be opened from the Welcome screen: without a project the preview stays static.
        val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
        if (project != null && !project.isDisposed) {
            JingleAnimator.getInstance(project).subscribe(disposable, feature = null) { frame ->
                preview.frame = frame.index
                preview.repaint()
            }
        }
        return preview
    }

    override fun apply() {
        super.apply()
        settings.fireChanged()
    }

    override fun disposeUIResources() {
        uiDisposable?.let(Disposer::dispose)
        uiDisposable = null
        super.disposeUIResources()
    }
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

private fun SnowFrameRate.label(): String = when (this) {
    SnowFrameRate.LOW -> message("settings.snow.frame.rate.low", fps)
    SnowFrameRate.MEDIUM -> message("settings.snow.frame.rate.medium", fps)
    SnowFrameRate.HIGH -> message("settings.snow.frame.rate.high", fps)
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

private fun GarlandSize.label(): String = when (this) {
    GarlandSize.SMALL -> message("settings.garland.size.small")
    GarlandSize.NORMAL -> message("settings.garland.size.normal")
    GarlandSize.LARGE -> message("settings.garland.size.large")
}
