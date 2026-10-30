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
import io.github.ioannvs.jinglecode.garland.GarlandMode
import io.github.ioannvs.jinglecode.garland.GarlandSize

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
            JingleAnimator.getInstance(project).subscribe(disposable) { frame ->
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
private fun GarlandMode.label(): String = when (this) {
    GarlandMode.TWINKLE -> message("settings.garland.mode.twinkle")
    GarlandMode.CHASE -> message("settings.garland.mode.chase")
    GarlandMode.FLOW -> message("settings.garland.mode.flow")
    GarlandMode.WARM_WHITE -> message("settings.garland.mode.warm_white")
}

private fun GarlandSize.label(): String = when (this) {
    GarlandSize.SMALL -> message("settings.garland.size.small")
    GarlandSize.NORMAL -> message("settings.garland.size.normal")
    GarlandSize.LARGE -> message("settings.garland.size.large")
}
