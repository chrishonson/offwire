package com.chrishonson.gemmavoice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrishonson.gemmavoice.bench.BenchmarkHost
import com.chrishonson.gemmavoice.bench.BenchmarkPhase
import com.chrishonson.gemmavoice.bench.BenchmarkScreen
import com.chrishonson.gemmavoice.bench.BenchmarkStore
import com.chrishonson.gemmavoice.chat.ChatIntent
import com.chrishonson.gemmavoice.chat.ChatScreen
import com.chrishonson.gemmavoice.chat.ChatStore
import com.chrishonson.gemmavoice.chat.LivePhase
import com.chrishonson.gemmavoice.devicecheck.DeviceCheckCompletionStore
import com.chrishonson.gemmavoice.devicecheck.DeviceCheckScreen
import com.chrishonson.gemmavoice.devicecheck.DeviceCheckStore
import com.chrishonson.gemmavoice.devicecheck.DeviceInfo
import com.chrishonson.gemmavoice.devicecheck.InMemoryDeviceCheckCompletionStore
import com.chrishonson.gemmavoice.devicecheck.UnknownDeviceInfo
import com.chrishonson.gemmavoice.limits.AcknowledgementStore
import com.chrishonson.gemmavoice.limits.DEFAULT_FEATURE_CONFIG
import com.chrishonson.gemmavoice.limits.InMemoryAcknowledgementStore
import com.chrishonson.gemmavoice.limits.LimitsIntent
import com.chrishonson.gemmavoice.limits.LimitsStore
import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.model.AssumeUnmetered
import com.chrishonson.gemmavoice.model.ModelRepository
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.model.NetworkMonitor
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.resource.ResourceMonitor
import com.chrishonson.gemmavoice.resource.ResourceSampler
import com.chrishonson.gemmavoice.settings.SettingsScreen
import com.chrishonson.gemmavoice.notes.InMemoryNotesRepository
import com.chrishonson.gemmavoice.notes.NotesRepository
import com.chrishonson.gemmavoice.notes.NotesScreen
import com.chrishonson.gemmavoice.notes.NotesStore
import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.EnergyVad
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.MicPermission
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.SpeechToText
import com.chrishonson.gemmavoice.voice.VoiceActivityDetector
import com.chrishonson.gemmavoice.voice.VoiceConfig

internal enum class AppScreen { CHAT, SETTINGS, NOTES, BENCHMARK }

private val AppScreenSaver: Saver<AppScreen, String> = Saver(
    save = { it.name },
    restore = { AppScreen.valueOf(it) }
)

