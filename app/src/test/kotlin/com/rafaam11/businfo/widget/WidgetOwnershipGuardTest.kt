package com.rafaam11.businfo.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetOwnershipGuardTest {
    @Test fun `ownership lost after display prevents click persistence and success`() {
        val ownership = ArrayDeque(listOf(true, false))
        val guard = WidgetOwnershipGuard(42) { ownership.removeFirst() }
        var preferenceWritten = false
        var widgetUpdated = false

        assertTrue(guard.isOwned())

        val accepted = guard.runIfOwned {
            preferenceWritten = true
            widgetUpdated = true
        }

        assertFalse(accepted)
        assertFalse(preferenceWritten)
        assertFalse(widgetUpdated)
    }
}
