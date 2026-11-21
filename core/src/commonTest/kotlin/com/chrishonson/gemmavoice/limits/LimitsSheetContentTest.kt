package com.chrishonson.gemmavoice.limits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LimitsSheetContentTest {

    @Test
    fun emptyConfigProducesNoneYetAndExactLastLine() {
        val lines = limitsSheetLines(safeguards = emptyList())

        assertEquals("Limits", lines.first())
        assertEquals("Never guaranteed. Verify with a professional.", lines.last())
        assertTrue("None yet." in lines)
        assertEquals(6, lines.size)
        assertEquals(
            listOf(
                "Limits",
                "A small AI model running on this phone, with no internet.",
                "It can mishear what you say and state things that are not true.",
                "Accuracy measures in this build",
                "None yet.",
                "Never guaranteed. Verify with a professional."
            ),
            lines
        )
    }

    @Test
    fun withSafeguardsExcludesNoneYet() {
        val safeguards = listOf(
            Safeguard(id = "T3", description = "Deterministic value check against cited source"),
            Safeguard(id = "T6", description = "Verbatim quote verification against OCR")
        )
        val lines = limitsSheetLines(safeguards)

        assertEquals("Limits", lines.first())
        assertEquals("Never guaranteed. Verify with a professional.", lines.last())
        assertFalse("None yet." in lines)
        assertTrue("Deterministic value check against cited source" in lines)
        assertTrue("Verbatim quote verification against OCR" in lines)
    }
}
