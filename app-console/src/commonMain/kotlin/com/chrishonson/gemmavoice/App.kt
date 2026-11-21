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
import com.chrishonson.gemmavoice.setup.SetupScreen
import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.EnergyVad
import com.chrishonson.gemmavoice.voice.InMemorySettingsStore
import com.chrishonson.gemmavoice.voice.MicPermission
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.SpeechToText
import com.chrishonson.gemmavoice.voice.VoiceActivityDetector
import com.chrishonson.gemmavoice.voice.VoiceConfig

internal enum class AppScreen { CHAT, SETUP }

internal val AppScreenSaver: Saver<AppScreen, String> = Saver(
    save = { it.name },
    restore = { AppScreen.valueOf(it) }
)

internal enum class NavigationEvent { OPEN_SETUP, BACK_TO_CHAT }

internal fun navigate(current: AppScreen, event: NavigationEvent): AppScreen = when (event) {
    NavigationEvent.OPEN_SETUP -> if (current == AppScreen.CHAT) AppScreen.SETUP else current
    NavigationEvent.BACK_TO_CHAT -> AppScreen.CHAT
}

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
    acknowledgementStore: AcknowledgementStore = InMemoryAcknowledgementStore(),
    deviceInfo: DeviceInfo = UnknownDeviceInfo,
    deviceCheckCompletionStore: DeviceCheckCompletionStore = InMemoryDeviceCheckCompletionStore(),
    appConfig: AppConfig,
    networkActivity: NetworkActivity = NetworkActivity(now = { 0L }),
    resourceMonitor: ResourceSampler = ResourceMonitor(),
    appVersion: String = "",
    onListeningChanged: (Boolean) -> Unit = {},
    onStoreReady: (ChatStore) -> Unit = {}
) {
    var currentScreen by rememberSaveable(stateSaver = AppScreenSaver) { mutableStateOf(AppScreen.CHAT) }

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
                wakeWordDefault = true,
                autoListen = true,
                voiceSpec = if (appConfig.clonedVoice) Models.POCKET_TTS else null,
                network = network,
                networkActivity = networkActivity,
                resourceMonitor = resourceMonitor
            )
        }
        val limitsStore: LimitsStore = viewModel {
            LimitsStore(
                acknowledgementStore = acknowledgementStore,
                featureConfig = DEFAULT_FEATURE_CONFIG
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
        AppForegroundEffect {
            store.dispatch(ChatIntent.AppForegrounded)
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
                onOpenSetup = { currentScreen = navigate(currentScreen, NavigationEvent.OPEN_SETUP) },
                onOpenLimits = onOpenLimits,
                sheetHost = sheetHost
            )
            AppScreen.SETUP -> {
                BackHandler {
                    currentScreen = navigate(currentScreen, NavigationEvent.BACK_TO_CHAT)
                }
                SetupScreen(
                    state = chatState,
                    onIntent = { store.dispatch(it) },
                    onBack = { currentScreen = navigate(currentScreen, NavigationEvent.BACK_TO_CHAT) },
                    onOpenLimits = onOpenLimits,
                    appVersion = appVersion,
                    sheetHost = sheetHost
                )
            }
        }
    }
}
