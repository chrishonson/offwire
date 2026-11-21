package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

class FakeLlmEngine : LlmEngine {
    var chunks: List<String> = emptyList()
    var loadError: Throwable? = null
    var generateError: Throwable? = null
    var holdAfterChunks: Boolean = false
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var loadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    val loadedPaths = mutableListOf<String>()
    val prompts = mutableListOf<LlmPrompt>()
    val assistantNames = mutableListOf<String>()
    val events = mutableListOf<String>()
    var cancelCount: Int = 0
    var resetCount: Int = 0
    var closeCount: Int = 0
    var thinking: Boolean = false
        private set

    override suspend fun load(modelPath: String) {
        events.add("load")
        loadGate?.await()
        loadError?.let { throw it }
        loadedPaths.add(modelPath)
    }

    override fun generate(prompt: LlmPrompt): Flow<String> {
        events.add("generate")
        prompts.add(prompt)
        val g = gate
        return flow {
            g?.await()
            for (chunk in chunks) {
                emit(chunk)
                yield()
            }
            generateError?.let { throw it }
            if (holdAfterChunks) {
                awaitCancellation()
            }
        }
    }

    override fun cancel() {
        events.add("cancel")
        cancelCount++
    }

    override suspend fun resetConversation() {
        events.add("resetConversation")
        resetCount++
    }

    override fun setAssistantName(name: String) {
        assistantNames.add(name)
    }

    override fun close() {
        closeCount++
    }

    override fun setThinking(enabled: Boolean) {
        thinking = enabled
    }
}
