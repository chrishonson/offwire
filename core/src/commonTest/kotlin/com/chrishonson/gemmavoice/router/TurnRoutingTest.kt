package com.chrishonson.gemmavoice.router

import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.llm.ReplySpeed
import com.chrishonson.gemmavoice.llm.formatReplySpeed
import com.chrishonson.gemmavoice.voice.withOverheard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TurnRoutingTest {
    private fun decision(vararg intents: ToolIntent) = RouteDecision(intents.toList(), emptyMap())

    @Test
    fun onlySaveGetsATool() {
        assertEquals(TurnTools.SAVE_NOTE, turnToolsFor(decision(ToolIntent.SAVE)))
        assertEquals(TurnTools.SAVE_NOTE, turnToolsFor(decision(ToolIntent.ACTION, ToolIntent.SAVE)))
        assertEquals(TurnTools.NONE, turnToolsFor(decision(ToolIntent.ACTION)))
        assertEquals(TurnTools.NONE, turnToolsFor(decision()))
    }

    @Test
    fun routingTextIsTheTypedOrTranscribedMessage() {
        assertEquals("Remember the gate code", routingText(LlmPrompt.Text("Remember the gate code")))
    }

    @Test
    fun routingTextSkipsOverheardLines() {
        val prompt = LlmPrompt.Text(withOverheard(listOf("save me a seat", "and then we left"), "What time is it?"))
        assertEquals("What time is it?", routingText(prompt))
    }

    @Test
    fun audioPhotosAndBlankTextAreNotRouted() {
        assertNull(routingText(LlmPrompt.Audio(ByteArray(2), "Answer")))
        assertNull(routingText(LlmPrompt.Image("/tmp/a.jpg", "What is this?")))
        assertNull(routingText(LlmPrompt.Text("   ")))
    }

    @Test
    fun readoutNamesTheRouteAndItsTime() {
        assertEquals("route SAVE 76 ms", formatRoute(RouteReadout("SAVE", TurnTools.SAVE_NOTE, 76)))
        assertEquals("route NONE 70 ms", formatRoute(RouteReadout("NONE", TurnTools.NONE, 70)))
        assertEquals("route ACTION as chat 81 ms", formatRoute(RouteReadout("ACTION", TurnTools.NONE, 81)))
    }

    @Test
    fun speedLineEndsWithTheRoute() {
        val speed = ReplySpeed(260L, 16.43, RouteReadout("NONE", TurnTools.NONE, 74))
        assertEquals("0.26 s to first token · 16.4 tok/s · route NONE 74 ms", formatReplySpeed(speed))
        assertEquals("0.26 s to first token · route SAVE 9 ms", formatReplySpeed(
            ReplySpeed(260L, route = RouteReadout("SAVE", TurnTools.SAVE_NOTE, 9))
        ))
    }
}
