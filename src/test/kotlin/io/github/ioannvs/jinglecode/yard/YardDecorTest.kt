package io.github.ioannvs.jinglecode.yard

import io.github.ioannvs.jinglecode.settings.JingleCodeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class YardDecorTest {

    @Test
    fun `the button goes round from snow and the yard to the yard, nothing, snow and back`() {
        val order = generateSequence(YardDecor.BOTH) { it.next }.take(5).toList()
        assertEquals(listOf(YardDecor.BOTH, YardDecor.YARD, YardDecor.OFF, YardDecor.SNOW, YardDecor.BOTH), order)
    }

    @Test
    fun `by default it snows over the yard`() {
        val state = JingleCodeSettings.State()
        assertEquals(YardDecor.BOTH, YardDecor.of(state.rightSnowEnabled, state.santaYardEnabled))
    }
}
