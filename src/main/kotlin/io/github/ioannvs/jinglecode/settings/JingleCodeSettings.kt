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
import io.github.ioannvs.jinglecode.railway.TrainFrequency
import io.github.ioannvs.jinglecode.snow.SnowAmount
import io.github.ioannvs.jinglecode.snow.SnowFrameRate
import io.github.ioannvs.jinglecode.snow.SnowSpeed
import io.github.ioannvs.jinglecode.village.ResidentFrequency
import io.github.ioannvs.jinglecode.village.VillageStyle

@Service(Service.Level.APP)
@State(name = "JingleCodeSettings", storages = [Storage("jingle-code.xml")], category = SettingsCategory.UI)
class JingleCodeSettings : SimplePersistentStateComponent<JingleCodeSettings.State>(State()) {

    class State : BaseState() {
        var enabled by property(true)
        var seasonMode by enum(SeasonMode.AUTO)
        var animationEnabled by property(true)
        var fireplaceEnabled by property(true)
        var garlandEnabled by property(true)
        var garlandMode by enum(GarlandMode.TWINKLE)
        var garlandSize by enum(GarlandSize.NORMAL)
        var iciclesEnabled by property(true)
        var tabIciclesEnabled by property(true)
        var runReactionsEnabled by property(true)
        var fireworksEnabled by property(true)
        var driftEnabled by property(true)
        var panelSnowEnabled by property(true)
        var villageEnabled by property(true)
        var villageStyle by enum(VillageStyle.HILL_FARM)
        var residentsEnabled by property(true)
        var residentFrequency by enum(ResidentFrequency.NORMAL)
        var editorSnowEnabled by property(true)
        var snowAmount by enum(SnowAmount.NORMAL)
        var snowSpeed by enum(SnowSpeed.NORMAL)
        var snowFrameRate by enum(SnowFrameRate.HIGH)
        var popupSnowEnabled by property(true)
        var menuSnowEnabled by property(true)
        var railwayEnabled by property(true)
        var trainFrequency by enum(TrainFrequency.NORMAL)
        var railwayResidents by property(true)
        var railwayPoles by property(true)
        var santaYardEnabled by property(true)
        var santaYardReactions by property(true)
        var santaYardLetters by property(true)
        var rightSnowEnabled by property(true)
        var yardButtonEnabled by property(true)
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
