package com.chrishonson.gemmavoice.llm

import com.chrishonson.gemmavoice.router.RouteReadout
import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    suspend fun load(modelPath: String)
    fun generate(prompt: LlmPrompt): Flow<String>
    fun cancel()
    /** Cancels a turn whose message the next prompt sends again, so the engine can drop the turn, not keep it. */
    fun cancelForResend() = cancel()
    suspend fun resetConversation()
    /** Takes effect when the next conversation starts. */
    fun setAssistantName(name: String)
    /** Lets the model reason before it answers, from the next reply on. The reasoning is not part of the reply. */
    fun setThinking(enabled: Boolean) = Unit
    fun close()

    /** How the intent router sorted the latest turn; null when the router is off or the turn was not routed. */
    val lastRoute: RouteReadout?
        get() = null
}
