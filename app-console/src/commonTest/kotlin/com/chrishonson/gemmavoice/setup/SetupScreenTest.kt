package com.chrishonson.gemmavoice.setup

import kotlin.test.Test
import kotlin.test.assertEquals

class SetupScreenTest {

    @Test
    fun microphoneStatusTextReturnsAllowedWhenNotDenied() {
        assertEquals("Allowed", microphoneStatusText(micDenied = false))
    }

    @Test
    fun microphoneStatusTextReturnsBlockedWhenDenied() {
        assertEquals("Blocked", microphoneStatusText(micDenied = true))
    }

    @Test
    fun wakeWordSubtextFormatsWithAssistantName() {
        assertEquals("Only answer after \"Computer\"", wakeWordSubtext("Computer"))
    }

    @Test
    fun wakeWordSubtextFallsBackWhenNameIsBlank() {
        assertEquals("Only answer after \"Assistant\"", wakeWordSubtext(""))
        assertEquals("Only answer after \"Assistant\"", wakeWordSubtext("   "))
    }
}
