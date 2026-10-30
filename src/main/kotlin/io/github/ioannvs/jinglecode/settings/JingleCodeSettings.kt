package io.github.ioannvs.jinglecode.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import io.github.ioannvs.jinglecode.core.SeasonMode
import io.github.ioannvs.jinglecode.garland.GarlandMode
import io.github.ioannvs.jinglecode.garland.GarlandSize
import io.github.ioannvs.jinglecode.snow.SnowAmount
import io.github.ioannvs.jinglecode.snow.SnowFrameRate
import io.github.ioannvs.jinglecode.snow.SnowSpeed

@Service(Service.Level.APP)
@State(name = "JingleCodeSettings", storages = [Storage("jingle-code.xml")], category = SettingsCategory.UI)
class JingleCodeSettings : SimplePersistentStateComponent<JingleCodeSettings.State>(State()) {

    class State : BaseState() {
        var enabled by property(true)
        var seasonMode by enum(SeasonMode.AUTO)
        var animationEnabled by property(true)
        var garlandEnabled by property(true)
        var garlandMode by enum(GarlandMode.TWINKLE)
        var garlandSize by enum(GarlandSize.NORMAL)
        var editorSnowEnabled by property(true)
        var snowAmount by enum(SnowAmount.NORMAL)
        var snowSpeed by enum(SnowSpeed.NORMAL)
        var snowFrameRate by enum(SnowFrameRate.HIGH)
    }

    /** Call after changing [state] so that decorations pick up the change. */
    fun fireChanged() {
        ApplicationManager.getApplication().messageBus
            .syncPublisher(JingleCodeSettingsListener.TOPIC)
            .settingsChanged(this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): JingleCodeSettings = service()
    }
}
