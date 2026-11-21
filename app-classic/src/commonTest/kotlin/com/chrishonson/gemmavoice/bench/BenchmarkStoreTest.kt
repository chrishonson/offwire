package com.chrishonson.gemmavoice.bench

import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.ModelSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class RecordingEngine : LlmEngine {
    val events = mutableListOf<String>()
    var loadError: Throwable? = null
    var loadGate: CompletableDeferred<Unit>? = null

    override suspend fun load(modelPath: String) {
        events += "load $modelPath"
        loadGate?.await()
        loadError?.let { throw it }
    }

    override fun generate(prompt: LlmPrompt): Flow<String> = flowOf("Hi.")
    override fun cancel() = Unit
    override suspend fun resetConversation() = Unit
    override fun setAssistantName(name: String) = Unit

    override fun close() {
        events += "close"
    }
}

private class RecordingHost : BenchmarkHost {
    val written = mutableMapOf<String, String>()
    override val deviceModel = "Pixel 9 Pro"
    override val backend = "gpu"

    override fun writeReport(name: String, json: String): String {
        written[name] = json
        return "/bench/$name"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkStoreTest {
    private val model = ModelSpec(
        id = "gemma-4-E4B-it",
        displayName = "Gemma 4 E4B",
        url = "https://example.com/e4b.litertlm",
        fileName = "e4b.litertlm",
        sizeBytes = 1L
    )
    private val engine = RecordingEngine()
    private val host = RecordingHost()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun store() = BenchmarkStore(engine, model, "/models/e4b.litertlm", host)

    @Test
    fun runClosesTheEngineThenLoadsTheSelectedModelAndWritesItsReport() = runTest {
        val store = store()

        store.dispatch(BenchmarkIntent.Run)
        advanceUntilIdle()

        assertEquals(listOf("close", "load /models/e4b.litertlm"), engine.events)
        val done = assertIs<BenchmarkPhase.Done>(store.state.value.phase)
        assertEquals("gemma-4-E4B-it", done.report.model)
        assertEquals("Pixel 9 Pro", done.report.deviceModel)
        assertEquals("/bench/llm-app-gemma-4-E4B-it.json", done.path)
        assertTrue(host.written.getValue("llm-app-gemma-4-E4B-it.json").contains("\"model\": \"gemma-4-E4B-it\""))
    }

    @Test
    fun aFailedLoadIsShown() = runTest {
        engine.loadError = IllegalStateException("Out of memory")
        val store = store()

        store.dispatch(BenchmarkIntent.Run)
        advanceUntilIdle()

        assertEquals(BenchmarkPhase.Failed("Out of memory"), store.state.value.phase)
        assertTrue(host.written.isEmpty())
    }

    @Test
    fun runIsIgnoredWhileRunning() = runTest {
        engine.loadGate = CompletableDeferred()
        val store = store()

        store.dispatch(BenchmarkIntent.Run)
        advanceUntilIdle()
        assertTrue(store.state.value.running)
        store.dispatch(BenchmarkIntent.Run)
        engine.loadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, engine.events.count { it.startsWith("load") })
    }

    @Test
    fun startsIdleWithTheModelName() {
        val state = store().state.value

        assertEquals("Gemma 4 E4B", state.modelName)
        assertEquals(BenchmarkPhase.Idle, state.phase)
    }

    @Test
    fun caseSummaryShowsMediansInWholeMilliseconds() {
        val case = LlmCaseResult(
            name = "Q",
            kind = "text",
            runs = emptyList(),
            aggregate = LlmCaseStats(
                ttftMs = MetricStats(120.4, null, null),
                firstClauseMs = MetricStats(null, null, null),
                totalMs = MetricStats(900.6, null, null),
                decodeChunksPerSec = MetricStats(null, null, null)
            ),
            replyText = null
        )

        assertEquals("First text 120 ms, first clause n/a, whole reply 901 ms", caseSummary(case))
    }
}
