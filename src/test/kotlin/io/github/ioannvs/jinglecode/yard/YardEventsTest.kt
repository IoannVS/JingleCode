package io.github.ioannvs.jinglecode.yard

import io.github.ioannvs.jinglecode.events.JingleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YardEventsTest {

    @Test
    fun `a task goes on while any run or build does, stopped ones included`() {
        val events = YardEvents()
        events.onEvent(JingleEvent.RunStarted)
        events.onEvent(JingleEvent.RunStarted)
        events.onEvent(JingleEvent.RunFinished(succeeded = true))
        assertTrue(events.running)
        events.onEvent(JingleEvent.RunStopped)
        assertFalse(events.running)
        // An end without a start (it began before the project opened) doesn't go below none.
        events.onEvent(JingleEvent.RunFinished(succeeded = false))
        events.onEvent(JingleEvent.RunStarted)
        assertTrue(events.running)
    }

    @Test
    fun `the yards get the events in order, Git ones left out`() {
        val events = YardEvents()
        events.onEvent(JingleEvent.Notification)
        events.onEvent(JingleEvent.Commit)
        events.onEvent(JingleEvent.RunFinished(succeeded = false))
        assertEquals(listOf(JingleEvent.Notification, JingleEvent.RunFinished(succeeded = false)), events.drain().map { it.event })
        assertEquals(emptyList<Any>(), events.drain())
    }
}
