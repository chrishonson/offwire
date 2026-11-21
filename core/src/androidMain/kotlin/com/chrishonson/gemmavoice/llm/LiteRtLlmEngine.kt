package com.chrishonson.gemmavoice.llm

import android.os.SystemClock
import android.util.Log
import com.chrishonson.gemmavoice.core.BuildConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.tool
import com.chrishonson.gemmavoice.notes.NoteTools
import com.chrishonson.gemmavoice.notes.NotesRepository
import com.chrishonson.gemmavoice.notes.SAVE_NOTE_NAME
import com.chrishonson.gemmavoice.notes.saveNote
import com.chrishonson.gemmavoice.router.RouteReadout
import com.chrishonson.gemmavoice.router.TurnRouter
import com.chrishonson.gemmavoice.router.TurnTools
import com.chrishonson.gemmavoice.router.routingText
import com.chrishonson.gemmavoice.router.turnToolsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private const val LOG_TAG = "GemmaVoice"
private const val DEFAULT_TOP_K = 64
private const val DEFAULT_TOP_P = 0.95
private const val DEFAULT_TEMPERATURE = 1.0
private const val DEFAULT_SEED = 0
private const val INSTRUCTION_LOG_CHARS = 60
// Logcat drops the end of a line past about 4 KB, so a long reply is logged in parts.
private const val REPLY_LOG_CHUNK = 3_000
private const val SAVED_WHEN_SILENT = "I saved that to your notes."

