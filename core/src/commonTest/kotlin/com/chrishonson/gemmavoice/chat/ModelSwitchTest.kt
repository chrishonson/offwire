package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.model.ModelRepository
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun spec(id: String, sizeBytes: Long) = ModelSpec(
    id = id,
    displayName = "Model $id",
    url = "https://example.com/$id.litertlm",
    fileName = "$id.litertlm",
    sizeBytes = sizeBytes
)

/** Tracks each model on its own, so one can be on disk while the other is not. */
private class PerModelRepository(val downloaded: MutableSet<String>) : ModelRepository {
    override fun modelPath(spec: ModelSpec): String = "/models/${spec.fileName}"

    override fun isDownloaded(spec: ModelSpec): Boolean = spec.id in downloaded

    override fun download(spec: ModelSpec): Flow<Long> = flow {
        emit(spec.sizeBytes)
        downloaded += spec.id
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelSwitchTest {
    private val testDispatcher = StandardTestDispatcher()
    private val small = spec("small", 2_000L)
    private val large = spec("large", 3_000L)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        engine: FakeLlmEngine = FakeLlmEngine(),
        repo: ModelRepository = PerModelRepository(mutableSetOf("small", "large")),
        settings: InMemorySettingsStore = InMemorySettingsStore()
    ): ChatStore = ChatStore(
        engine = engine,
        repo = repo,
        audioStream = FakeAudioStream(),
        synthesizer = FakeSpeechSynthesizer(),
        micPermission = FakeMicPermission(granted = true),
        stt = FakeSpeechToText(),
        models = listOf(small, large),
        settings = settings,
        voiceSpec = null,
        networkActivity = NetworkActivity(now = { 0L })
    )

    @Test
    fun startsWithTheFirstModelWhenNoneIsSaved() {
        val store = createStore()

        assertEquals("small", store.state.value.modelId)
        assertEquals("Model small", store.state.value.modelName)
    }

    @Test
    fun startsWithTheSavedModel() = runTest {
        val engine = FakeLlmEngine()
        val store = createStore(engine = engine, settings = InMemorySettingsStore(modelId = "large"))

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals("large", store.state.value.modelId)
        assertEquals(listOf("/models/large.litertlm"), engine.loadedPaths)
    }

    @Test
    fun unknownSavedModelFallsBackToTheFirst() {
        val store = createStore(settings = InMemorySettingsStore(modelId = "gone"))

        assertEquals("small", store.state.value.modelId)
    }

    @Test
    fun switchingToADownloadedModelReloadsTheEngineAndSavesTheChoice() = runTest {
        val engine = FakeLlmEngine()
        val settings = InMemorySettingsStore()
        val store = createStore(engine = engine, settings = settings)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.SelectModel("large"))
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals("Model large", store.state.value.modelName)
        assertEquals("large", settings.loadModelId())
        assertEquals(listOf("/models/small.litertlm", "/models/large.litertlm"), engine.loadedPaths)
        assertEquals(1, engine.closeCount)
        assertEquals(1, engine.resetCount)
    }

    @Test
    fun switchingToAMissingModelAsksForItsDownloadAndKeepsTheOther() = runTest {
        val engine = FakeLlmEngine()
        val repo = PerModelRepository(mutableSetOf("small"))
        val store = createStore(engine = engine, repo = repo)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.SelectModel("large"))
        advanceUntilIdle()
        assertEquals(ModelStatus.Missing, store.state.value.modelStatus)

        store.dispatch(ChatIntent.DownloadModel)
        assertEquals(ModelStatus.ConfirmDownload(large.sizeBytes), store.state.value.modelStatus)
        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertEquals(setOf("small", "large"), repo.downloaded)
        assertEquals("/models/large.litertlm", engine.loadedPaths.last())
    }

    @Test
    fun switchingStartsANewChat() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("Hi") }
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.InputChanged("Hello"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(store.state.value.messages.isNotEmpty())

        store.dispatch(ChatIntent.SelectModel("large"))
        advanceUntilIdle()

        assertTrue(store.state.value.messages.isEmpty())
    }

    @Test
    fun selectingTheCurrentModelDoesNothing() = runTest {
        val engine = FakeLlmEngine()
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        store.dispatch(ChatIntent.SelectModel("small"))
        advanceUntilIdle()

        assertEquals(0, engine.closeCount)
        assertEquals(1, engine.loadedPaths.size)
    }

    @Test
    fun switchingIsIgnoredWhileTheModelLoads() = runTest {
        val engine = FakeLlmEngine().apply { loadGate = kotlinx.coroutines.CompletableDeferred() }
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        assertEquals(ModelStatus.Loading, store.state.value.modelStatus)

        store.dispatch(ChatIntent.SelectModel("large"))
        engine.loadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals("small", store.state.value.modelId)
        assertEquals(listOf("/models/small.litertlm"), engine.loadedPaths)
    }

    @Test
    fun reloadLoadsTheSameModelAgainInANewChat() = runTest {
        val engine = FakeLlmEngine().apply { chunks = listOf("Hi") }
        val store = createStore(engine = engine)
        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()
        store.dispatch(ChatIntent.InputChanged("Hello"))
        store.dispatch(ChatIntent.Send)
        advanceUntilIdle()

        store.dispatch(ChatIntent.ReloadModel)
        advanceUntilIdle()

        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
        assertTrue(store.state.value.messages.isEmpty())
        assertEquals(listOf("/models/small.litertlm", "/models/small.litertlm"), engine.loadedPaths)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun theDefaultCatalogOffersE2bThenE4b() {
        assertEquals(listOf(Models.GEMMA_4_E2B, Models.GEMMA_4_E4B), Models.GEMMA)
    }

    @Test
    fun aDeviceWithLittleRamIsOfferedOnlyE2b() {
        assertEquals(listOf(Models.GEMMA_4_E2B), Models.languageModelsFor(totalRamBytes = 3_000_000_000L))
    }

    @Test
    fun aDeviceWithEnoughRamIsOfferedBoth() {
        assertEquals(Models.GEMMA, Models.languageModelsFor(totalRamBytes = 16_000_000_000L))
        assertEquals(Models.GEMMA, Models.languageModelsFor(totalRamBytes = Models.GEMMA_4_E4B.minRamBytes))
    }

    @Test
    fun theStateListsTheModelsTheStoreWasGiven() {
        assertEquals(listOf(small, large), createStore().state.value.models)
    }

    @Test
    fun aSavedModelTheDeviceCannotRunFallsBackToTheFirst() = runTest {
        val engine = FakeLlmEngine()
        val store = ChatStore(
            engine = engine,
            repo = PerModelRepository(mutableSetOf("small", "large")),
            audioStream = FakeAudioStream(),
            synthesizer = FakeSpeechSynthesizer(),
            micPermission = FakeMicPermission(granted = true),
            stt = FakeSpeechToText(),
            models = listOf(small),
            settings = InMemorySettingsStore(modelId = "large"),
            voiceSpec = null,
            networkActivity = NetworkActivity(now = { 0L })
        )

        store.dispatch(ChatIntent.Start)
        advanceUntilIdle()

        assertEquals("small", store.state.value.modelId)
        assertEquals(listOf("/models/small.litertlm"), engine.loadedPaths)
    }
}