@Composable
@Suppress("CyclomaticComplexMethod")
fun App(
    engine: LlmEngine,
    repo: ModelRepository,
    audioStream: AudioStream,
    synthesizer: SpeechSynthesizer,
    micPermission: MicPermission,
    stt: SpeechToText,
    config: VoiceConfig = VoiceConfig(),
    vad: VoiceActivityDetector = EnergyVad(config),
    settings: SettingsStore = InMemorySettingsStore(),
    network: NetworkMonitor = AssumeUnmetered,
    notes: NotesRepository = InMemoryNotesRepository(),
    acknowledgementStore: AcknowledgementStore = InMemoryAcknowledgementStore(),
    deviceInfo: DeviceInfo = UnknownDeviceInfo,
    deviceCheckCompletionStore: DeviceCheckCompletionStore = InMemoryDeviceCheckCompletionStore(),
    appConfig: AppConfig,
    networkActivity: NetworkActivity = NetworkActivity(now = { 0L }),
    resourceMonitor: ResourceSampler = ResourceMonitor(),
    appVersion: String = "",
    /** Null hides the benchmark; apps pass one in debug builds only. */
    benchmarkHost: BenchmarkHost? = null,
    onListeningChanged: (Boolean) -> Unit = {},
    onStoreReady: (ChatStore) -> Unit = {}
) {
    var currentScreen by rememberSaveable(stateSaver = AppScreenSaver) { mutableStateOf(AppScreen.CHAT) }
    // Each visit gets a fresh benchmark, so leaving reloads the chat's model only after a run this visit.
    var benchmarkVisit by rememberSaveable { mutableStateOf(0) }

    GemmaVoiceTheme {
        val store: ChatStore = viewModel {
            ChatStore(
                engine = engine,
                repo = repo,
                audioStream = audioStream,
                synthesizer = synthesizer,
                micPermission = micPermission,
                stt = stt,
                config = config,
                vad = vad,
                settings = settings,
                models = Models.languageModelsFor(deviceInfo.totalRamBytes),
                voiceSpec = if (appConfig.clonedVoice) Models.POCKET_TTS else null,
                network = network,
                networkActivity = networkActivity,
                resourceMonitor = resourceMonitor
            )
        }
        val limitsStore: LimitsStore = viewModel {
            LimitsStore(
                acknowledgementStore = acknowledgementStore,
                featureConfig = DEFAULT_FEATURE_CONFIG,
            )
        }
        val deviceCheckStore: DeviceCheckStore = viewModel {
            DeviceCheckStore(
                deviceInfo = deviceInfo,
                completionStore = deviceCheckCompletionStore,
                acknowledgementStore = acknowledgementStore,
                voiceSpec = if (appConfig.clonedVoice) Models.POCKET_TTS else null
            )
        }
        val limitsState by limitsStore.state.collectAsState()
        val deviceCheckState by deviceCheckStore.state.collectAsState()
        val chatState by store.state.collectAsState()

        LaunchedEffect(store) {
            onStoreReady(store)
        }
        LaunchedEffect(chatState.livePhase, onListeningChanged) {
            onListeningChanged(chatState.livePhase != LivePhase.IDLE)
        }
        DisposableEffect(onListeningChanged) {
            onDispose { onListeningChanged(false) }
        }
        LaunchedEffect(store, limitsState.requiresAcknowledgement, deviceCheckState.requiresCheck) {
            if (!deviceCheckState.requiresCheck && !limitsState.requiresAcknowledgement) {
                store.dispatch(ChatIntent.Start)
            }
        }
        val onOpenLimits = { limitsStore.dispatch(LimitsIntent.Open) }
        val sheetHost: @Composable () -> Unit = {
            if (!deviceCheckState.requiresCheck && limitsState.open) {
                if (limitsState.showLicenses) {
                    LicensesSheet(
                        licenses = limitsState.licenses,
                        onClose = { limitsStore.dispatch(LimitsIntent.CloseLicenses) }
                    )
                } else {
                    LimitsSheet(
                        requiresAcknowledgement = limitsState.requiresAcknowledgement,
                        safeguards = limitsState.safeguards,
                        onOpenLicenses = { limitsStore.dispatch(LimitsIntent.OpenLicenses) },
                        onAcknowledge = { limitsStore.dispatch(LimitsIntent.Acknowledge) },
                        onDismiss = { limitsStore.dispatch(LimitsIntent.Dismiss) }
                    )
                }
            }
        }

        if (deviceCheckState.requiresCheck) {
            DeviceCheckScreen(deviceCheckStore)
        } else when (currentScreen) {
            AppScreen.CHAT -> ChatScreen(
                store = store,
                onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                onOpenNotes = { currentScreen = AppScreen.NOTES },
                onOpenLimits = onOpenLimits,
                sheetHost = sheetHost
            )
            AppScreen.NOTES -> {
                BackHandler { currentScreen = AppScreen.CHAT }
                val notesStore: NotesStore = viewModel { NotesStore(notes) }
                NotesScreen(store = notesStore, onBack = { currentScreen = AppScreen.CHAT })
            }
            AppScreen.SETTINGS -> {
                BackHandler { currentScreen = AppScreen.CHAT }
                SettingsScreen(
                    state = chatState,
                    onIntent = store::dispatch,
                    onBack = { currentScreen = AppScreen.CHAT },
                    appVersion = appVersion,
                    onOpenBenchmark = benchmarkHost?.let {
                        {
                            // The benchmark borrows the engine, so nothing in the chat may be using it.
                            store.dispatch(ChatIntent.ExitLiveMode)
                            store.dispatch(ChatIntent.Stop)
                            benchmarkVisit++
                            currentScreen = AppScreen.BENCHMARK
                        }
                    }
                )
            }
            AppScreen.BENCHMARK -> benchmarkHost?.let { host ->
                val model = Models.GEMMA.first { it.id == chatState.modelId }
                val benchmarkStore: BenchmarkStore = viewModel(key = "benchmark-$benchmarkVisit") {
                    BenchmarkStore(engine, model, repo.modelPath(model), host)
                }
                val benchmarkState by benchmarkStore.state.collectAsState()
                val leave = {
                    if (benchmarkState.phase != BenchmarkPhase.Idle) store.dispatch(ChatIntent.ReloadModel)
                    currentScreen = AppScreen.SETTINGS
                }
                BackHandler { if (!benchmarkState.running) leave() }
                BenchmarkScreen(store = benchmarkStore, onBack = leave)
            }
        }
    }
}
