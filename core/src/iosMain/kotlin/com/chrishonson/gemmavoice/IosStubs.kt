package com.chrishonson.gemmavoice

import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.voice.SpeechToText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val IOS_UNSUPPORTED_MESSAGE = "iOS inference not implemented yet"

class NoOpSpeechToText : SpeechToText {
    override val isAvailable: Boolean
        get() = false

    override suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String = ""
}

class UnsupportedLlmEngine : LlmEngine {
    override suspend fun load(modelPath: String) {
        throw UnsupportedOperationException(IOS_UNSUPPORTED_MESSAGE)
    }

    override fun generate(prompt: LlmPrompt): Flow<String> = flow {
        throw UnsupportedOperationException(IOS_UNSUPPORTED_MESSAGE)
    }

    override fun cancel() = Unit

    override suspend fun resetConversation() = Unit

    override fun setAssistantName(name: String) = Unit

    override fun close() = Unit
}
