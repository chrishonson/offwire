package com.chrishonson.gemmavoice.router

import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.voice.ADDRESSED_TO_YOU

/** Picks this turn's tools from what the user said. [EmbeddingIntentRouter] behind a platform loader. */
interface TurnRouter {
    fun decide(transcript: String): RouteDecision

    /** Loads the model ahead of the first turn, so that turn does not pay for it. */
    fun warmUp() = Unit
}

/** The tools Gemma sees on a routed turn. */
enum class TurnTools { NONE, SAVE_NOTE }

/**
 * The app has save_note and nothing else, so an ACTION turn (a timer, an email, directions) is plain chat with no
 * tools, and the instruction's limits line has Gemma say it cannot. SAVE+ACTION keeps save_note.
 */
fun turnToolsFor(decision: RouteDecision): TurnTools =
    if (ToolIntent.SAVE in decision.intents) TurnTools.SAVE_NOTE else TurnTools.NONE

/**
 * The words the router reads: a typed or transcribed message, without the overheard lines a wake-word turn puts
 * before it. Audio and photo prompts have no words to read, so they are not routed and keep every tool.
 */
fun routingText(prompt: LlmPrompt): String? = when (prompt) {
    is LlmPrompt.Text -> prompt.text.substringAfterLast(ADDRESSED_TO_YOU, prompt.text).takeIf { it.isNotBlank() }
    is LlmPrompt.Audio, is LlmPrompt.Image -> null
}

/** What the speed readout shows for a routed turn. [label] is the router's (NONE, SAVE, ACTION, SAVE+ACTION). */
data class RouteReadout(val label: String, val tools: TurnTools, val millis: Long)

/** For example "route SAVE 76 ms", or "route ACTION as chat 80 ms" when the app has no tool for it. */
fun formatRoute(route: RouteReadout): String {
    val asChat = route.tools == TurnTools.NONE && route.label != "NONE"
    return "route ${route.label}${if (asChat) " as chat" else ""} ${route.millis} ms"
}