@Suppress("TooManyFunctions")
class LiteRtLlmEngine(
    private val cacheDir: String,
    @Volatile private var assistantName: String,
    val backend: LlmBackend = LlmBackend.CPU,
    private val contextTokens: Int = CONTEXT_TOKENS,
    /** When set, the model gets the save_note tool and sees these notes in its instruction. */
    private val notes: NotesRepository? = null,
    /**
     * When set (and [notes] is), each typed or transcribed turn is routed first: a chat turn's conversation has no
     * tool schemas, a save request's has save_note alone. Audio and photo turns are not routed and keep save_note.
     */
    router: TurnRouter? = null
) : LlmEngine {
    private val tools = notes?.let { listOf(tool(NoteTools(it))) }.orEmpty()
    private val router = router?.takeIf { notes != null }

    // The tools the current conversation was made with: null is unrouted (save_note, as without a router). A turn
    // that needs other tools starts a fresh conversation seeded with the history, since tools and the instruction
    // are fixed per conversation. Chat stays on one conversation; a save switches to save_note and back.
    @Volatile
    private var conversationTools: TurnTools? = null

    @Volatile
    private var thinking = false

    @Volatile
    override var lastRoute: RouteReadout? = null
        private set

    private var engine: Engine? = null

    // Barge-in cancels from the main thread while a turn may be swapping conversations on a worker thread; the
    // lock keeps cancelProcess() off a conversation that is being closed, which the library rejects with a crash.
    private val lock = Any()
    private var conversation: Conversation? = null

    // cancelProcess() leaves the conversation cancelling every later request, so the next turn starts a fresh
    // conversation seeded with the turns so far.
    private val history = mutableListOf<Message>()

    // A copy of the conversation taken before each turn. When a cancelled turn is sent again, the next turn resumes
    // from it, which skips replaying the history and keeps the tool calls the replay drops.
    private var beforeTurn: Conversation? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var resending = false

    // Set in load(): the E4B file gets one more chat-turn sentence (see SHORTER_ON_ASK in SystemInstruction.kt).
    @Volatile
    private var shorterOnAsk = false

    override suspend fun load(modelPath: String) {
        withContext(Dispatchers.Default) {
            val startMs = SystemClock.elapsedRealtime()
            close()
            shorterOnAsk = shorterOnAskFor(modelPath)
            val backendObj = when (backend) {
                LlmBackend.CPU -> Backend.CPU()
                LlmBackend.GPU -> Backend.GPU()
            }
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backendObj,
                visionBackend = backendObj,
                audioBackend = Backend.CPU(),
                maxNumImages = 1,
                cacheDir = cacheDir,
                maxNumTokens = contextTokens
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            warmUpRouter()
            replaceConversation(createNewConversation(newEngine, router?.let { TurnTools.NONE }))
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            Log.i(LOG_TAG, "Loaded LiteRT-LM model from $modelPath in ${elapsedMs}ms")
        }
    }

    override fun generate(prompt: LlmPrompt): Flow<String> = flow {
        prepareConversation(routeTurn(prompt))
        val activeConversation = checkNotNull(synchronized(lock) { conversation }) { "LlmEngine is not loaded" }
        val startMs = SystemClock.elapsedRealtime()
        keepCopyBeforeTurn(activeConversation)
        Log.i(LOG_TAG, "Copied the conversation in ${SystemClock.elapsedRealtime() - startMs}ms")
        // Debug builds only; opt in with adb shell setprop log.tag.GemmaVoice DEBUG.
        val logContent = BuildConfig.DEBUG && Log.isLoggable(LOG_TAG, Log.DEBUG)
        if (prompt is LlmPrompt.Audio && logContent) {
            val instruction = prompt.instruction.take(INSTRUCTION_LOG_CHARS)
            Log.d(LOG_TAG, "Audio prompt ${prompt.wavBytes.size}B, instruction: $instruction")
        }
        // The engine runs tool calls itself (automaticToolCalling = false), so ToolTurn can keep a confirmation
        // from being spoken twice: the library streams the text before a call and the text after it alike.
        val turn = ToolTurn(SAVED_WHEN_SILENT, ::runTool)
        try {
            streamReply(activeConversation, Message.user(contentsOf(prompt)), turn)
            if (turn.droppedAfterTool) Log.i(LOG_TAG, "Kept the reply after a tool call silent: it was already spoken")
        } finally {
            // Keep what the user said and whatever of the reply they saw, even when it was cut off.
            synchronized(lock) {
                history += Message.user(historyText(prompt))
                if (turn.text.isNotBlank()) history += Message.model(turn.text)
            }
        }
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        Log.i(LOG_TAG, "Completed generation in ${elapsedMs}ms")
        if (logContent) turn.text.chunked(REPLY_LOG_CHUNK).forEach { Log.d(LOG_TAG, "Reply: $it") }
    }.flowOn(Dispatchers.Default)

    override fun cancel() {
        synchronized(lock) {
            conversation?.cancelProcess()
            cancelled = true
            resending = false
        }
    }

    override fun cancelForResend() {
        cancel()
        resending = true
    }

    override suspend fun resetConversation() = withContext(Dispatchers.Default) {
        synchronized(lock) {
            history.clear()
            cancelled = false
            dropCopyBeforeTurn()
        }
        replaceConversation(engine?.let { createNewConversation(it, conversationTools) })
    }

    override fun setAssistantName(name: String) {
        assistantName = name
    }

    override fun setThinking(enabled: Boolean) {
        thinking = enabled
    }

    override fun close() {
        synchronized(lock) { dropCopyBeforeTurn() }
        replaceConversation(null)
        engine?.close()
        engine = null
    }


    /** Streams one reply into this flow, answering the model's tool calls until it replies without one. */
    private suspend fun FlowCollector<String>.streamReply(conversation: Conversation, first: Message, turn: ToolTurn) {
        var message = first
        while (true) {
            val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
            // The model's reasoning arrives on a channel, not in the text, so it is never shown or spoken.
            val thinkingConfig = if (thinking) ThinkingConfig(enableThinking = true) else null
            conversation.sendMessageAsync(message, thinkingConfig = thinkingConfig).collect { part ->
                // Anything outside plain text and tool calls is dropped; log it to see it.
                if (part.channels.isNotEmpty() && thinkingConfig == null) {
                    Log.w(LOG_TAG, "Dropped non-text output: channels ${part.channels.keys}")
                }
                part.toolCalls.forEach { calls += it.name to it.arguments }
                val chunk = part.contents.contents.mapNotNull { (it as? Content.Text)?.text }.joinToString("")
                turn.onText(chunk)?.let { emit(it) }
            }
            if (calls.isEmpty() || !turn.mayRunTools()) break
            val results = turn.runRound(calls)
            message = Message.tool(
                Contents.of(calls.zip(results).map { (call, result) -> Content.ToolResponse(call.first, result) })
            )
        }
        turn.finish()?.let { emit(it) }
    }

    /** This turn's tools from the router, or null (unrouted: save_note) when there is no router or no words. */
    private fun routeTurn(prompt: LlmPrompt): TurnTools? {
        val text = routingText(prompt)
        val decision = if (router == null || text == null) {
            null
        } else {
            try {
                router.decide(text)
            } catch (expected: Exception) {
                // A router that cannot load or run must not cost the turn: fall back to the unrouted tools.
                Log.w(LOG_TAG, "Intent router failed; this turn keeps save_note", expected)
                null
            }
        }
        val turnTools = decision?.let(::turnToolsFor)
        lastRoute = decision?.let { RouteReadout(it.label, turnToolsFor(it), it.millis) }
        if (decision != null) {
            Log.i(LOG_TAG, "Routed ${decision.label} to $turnTools in ${decision.millis}ms ${decision.margins}")
        }
        return turnTools
    }

    private fun warmUpRouter() {
        val activeRouter = router ?: return
        try {
            val startMs = SystemClock.elapsedRealtime()
            activeRouter.warmUp()
            Log.i(LOG_TAG, "Loaded the intent router in ${SystemClock.elapsedRealtime() - startMs}ms")
        } catch (expected: Exception) {
            Log.w(LOG_TAG, "Intent router failed to load; turns keep save_note", expected)
        }
    }

    /** Runs one of the model's tool calls on the engine's thread and returns what the model reads back. */
    private fun runTool(name: String, arguments: Map<String, Any?>): String {
        val repository = notes
        val text = arguments["text"] as? String
        Log.i(LOG_TAG, "Tool call: $name")
        return when {
            repository == null || name != SAVE_NOTE_NAME -> "There is no tool called $name."
            text.isNullOrBlank() -> "Nothing was saved because the note was empty. Ask the person what to save."
            else -> repository.saveNote(text)
        }
    }

    /** Starts a conversation for this turn's tools, or resumes the copy from before a cancelled turn sent again. */
    private fun prepareConversation(turnTools: TurnTools?) {
        // The copy has the tools of the conversation it was taken from, so it only serves a turn with the same route.
        val sameTools = turnTools == conversationTools
        if (!cancelled && sameTools) return
        val resumed = synchronized(lock) {
            beforeTurn.takeIf { cancelled && resending && sameTools }.also { if (it != null) beforeTurn = null }
        }
        cancelled = false
        val how = if (resumed != null) "Resumed from the copy before the cancelled turn" else "Rebuilt the conversation"
        Log.i(LOG_TAG, how)
        replaceConversation(resumed ?: engine?.let { createNewConversation(it, turnTools) })
    }

    private fun contentsOf(prompt: LlmPrompt): Contents = when (prompt) {
        is LlmPrompt.Text -> Contents.of(Content.Text(prompt.text))
        is LlmPrompt.Audio -> Contents.of(Content.AudioBytes(prompt.wavBytes), Content.Text(prompt.instruction))
        is LlmPrompt.Image -> Contents.of(Content.ImageFile(prompt.imagePath), Content.Text(prompt.instruction))
    }

    private fun keepCopyBeforeTurn(active: Conversation) = synchronized(lock) {
        dropCopyBeforeTurn()
        beforeTurn = active.clone()
    }

    private fun dropCopyBeforeTurn() {
        beforeTurn?.close()
        beforeTurn = null
    }

    private fun replaceConversation(fresh: Conversation?) {
        synchronized(lock) {
            val stale = conversation
            conversation = fresh
            stale?.close()
        }
    }

    private fun createNewConversation(activeEngine: Engine, turnTools: TurnTools?): Conversation {
        conversationTools = turnTools
        val saved = notes?.notes?.value?.map { it.text }
        val instruction = if (turnTools == null || saved == null) {
            systemInstruction(assistantName, notes = saved)
        } else {
            routedSystemInstruction(
                assistantName,
                saved,
                saveNote = turnTools == TurnTools.SAVE_NOTE,
                shorterOnAsk = shorterOnAsk
            )
        }
        return activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(Content.Text(instruction)),
                initialMessages = synchronized(lock) { history.toList() },
                tools = if (turnTools == TurnTools.NONE) emptyList() else tools,
                automaticToolCalling = false,
                samplerConfig = SamplerConfig(
                    topK = DEFAULT_TOP_K,
                    topP = DEFAULT_TOP_P,
                    temperature = DEFAULT_TEMPERATURE,
                    seed = DEFAULT_SEED
                )
            )
        )
    }
}

private fun historyText(prompt: LlmPrompt): String = when (prompt) {
    is LlmPrompt.Text -> prompt.text
    is LlmPrompt.Audio -> "(spoken message)"
    is LlmPrompt.Image -> "(photo)"
}
