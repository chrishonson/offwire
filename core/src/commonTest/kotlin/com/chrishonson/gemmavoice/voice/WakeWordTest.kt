package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WakeWordTest {
    private val wakeWords = listOf("computer")

    @Test
    fun addressedMatchesAnyVariantCaseInsensitively() {
        assertTrue(addressed("Hey Computer what time is it", wakeWords))
        assertTrue(addressed("COMPUTER", wakeWords))
        assertTrue(addressed("what do you think, computer?", wakeWords))
        assertTrue(addressed("thanks computer", wakeWords))
        assertTrue(addressed("Computer, what time is it?", wakeWords))
    }

    @Test
    fun addressedRequiresWholeWords() {
        assertFalse(addressed("my computers are lovely", wakeWords))
        assertFalse(addressed("supercomputer", wakeWords))
        assertFalse(addressed("what time is it", wakeWords))
        assertTrue(addressed("computer, hello", wakeWords))
        assertTrue(addressed("hello.computer!", wakeWords))
    }

    @Test
    fun addressedIsTrueWhenThereAreNoWakeWords() {
        assertTrue(addressed("anything at all", emptyList()))
    }

    @Test
    fun addressedTreatsWakeWordsLiterally() {
        assertFalse(addressed("anything", listOf(".*")))
    }

    @Test
    fun onlyWakeWordAllowsGreetingsAndFiller() {
        assertTrue(onlyWakeWord("Computer", wakeWords))
        assertTrue(onlyWakeWord("Hey, Computer.", wakeWords))
        assertTrue(onlyWakeWord("okay um computer", wakeWords))
        assertTrue(onlyWakeWord("hello hi computer", wakeWords))
    }

    @Test
    fun onlyWakeWordIsFalseWithRealContentOrNoWakeWord() {
        assertFalse(onlyWakeWord("Computer what time is it", wakeWords))
        assertFalse(onlyWakeWord("hey", wakeWords))
        assertFalse(onlyWakeWord("", wakeWords))
        assertFalse(onlyWakeWord("Computer", emptyList()))
    }

    @Test
    fun expectingReplyDetectsQuestionsAndPrompts() {
        assertTrue(expectingReply("What do you mean?"))
        assertTrue(expectingReply("Please specify the city."))
        assertTrue(expectingReply("Please clarify what you want"))
        assertTrue(expectingReply("Specify which one"))
        assertTrue(expectingReply("State your request."))
        assertTrue(expectingReply("Standing by."))
        assertTrue(expectingReply("Awaiting instruction"))
        assertTrue(expectingReply("Go ahead whenever you are ready."))
        assertTrue(expectingReply("Shall I proceed"))
    }

    @Test
    fun expectingReplyIsFalseForPlainStatements() {
        assertFalse(expectingReply("It is noon."))
        assertFalse(expectingReply("Okay."))
        assertFalse(expectingReply("The procedure is finished."))
    }

    @Test
    fun withOverheardReturnsTranscriptWhenNothingWasOverheard() {
        assertEquals("what time is it", withOverheard(emptyList(), "what time is it"))
    }

    @Test
    fun withOverheardPrefixesOverheardLines() {
        assertEquals(
            "$OVERHEARD_HEADER\n" +
                "line one\nline two\n\n" +
                "Now said to you: Computer, hi",
            withOverheard(listOf("line one", "line two"), "Computer, hi")
        )
    }

    @Test
    fun isSpeechTranscriptRequiresAtLeastTwoConsecutiveLettersOrDigits() {
        assertFalse(isSpeechTranscript(""))
        assertFalse(isSpeechTranscript(" "))
        assertFalse(isSpeechTranscript("..."))
        assertFalse(isSpeechTranscript("-"))
        assertFalse(isSpeechTranscript("."))
        assertFalse(isSpeechTranscript("a"))

        assertTrue(isSpeechTranscript("hi"))
        assertTrue(isSpeechTranscript("Hello there"))
        assertTrue(isSpeechTranscript("42"))
        assertTrue(isSpeechTranscript("ok."))
    }
}
