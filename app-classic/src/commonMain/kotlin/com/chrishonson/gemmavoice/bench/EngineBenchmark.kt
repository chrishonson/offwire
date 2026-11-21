package com.chrishonson.gemmavoice.bench

import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import kotlin.time.TimeSource

/** The same prompts on every platform, so the numbers compare. */
val ENGINE_BENCH_PROMPTS = listOf(
    "What is the capital of France?",
    "In two sentences, why does a GFCI outlet trip?",
    "Give me three tips for reading a furnace data plate."
)

private const val WARM_UP_PROMPT = "Say hello."

/**
 * Times the app's own engine, so Android and iOS are measured the same way: the load, one discarded warm-up reply,
 * then each prompt [runs] times, each in a fresh conversation.
 */
suspend fun benchmarkEngine(
    engine: LlmEngine,
    modelPath: String,
    deviceModel: String,
    backend: String,
    runs: Int,
    model: String? = null,
    timeSource: TimeSource = TimeSource.Monotonic,
    onStatus: (String) -> Unit = {}
): LlmBenchmarkReport {
    val origin = timeSource.markNow()
    val nanos = { origin.elapsedNow().inWholeNanoseconds }
    onStatus("Loading")
    engine.load(modelPath)
    val loadMs = nanos() / NANOS_PER_MILLI
    engine.generate(LlmPrompt.Text(WARM_UP_PROMPT)).collect {}
    val cases = ENGINE_BENCH_PROMPTS.mapIndexed { index, prompt ->
        onStatus("Running case ${index + 1} of ${ENGINE_BENCH_PROMPTS.size}")
        val replies = List(runs) { timeReply(engine, prompt, nanos) }
        val summaries = replies.map { it.first }
        LlmCaseResult(prompt, "text", summaries, aggregate(summaries), replies.first().second)
    }
    return LlmBenchmarkReport(backend, deviceModel, model, loadMs = loadMs, visionAvailable = false, cases = cases)
}

private const val NANOS_PER_MILLI = 1_000_000L

private suspend fun timeReply(engine: LlmEngine, prompt: String, nanos: () -> Long): Pair<LlmRunSummary, String> {
    engine.resetConversation()
    val chunkNanos = mutableListOf<Long>()
    val chunkTexts = mutableListOf<String>()
    var reply = ""
    val sendNanos = nanos()
    // The engine emits the whole reply so far each time, so each new piece is what was added.
    engine.generate(LlmPrompt.Text(prompt)).collect { text ->
        if (text.length > reply.length) {
            chunkNanos += nanos()
            chunkTexts += text.substring(reply.length)
            reply = text
        }
    }
    return summarize(LlmRunTiming(sendNanos, chunkNanos, chunkTexts, nanos())) to reply
}
