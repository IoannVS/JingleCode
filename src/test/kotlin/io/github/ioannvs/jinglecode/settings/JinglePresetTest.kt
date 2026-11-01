package io.github.ioannvs.jinglecode.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class JinglePresetTest {

    @Test
    fun `each mode is read back from its own switches`() {
        for (mode in JinglePreset.MODES) {
            assertEquals(mode, JinglePreset.detect { mode.value(it)!! })
        }
    }

    @Test
    fun `default settings are the maximum mode`() {
        val state = JingleCodeSettings.State()
        assertEquals(JinglePreset.MAXIMUM, JinglePreset.detect { it.property(state).get() })
    }

    @Test
    fun `any switch changed by hand makes the mode custom`() {
        for (mode in JinglePreset.MODES) {
            for (changed in PresetSwitch.entries) {
                val preset = JinglePreset.detect { if (it == changed) !mode.value(it)!! else mode.value(it)!! }
                assertEquals("$mode with $changed flipped", JinglePreset.CUSTOM, preset)
            }
        }
    }

    @Test
    fun `minimal mode has no animation and no scenes, normal has no life scenes`() {
        val scenes = listOf(PresetSwitch.VILLAGE, PresetSwitch.DRIFT, PresetSwitch.RAILWAY, PresetSwitch.YARD)
        assertEquals(false, JinglePreset.MINIMAL.value(PresetSwitch.ANIMATION))
        scenes.forEach { assertEquals(false, JinglePreset.MINIMAL.value(it)) }
        scenes.forEach { assertEquals(true, JinglePreset.NORMAL.value(it)) }
        assertEquals(false, JinglePreset.NORMAL.value(PresetSwitch.RESIDENTS))
        assertEquals(false, JinglePreset.NORMAL.value(PresetSwitch.RAILWAY_RESIDENTS))
    }
}
