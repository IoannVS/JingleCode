package io.github.ioannvs.jinglecode.snow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditorDecorTest {

    @Test
    fun `the button goes round nothing, snow only, both, village only`() {
        assertEquals(EditorDecor.SNOW, EditorDecor.OFF.next)
        assertEquals(EditorDecor.BOTH, EditorDecor.SNOW.next)
        assertEquals(EditorDecor.VILLAGE, EditorDecor.BOTH.next)
        assertEquals(EditorDecor.OFF, EditorDecor.VILLAGE.next)
        assertFalse("off is off", EditorDecor.OFF.snow || EditorDecor.OFF.village)
    }

    @Test
    fun `each combination of the two switches is one mode`() {
        assertEquals(EditorDecor.BOTH, EditorDecor.of(snow = true, village = true))
        assertEquals(EditorDecor.SNOW, EditorDecor.of(snow = true, village = false))
        assertEquals(EditorDecor.VILLAGE, EditorDecor.of(snow = false, village = true))
        assertEquals(EditorDecor.OFF, EditorDecor.of(snow = false, village = false))
        for (mode in EditorDecor.entries) assertEquals(mode, EditorDecor.of(mode.snow, mode.village))
    }
}
