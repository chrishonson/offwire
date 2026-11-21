package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.model.ModelSpec
import com.chrishonson.gemmavoice.model.formatDownloadSize
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadPolicyTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testSpec = ModelSpec(
        id = "test-model",
        displayName = "Test Gemma",
        url = "https://example.com/model.litertlm",
        fileName = "model.litertlm",
        sizeBytes = 2_500_000_000L
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createStore(
        repo: FakeModelRepository = FakeModelRepository(downloaded = false),
        network: FakeNetworkMonitor = FakeNetworkMonitor(unmetered = true),
        settings: SettingsStore = InMemorySettingsStore(),
        spec: ModelSpec = testSpec,
        voiceSpec: ModelSpec? = null
    ): ChatStore = ChatStore(
        engine = FakeLlmEngine(),
        repo = repo,
        audioStream = FakeAudioStream(),
        synthesizer = FakeSpeechSynthesizer(),
        micPermission = FakeMicPermission(granted = true),
        stt = FakeSpeechToText(),
        config = VoiceConfig(),
        models = listOf(spec),
        settings = settings,
        voiceSpec = voiceSpec,
        networkActivity = NetworkActivity(now = { 0L }),
        network = network
    )

    @Test
    fun cellularDefaultSettingGivesWifiRequiredAndNeverCallsDownload() = runTest {
        val repo = FakeModelRepository(downloaded = false)
        val network = FakeNetworkMonitor(unmetered = false)
        val store = createStore(repo = repo, network = network)

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()

        assertEquals(ModelStatus.WifiRequired(testSpec.sizeBytes), store.state.value.modelStatus)
        assertEquals(0, repo.downloadCalls)
    }

    @Test
    fun wifiGivesConfirmDownloadThenConfirmDownloadsAndEndsReady() = runTest {
        val repo = FakeModelRepository(downloaded = false)
        val network = FakeNetworkMonitor(unmetered = true)
        val store = createStore(repo = repo, network = network)

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()

        assertEquals(ModelStatus.ConfirmDownload(testSpec.sizeBytes), store.state.value.modelStatus)
        assertEquals(0, repo.downloadCalls)

        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()

        assertEquals(1, repo.downloadCalls)
        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
    }

    @Test
    fun cellularUserTogglesAllowThenConfirmsDownloadsAndPersistsSetting() = runTest {
        val repo = FakeModelRepository(downloaded = false)
        val network = FakeNetworkMonitor(unmetered = false)
        val settings = InMemorySettingsStore()
        val store = createStore(repo = repo, network = network, settings = settings)

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()
        assertEquals(ModelStatus.WifiRequired(testSpec.sizeBytes), store.state.value.modelStatus)

        store.dispatch(ChatIntent.ToggleCellularDownload)
        advanceUntilIdle()
        assertEquals(true, store.state.value.allowCellularDownload)
        assertEquals(true, settings.loadAllowCellularDownload())
        assertEquals(ModelStatus.ConfirmDownload(testSpec.sizeBytes), store.state.value.modelStatus)
        assertEquals(0, repo.downloadCalls)

        // Verify a new store reading the same settings store gets allowCellularDownload = true
        val secondStore = createStore(repo = repo, network = network, settings = settings)
        assertEquals(true, secondStore.state.value.allowCellularDownload)

        // In first store, confirm download now succeeds on cellular
        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()
        assertEquals(1, repo.downloadCalls)
        assertEquals(ModelStatus.Ready, store.state.value.modelStatus)
    }

    @Test
    fun networkDropsToCellularBetweenPromptAndConfirmGivesWifiRequiredNoDownload() = runTest {
        val repo = FakeModelRepository(downloaded = false)
        val network = FakeNetworkMonitor(unmetered = true)
        val store = createStore(repo = repo, network = network)

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()
        assertEquals(ModelStatus.ConfirmDownload(testSpec.sizeBytes), store.state.value.modelStatus)

        // Network drops to metered cellular
        network.unmetered = false

        store.dispatch(ChatIntent.ConfirmDownload)
        advanceUntilIdle()
        assertEquals(ModelStatus.WifiRequired(testSpec.sizeBytes), store.state.value.modelStatus)
        assertEquals(0, repo.downloadCalls)
    }

    @Test
    fun cancelDownloadFromConfirmDownloadBecomesMissing() = runTest {
        val store = createStore(network = FakeNetworkMonitor(unmetered = true))

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()
        assertEquals(ModelStatus.ConfirmDownload(testSpec.sizeBytes), store.state.value.modelStatus)

        store.dispatch(ChatIntent.CancelDownload)
        advanceUntilIdle()
        assertEquals(ModelStatus.Missing, store.state.value.modelStatus)
    }

    @Test
    fun cancelDownloadFromWifiRequiredBecomesMissing() = runTest {
        val store = createStore(network = FakeNetworkMonitor(unmetered = false))

        store.dispatch(ChatIntent.DownloadModel)
        advanceUntilIdle()
        assertEquals(ModelStatus.WifiRequired(testSpec.sizeBytes), store.state.value.modelStatus)

        store.dispatch(ChatIntent.CancelDownload)
        advanceUntilIdle()
        assertEquals(ModelStatus.Missing, store.state.value.modelStatus)
    }

    @Test
    fun formatDownloadSizeFormatsGbAndMbCorrectly() {
        assertEquals("2.6 GB", formatDownloadSize(2_588_147_712L))
        assertEquals("98 MB", formatDownloadSize(98_336_520L))
        assertEquals("2.7 GB", formatDownloadSize(2_686_484_232L))
        assertEquals("1.0 GB", formatDownloadSize(1_000_000_000L))
        assertEquals("1.1 GB", formatDownloadSize(1_050_000_000L))
        assertEquals("500 MB", formatDownloadSize(500_000_000L))
        assertEquals("2 MB", formatDownloadSize(1_500_000L))
        assertEquals("1 MB", formatDownloadSize(1_000_000L))
        assertEquals("1 MB", formatDownloadSize(500_000L))
        assertEquals("0 MB", formatDownloadSize(400_000L))
    }
}
