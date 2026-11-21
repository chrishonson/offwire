package com.chrishonson.gemmavoice.llm

import com.chrishonson.gemmavoice.notes.InMemoryNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeLlmBridge : LlmBridge {
    class Conversation(
        val systemInstruction: String,
        val history: List<String>,
        val saveNote: ((String) -> String)?
    )

    val conversations = mutableListOf<Conversation>()
    val sent = mutableListOf<String>()
    var cancelCount = 0
    var closeCount = 0
    var script: List<String> = emptyList()
    var finishSend = true

    var loadedTokens: Int? = null

    override fun load(modelPath: String, cacheDir: String, maxNumTokens: Int, onDone: (String?) -> Unit) {
        loadedTokens = maxNumTokens
        onDone(null)
    }

    override fun startConversation(
        systemInstruction: String,
        history: List<String>,
        saveNote: ((String) -> String)?,
        onDone: (String?) -> Unit
    ) {
        conversations += Conversation(systemInstruction, history, saveNote)
        onDone(null)
    }

    override fun send(text: String, thinking: Boolean, onChunk: (String) -> Unit, onDone: (String?) -> Unit) {
        sent += text
        script.forEach(onChunk)
        if (finishSend) onDone(null)
    }

    override fun cancel() {
        cancelCount++
    }

    override fun close() {
        closeCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class IosLlmEngineTest {
    private val bridge = FakeLlmBridge()
    private val engine = IosLlmEngine(
        bridge = bridge,
        assistantName = "Computer",
        cacheDir = "/tmp/cache"
    )

    @Test
    fun loadStartsAnEmptyConversationWithTheSystemInstruction() = runTest {
        engine.load("/model.litertlm")
        val conversation = bridge.conversations.single()
        assertTrue(conversation.history.isEmpty())
        assertContains(conversation.systemInstruction, "You are Computer,")
    }

    @Test
    fun withoutNotesTheModelGetsNoTool() = runTest {
        engine.load("/model.litertlm")
        val conversation = bridge.conversations.single()
        assertNull(conversation.saveNote)
        assertContains(conversation.systemInstruction, "You cannot save notes.")
    }

    @Test
    fun withNotesTheInstructionListsThemAndTheToolSavesToThem() = runTest {
        val notes = InMemoryNotesRepository(now = { 1L })
        notes.add("parking is level 3")
        val notesEngine = IosLlmEngine(bridge, "Computer", cacheDir = "/tmp/cache", notes = notes)
        notesEngine.load("/model.litertlm")
        val conversation = bridge.conversations.single()
        assertContains(conversation.systemInstruction, "- parking is level 3")

        val reply = conversation.saveNote!!("buy milk")

        assertEquals(listOf("parking is level 3", "buy milk"), notes.notes.value.map { it.text })
        assertContains(reply, "Saved")
    }

    @Test
    fun loadUsesTheSharedContextSize() = runTest {
        engine.load("/model.litertlm")
        assertEquals(CONTEXT_TOKENS, bridge.loadedTokens)
    }

    @Test
    fun generateEmitsGrowingTextAndCompletes() = runTest {
        engine.load("/model.litertlm")
        bridge.script = listOf("Hel", "lo", " there")
        val emitted = engine.generate(LlmPrompt.Text("hi")).toList()
        assertEquals(listOf("Hel", "Hello", "Hello there"), emitted)
    }

    @Test
    fun generateSendsTheUserTextAsIs() = runTest {
        engine.load("/model.litertlm")
        engine.generate(LlmPrompt.Text("what is up")).toList()
        assertEquals("what is up", bridge.sent.single())
    }

    @Test
    fun cancelThenGenerateStartsAConversationSeededWithTheHistory() = runTest {
        engine.load("/model.litertlm")
        bridge.script = listOf("part")
        bridge.finishSend = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { engine.generate(LlmPrompt.Text("hi")).toList() }
        job.cancelAndJoin()
        engine.cancel()
        assertEquals(1, bridge.cancelCount)

        bridge.script = listOf("ok")
        bridge.finishSend = true
        engine.generate(LlmPrompt.Text("again")).toList()

        assertEquals(listOf("hi", "part"), bridge.conversations.last().history)
        assertEquals(2, bridge.conversations.size)
    }

    @Test
    fun generateWithoutCancelKeepsTheConversation() = runTest {
        engine.load("/model.litertlm")
        bridge.script = listOf("one")
        engine.generate(LlmPrompt.Text("a")).toList()
        engine.generate(LlmPrompt.Text("b")).toList()
        assertEquals(1, bridge.conversations.size)
    }

    @Test
    fun resetConversationClearsTheHistory() = runTest {
        engine.load("/model.litertlm")
        bridge.script = listOf("one")
        engine.generate(LlmPrompt.Text("a")).toList()
        engine.resetConversation()
        assertTrue(bridge.conversations.last().history.isEmpty())
        engine.cancel()
        engine.resetConversation()
        engine.generate(LlmPrompt.Text("b")).toList()
        assertEquals(3, bridge.conversations.size)
    }

    @Test
    fun audioPromptFailsAndRecordsNothing() = runTest {
        engine.load("/model.litertlm")
        assertFailsWith<UnsupportedOperationException> {
            engine.generate(LlmPrompt.Audio(ByteArray(1), "listen")).toList()
        }
        engine.cancel()
        bridge.script = listOf("ok")
        engine.generate(LlmPrompt.Text("hi")).toList()
        assertTrue(bridge.conversations.last().history.isEmpty())
        assertEquals(1, bridge.sent.size)
    }

    @Test
    fun bridgeErrorFailsTheFlow() = runTest {
        val failing = object : LlmBridge by bridge {
            override fun send(
                text: String,
                thinking: Boolean,
                onChunk: (String) -> Unit,
                onDone: (String?) -> Unit
            ) = onDone("boom")
        }
        val failingEngine = IosLlmEngine(failing, "Computer", cacheDir = "/tmp/cache")
        failingEngine.load("/model.litertlm")
        val error = assertFailsWith<IllegalStateException> { failingEngine.generate(LlmPrompt.Text("hi")).toList() }
        assertEquals("boom", error.message)
    }

    @Test
    fun closeClosesTheBridge() {
        engine.close()
        assertEquals(1, bridge.closeCount)
    }
}
