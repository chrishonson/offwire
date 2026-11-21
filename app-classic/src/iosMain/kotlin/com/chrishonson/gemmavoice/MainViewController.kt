package com.chrishonson.gemmavoice

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.chrishonson.gemmavoice.bench.IosBenchmarkHost
import com.chrishonson.gemmavoice.bench.IosBenchmarkScreen
import com.chrishonson.gemmavoice.devicecheck.InMemoryDeviceCheckCompletionStore
import com.chrishonson.gemmavoice.devicecheck.IosDeviceInfo
import com.chrishonson.gemmavoice.limits.IosAcknowledgementStore
import com.chrishonson.gemmavoice.llm.IosLlmEngine
import com.chrishonson.gemmavoice.llm.LlmBridge
import com.chrishonson.gemmavoice.model.IosModelRepository
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.notes.IosFileNotesRepository
import com.chrishonson.gemmavoice.voice.EnergyVad
import com.chrishonson.gemmavoice.voice.IosAudioStream
import com.chrishonson.gemmavoice.voice.IosMicPermission
import com.chrishonson.gemmavoice.voice.IosSpeechSynthesizer
import com.chrishonson.gemmavoice.voice.IosSpeechToText
import com.chrishonson.gemmavoice.voice.IosVad
import com.chrishonson.gemmavoice.voice.IosVoiceRoute
import com.chrishonson.gemmavoice.voice.SttBridge
import com.chrishonson.gemmavoice.voice.VadBridge
import com.chrishonson.gemmavoice.voice.VoiceConfig
import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalNativeApi::class)
private val isDebugBinary = Platform.isDebugBinary

private const val MILLIS_PER_SECOND = 1000.0

// Each native bridge is optional so the simulator gate and any host without the Swift packages still build.
fun MainViewController(
    bridge: LlmBridge? = null,
    stt: SttBridge? = null,
    vad: VadBridge? = null,
    /** Launched with -bench: times the model instead of starting the app. */
    benchmark: Boolean = false
) = ComposeUIViewController {
    if (benchmark && bridge != null) {
        IosBenchmarkScreen(bridge)
        return@ComposeUIViewController
    }
    val notes = remember { IosFileNotesRepository() }
    val voiceRoute = remember { IosVoiceRoute() }
    // iOS voice processing leaves enough of the reply in the mic for Silero to call it speech.
    val voiceConfig = remember { VoiceConfig(ignoreMicWhileSpeaking = true) }
    App(
        engine = bridge?.let { IosLlmEngine(it, VoiceConfig.DEFAULT_ASSISTANT_NAME, notes = notes) }
            ?: UnsupportedLlmEngine(),
        repo = IosModelRepository(),
        audioStream = IosAudioStream(voiceConfig, voiceRoute),
        synthesizer = IosSpeechSynthesizer(voiceRoute),
        micPermission = IosMicPermission(),
        stt = stt?.let { IosSpeechToText(it) } ?: NoOpSpeechToText(),
        vad = vad?.let { IosVad(it) } ?: EnergyVad(),
        config = voiceConfig,
        settings = IosSettingsStore(),
        notes = notes,
        acknowledgementStore = IosAcknowledgementStore(),
        deviceInfo = IosDeviceInfo(),
        deviceCheckCompletionStore = InMemoryDeviceCheckCompletionStore(),
        appConfig = AppConfig(clonedVoice = false, renameAssistant = true),
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "",
        networkActivity = NetworkActivity(now = { (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong() }),
        benchmarkHost = if (isDebugBinary && bridge != null) IosBenchmarkHost() else null
    )
}
