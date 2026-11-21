package com.chrishonson.gemmavoice.llm

import com.chrishonson.gemmavoice.notes.NotesRepository
import com.chrishonson.gemmavoice.notes.saveNote
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import kotlin.coroutines.resume

class IosLlmEngine(
    private val bridge: LlmBridge,
    private var assistantName: String,
    private val cacheDir: String = defaultCacheDir(),
    /** When set, the model gets the save_note tool and sees these notes in its instruction. */
    private val notes: NotesRepository? = null
) : LlmEngine {
    private val history = mutableListOf<String>()

    // A cancelled native conversation rejects every later request, so the next turn starts a fresh one
    // seeded with the turns so far.
    private var cancelled = false
    private var loaded = false
    private var thinking = false

    override suspend fun load(modelPath: String) {
        awaitBridge { bridge.load(modelPath, cacheDir, CONTEXT_TOKENS, it) }
        history.clear()
        cancelled = false
        startConversation()
        loaded = true
    }

    override fun generate(prompt: LlmPrompt): Flow<String> = flow {
        val text = when (prompt) {
            is LlmPrompt.Text -> prompt.text
            is LlmPrompt.Audio -> throw UnsupportedOperationException("audio prompts are not supported on iOS yet")
            is LlmPrompt.Image -> throw UnsupportedOperationException("image prompts are not supported on iOS yet")
        }
        check(loaded) { "LlmEngine is not loaded" }
        if (cancelled) {
            cancelled = false
            startConversation()
        }
        val accumulator = StreamAccumulator()
        var fullText = ""
        try {
            emitAll(replyChunks(text).map { accumulator.append(it) }.onEach { fullText = it })
        } finally {
            // Keep what the user said and whatever of the reply they saw, even when it was cut off. A turn with no
            // reply is dropped so the history keeps alternating user and model.
            if (fullText.isNotBlank()) {
                history += text
                history += fullText
            }
        }
    }

    override fun cancel() {
        bridge.cancel()
        cancelled = true
    }

    override suspend fun resetConversation() {
        history.clear()
        cancelled = false
        if (loaded) startConversation()
    }

    override fun setAssistantName(name: String) {
        assistantName = name
    }

    override fun setThinking(enabled: Boolean) {
        thinking = enabled
    }

    override fun close() {
        loaded = false
        bridge.close()
    }

    private suspend fun startConversation() {
        val instruction = systemInstruction(assistantName, deviceWord(), notes?.notes?.value?.map { it.text })
        val saveNote = notes?.let { repository -> { text: String -> repository.saveNote(text) } }
        awaitBridge { bridge.startConversation(instruction, history.toList(), saveNote, it) }
    }

    private fun replyChunks(text: String): Flow<String> = callbackFlow {
        bridge.send(
            text = text,
            thinking = thinking,
            onChunk = { trySend(it) },
            onDone = { error -> close(error?.let { IllegalStateException(it) }) }
        )
        awaitClose()
    }

    private suspend fun awaitBridge(start: (onDone: (String?) -> Unit) -> Unit) {
        val error = suspendCancellableCoroutine { continuation -> start { continuation.resume(it) } }
        if (error != null) throw IllegalStateException(error)
    }
}

private fun deviceWord(): String =
    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) "iPad" else "phone"

private fun defaultCacheDir(): String =
    NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String
        ?: NSTemporaryDirectory()
