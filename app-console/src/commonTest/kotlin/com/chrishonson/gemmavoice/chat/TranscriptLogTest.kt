package com.chrishonson.gemmavoice.chat

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptLogTest {

    @Test
    fun transcriptTagReturnsExpectedLabelPerRole() {
        assertEquals("YOU", transcriptTag(Role.USER))
        assertEquals("COMPUTER", transcriptTag(Role.ASSISTANT))
        assertNull(transcriptTag(Role.OVERHEARD))
    }

    @Test
    fun transcriptTestTagReturnsExpectedTagPerRole() {
        assertEquals("logEntry-user", transcriptTestTag(Role.USER))
        assertEquals("logEntry-assistant", transcriptTestTag(Role.ASSISTANT))
        assertEquals("logEntry-overheard", transcriptTestTag(Role.OVERHEARD))
    }

    @Test
    fun transcriptBarColorAssignsSecondaryToUserAndTertiaryToAssistant() {
        val userColor = Color.Blue
        val assistantColor = Color.Yellow

        assertEquals(userColor, transcriptBarColor(Role.USER, userColor, assistantColor))
        assertEquals(assistantColor, transcriptBarColor(Role.ASSISTANT, userColor, assistantColor))
        assertNull(transcriptBarColor(Role.OVERHEARD, userColor, assistantColor))
    }

    @Test
    fun transcriptDisplayTextAppendsCursorOnlyWhenStreaming() {
        assertEquals("Hello world", transcriptDisplayText("Hello world", isStreaming = false))
        assertEquals("Hello world▍", transcriptDisplayText("Hello world", isStreaming = true))
        assertEquals("▍", transcriptDisplayText("", isStreaming = true))
    }
}
