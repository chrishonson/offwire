package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssistantNameTest {
    @Test
    fun capitalizesASingleWord() {
        assertEquals("Computer", validateAssistantName("computer"))
    }

    @Test
    fun trimsCollapsesWhitespaceAndCapitalizesEachWord() {
        assertEquals("Hey Jarvis", validateAssistantName("  hey   jarvis "))
    }

    @Test
    fun allowsHyphenAndApostropheInsideAWord() {
        assertEquals("Mary-Jo", validateAssistantName("Mary-Jo"))
        assertEquals("O'neil", validateAssistantName("o'neil"))
    }

    @Test
    fun keepsTheRestOfEachWordAsTyped() {
        assertEquals("McKenzie", validateAssistantName("mcKenzie"))
    }

    @Test
    fun rejectsInvalidNames() {
        listOf(
            "",
            "   ",
            "a",
            "R2D2",
            "three word name",
            "a".repeat(21),
            "-jo",
            "jo-",
            "mary--jo",
            "hi!"
        ).forEach { assertNull(validateAssistantName(it), "expected null for \"$it\"") }
    }

    @Test
    fun acceptsTheLengthBounds() {
        assertEquals("Jo", validateAssistantName("jo"))
        assertEquals("A" + "a".repeat(19), validateAssistantName("a".repeat(20)))
    }
}
