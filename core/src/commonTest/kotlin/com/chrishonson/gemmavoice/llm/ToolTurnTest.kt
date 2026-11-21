package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolTurnTest {
    private val saved = mutableListOf<String>()
    private val turn = ToolTurn(replyWhenSilent = "I saved that to your notes.") { name, arguments ->
        saved += "$name:${arguments["text"]}"
        "Saved."
    }

    private fun save(text: String) = "save_note" to mapOf<String, Any?>("text" to text)

    @Test
    fun aReplyWithoutToolsStreamsAsBefore() {
        assertEquals("Hi", turn.onText("Hi"))
        assertEquals("Hi there.", turn.onText(" there."))
        assertNull(turn.finish())
        assertEquals("Hi there.", turn.text)
    }

    @Test
    fun theReplyAfterTheToolIsSpokenWhenNothingWasSaidBefore() {
        assertEquals(listOf("Saved."), turn.runRound(listOf(save("buy milk"))))

        assertEquals("I saved buy milk.", turn.onText("I saved buy milk."))
        assertNull(turn.finish())
        assertEquals(listOf("save_note:buy milk"), saved)
        assertFalse(turn.droppedAfterTool)
    }

    @Test
    fun theConfirmationIsNotSpokenTwiceWhenTheModelSpokeBeforeTheTool() {
        turn.onText("Got it, I've saved that you need milk.")
        turn.runRound(listOf(save("buy milk")))

        assertNull(turn.onText("I saved your note: buy milk."))
        assertNull(turn.finish())
        assertTrue(turn.droppedAfterTool)
        assertEquals("Got it, I've saved that you need milk.", turn.text)
    }

    @Test
    fun aRepeatedCallSavesOnce() {
        turn.runRound(listOf(save("buy milk")))
        turn.onText("Saved buy milk.")

        assertEquals(listOf(REPEATED_TOOL_CALL_RESULT), turn.runRound(listOf(save("buy milk"))))
        assertNull(turn.onText(" I saved buy milk."))
        assertEquals(listOf("save_note:buy milk"), saved)
        assertEquals("Saved buy milk.", turn.text)
    }

    @Test
    fun twoDifferentNotesInOneRequestAreBothSaved() {
        turn.runRound(listOf(save("buy milk"), save("call the plumber")))

        assertEquals(listOf("save_note:buy milk", "save_note:call the plumber"), saved)
    }

    @Test
    fun toolRoundsStopAtTheLimit() {
        repeat(MAX_TOOL_ROUNDS) { round ->
            assertTrue(turn.mayRunTools())
            turn.runRound(listOf(save("note $round")))
        }

        assertFalse(turn.mayRunTools())
    }

    @Test
    fun aSilentReplyAfterASaveStillConfirmsIt() {
        turn.runRound(listOf(save("buy milk")))

        assertEquals("I saved that to your notes.", turn.finish())
        assertEquals("I saved that to your notes.", turn.text)
    }

    @Test
    fun aBlankChunkBeforeTheToolDoesNotMuteTheReply() {
        turn.onText(" ")
        turn.runRound(listOf(save("buy milk")))

        assertEquals("Saved buy milk.", turn.onText("Saved buy milk.")?.trim())
    }
}
