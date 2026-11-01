package io.github.ioannvs.jinglecode.railway

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class RailwayDecorTest {

    @Test
    fun `the button goes round from snow to snow and the railway, the railway, nothing and back`() {
        val order = generateSequence(RailwayDecor.SNOW) { it.next }.take(5).toList()
        assertEquals(listOf(RailwayDecor.SNOW, RailwayDecor.BOTH, RailwayDecor.RAILWAY, RailwayDecor.OFF, RailwayDecor.SNOW), order)
    }

    @Test
    fun `by default it snows over the railway`() {
        val state = JingleCodeSettings.State()
        assertEquals(RailwayDecor.BOTH, RailwayDecor.of(state.bottomSnowEnabled, state.railwayEnabled))
    }
}
