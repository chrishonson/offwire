package com.chrishonson.gemmavoice.llm

/** Tool rounds one reply may take before the engine stops answering the model's calls. */
const val MAX_TOOL_ROUNDS = 3

/** What the model reads back when it repeats a call it already made in this reply. */
const val REPEATED_TOOL_CALL_RESULT = "Already done. Do not call it again and do not repeat what you already said."

/**
 * One reply in which the app, not the library, runs the model's tool calls.
 *
 * The library streams every pass of a reply, so when Gemma says "I've saved that" and then calls save_note, the
 * reply it writes after the tool result ("I saved your note") was spoken too, and the person heard the
 * confirmation twice. The eval in scripts/eval/notes.py could not see this, because a blocking call returns only
 * the last pass. So once any text has been spoken, the text that follows a tool call is dropped, and a call
 * repeated with the same arguments is answered without running again, so it cannot save the same note twice.
 *
 * [runTool] runs one call and returns what the model reads back. [replyWhenSilent] is said when a tool ran and the
 * model said nothing at all.
 */
class ToolTurn(
    private val replyWhenSilent: String,
    private val runTool: (name: String, arguments: Map<String, Any?>) -> String
) {
    private val accumulator = StreamAccumulator()
    private val callsRun = mutableSetOf<Pair<String, Map<String, Any?>>>()
    private var rounds = 0
    private var mutedAfterTool = false

    /** Whether the model wrote text after a tool call that was not spoken, because it had already spoken. */
    var droppedAfterTool = false
        private set

    /** The reply so far, as shown and spoken. */
    var text: String = ""
        private set

    /** Adds a streamed piece of the reply and returns the reply so far, or null when nothing new is shown. */
    fun onText(chunk: String): String? {
        if (chunk.isEmpty()) return null
        if (mutedAfterTool) {
            if (chunk.isNotBlank()) droppedAfterTool = true
            return null
        }
        text = accumulator.append(chunk)
        return text
    }

    /** Whether the model's next round of calls should run, or the reply should end here. */
    fun mayRunTools(): Boolean = rounds < MAX_TOOL_ROUNDS

    /** Runs one round of calls and returns, in order, what the model reads back for each. */
    fun runRound(calls: List<Pair<String, Map<String, Any?>>>): List<String> {
        rounds++
        if (text.isNotBlank()) mutedAfterTool = true
        return calls.map { call ->
            if (callsRun.add(call)) runTool(call.first, call.second) else REPEATED_TOOL_CALL_RESULT
        }
    }

    /** Called when the reply ends. Returns the reply to show when it changed, or null when it did not. */
    fun finish(): String? {
        if (rounds == 0 || text.isNotBlank()) return null
        text = replyWhenSilent
        return text
    }
}
