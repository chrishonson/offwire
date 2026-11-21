package com.chrishonson.gemmavoice.bench

import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Loads in 2 s; every reply takes 100 ms to its first piece, then 50 ms per piece. */
private class TimedEngine(private val clock: TestTimeSource) : LlmEngine {
    val prompts = mutableListOf<String>()
    var resets = 0

    override suspend fun load(modelPath: String) {
        clock += 2_000.milliseconds
    }

    override fun generate(prompt: LlmPrompt): Flow<String> = flow {
        prompts += (prompt as LlmPrompt.Text).text
        clock += 100.milliseconds
        emit("Hel")
        clock += 50.milliseconds
        emit("Hello")
        clock += 50.milliseconds
        emit("Hello.")
    }

    override fun cancel() = Unit
    override suspend fun resetConversation() {
        resets++
    }

    override fun setAssistantName(name: String) = Unit
    override fun close() = Unit
}

class EngineBenchmarkTest {
    private val clock = TestTimeSource()
    private val engine = TimedEngine(clock)

    @Test
    fun timesTheLoadAndEveryRunAfterOneWarmUp() = runTest {
        val report = benchmarkEngine(engine, "/model", "Pixel", "gpu", runs = 2, timeSource = clock)

        assertEquals(2_000L, report.loadMs)
        assertEquals(1 + ENGINE_BENCH_PROMPTS.size * 2, engine.prompts.size)
        assertEquals(ENGINE_BENCH_PROMPTS.size * 2, engine.resets)
        val case = report.cases.first()
        assertEquals(ENGINE_BENCH_PROMPTS.first(), case.name)
        assertEquals("Hello.", case.replyText)
        assertEquals(100.0, case.aggregate.ttftMs.median)
        assertEquals(20.0, case.aggregate.decodeChunksPerSec.median)
        assertEquals(listOf(3, 3), case.runs.map { it.chunks })
    }
}
