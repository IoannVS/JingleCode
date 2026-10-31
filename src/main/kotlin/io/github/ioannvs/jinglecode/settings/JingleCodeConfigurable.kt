package io.github.ioannvs.jinglecode.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
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

/** Settings → Appearance → Jingle Code. */
class JingleCodeConfigurable : BoundSearchableConfigurable(
    message("settings.display.name"),
    "io.github.ioannvs.jinglecode.settings",
) {
    private val settings = JingleCodeSettings.getInstance()
    private var uiDisposable: Disposable? = null

    override fun createPanel() = panel {
        val state = settings.state
        row {
            checkBox(message("settings.enabled")).bindSelected(state::enabled)
        }
        buttonsGroup(message("settings.season")) {
            for (mode in SeasonMode.entries) {
                row { radioButton(mode.label(), mode) }
            }
        }.bind(state::seasonMode)
        row {
            checkBox(message("settings.animation")).bindSelected(state::animationEnabled)
                .comment(message("settings.animation.comment"))
        }
        group(message("settings.garland.group")) {
            lateinit var garland: Cell<JBCheckBox>
            row {
                garland = checkBox(message("settings.garland.enabled")).bindSelected(state::garlandEnabled)
                    .comment(message("settings.garland.comment"))
            }
            indent {
                buttonsGroup(message("settings.garland.mode")) {
                    for (mode in GarlandMode.entries) {
                        row { radioButton(mode.label(), mode) }
                    }
                }.bind(state::garlandMode)
                buttonsGroup {
                    row(message("settings.garland.size")) {
                        for (size in GarlandSize.entries) {
                            radioButton(size.label(), size)
                        }
                    }
                }.bind(state::garlandSize)
            }.enabledIf(garland.component.selected)
        }
        group(message("settings.icicles.group")) {
            row {
                checkBox(message("settings.icicles.enabled")).bindSelected(state::iciclesEnabled)
                    .comment(message("settings.icicles.comment"))
            }
        }
        group(message("settings.tabicicles.group")) {
            row {
                checkBox(message("settings.tabicicles.enabled")).bindSelected(state::tabIciclesEnabled)
                    .comment(message("settings.tabicicles.comment"))
            }
        }
        group(message("settings.village.group")) {
            lateinit var village: Cell<JBCheckBox>
            row {
                village = checkBox(message("settings.village.enabled")).bindSelected(state::villageEnabled)
                    .comment(message("settings.village.comment"))
            }
            indent {
                buttonsGroup(message("settings.village.style")) {
                    for (style in VillageStyle.entries) {
                        row { radioButton(style.label(), style) }
                    }
                }.bind(state::villageStyle)
                lateinit var residents: Cell<JBCheckBox>
                row {
                    residents = checkBox(message("settings.residents.enabled")).bindSelected(state::residentsEnabled)
                        .comment(message("settings.residents.comment"))
                }
                indent {
                    buttonsGroup {
                        row(message("settings.residents.frequency")) {
                            for (frequency in ResidentFrequency.entries) {
                                radioButton(frequency.label(), frequency)
                            }
                        }
                    }.bind(state::residentFrequency)
                }.enabledIf(residents.component.selected)
            }.enabledIf(village.component.selected)
        }
        group(message("settings.drift.group")) {
            row {
                checkBox(message("settings.drift.enabled")).bindSelected(state::driftEnabled)
                    .comment(message("settings.drift.comment"))
            }
        }
        group(message("settings.snow.group")) {
            lateinit var snow: Cell<JBCheckBox>
            row {
                snow = checkBox(message("settings.snow.enabled")).bindSelected(state::editorSnowEnabled)
                    .comment(message("settings.snow.comment"))
            }
            indent {
                buttonsGroup {
                    row(message("settings.snow.amount")) {
                        for (amount in SnowAmount.entries) {
                            radioButton(amount.label(), amount)
                        }
                    }
                }.bind(state::snowAmount)
                buttonsGroup {
                    row(message("settings.snow.speed")) {
                        for (speed in SnowSpeed.entries) {
                            radioButton(speed.label(), speed)
                        }
                    }
                }.bind(state::snowSpeed)
                buttonsGroup(message("settings.snow.frame.rate")) {
                    for (rate in SnowFrameRate.entries) {
                        row { radioButton(rate.label(), rate) }
                    }
                }.bind(state::snowFrameRate)
            }.enabledIf(snow.component.selected)
        }
        group(message("settings.preview.group")) {
            row { cell(createPreview()) }
        }
    }

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
