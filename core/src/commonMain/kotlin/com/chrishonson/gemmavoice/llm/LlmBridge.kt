package com.chrishonson.gemmavoice.llm

/** The native half of an LLM engine. Every method is called from the main thread and reports back on any thread. */
interface LlmBridge {
    /** Loads the model with a context of [maxNumTokens] tokens. */
    fun load(modelPath: String, cacheDir: String, maxNumTokens: Int, onDone: (error: String?) -> Unit)

    /**
     * Starts a conversation with [systemInstruction] and [history] (alternating user, model text, user first). When
     * [saveNote] is set the model gets the save_note tool, and each call runs it on the main thread and hands the
     * model what it returns.
     */
    fun startConversation(
        systemInstruction: String,
        history: List<String>,
        saveNote: ((text: String) -> String)?,
        onDone: (error: String?) -> Unit
    )

    /**
     * Streams reply chunks; [onChunk] gets each new piece of text, [onDone] a null error on success. With [thinking]
     * the model reasons first, and its reasoning is not part of the chunks.
     */
    fun send(text: String, thinking: Boolean, onChunk: (String) -> Unit, onDone: (error: String?) -> Unit)

    fun cancel()

    fun close()
}
