package io.github.ioannvs.jinglecode.snow

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PanelDecorTest {

    @Test
    fun `the button goes round nothing, snow only, both, the scene only`() {
        assertEquals(PanelDecor.SNOW, PanelDecor.OFF.next)
        assertEquals(PanelDecor.BOTH, PanelDecor.SNOW.next)
        assertEquals(PanelDecor.SCENE, PanelDecor.BOTH.next)
        assertEquals(PanelDecor.OFF, PanelDecor.SCENE.next)
        assertFalse("off is off", PanelDecor.OFF.snow || PanelDecor.OFF.scene)
    }

    @Test
    fun `each combination of the two switches is one mode`() {
        for (mode in PanelDecor.entries) assertEquals(mode, PanelDecor.of(mode.snow, mode.scene))
    }

    @Test
    fun `snow and the scene by default`() {
        val state = JingleCodeSettings.State()
        assertEquals(PanelDecor.BOTH, PanelDecor.of(state.panelSnowEnabled, state.driftEnabled))
    }
}
