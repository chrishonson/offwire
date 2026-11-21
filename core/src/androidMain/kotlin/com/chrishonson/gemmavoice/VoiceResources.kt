package com.chrishonson.gemmavoice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chrishonson.gemmavoice.devicecheck.AndroidDeviceCheckCompletionStore
import com.chrishonson.gemmavoice.devicecheck.AndroidDeviceInfo
import com.chrishonson.gemmavoice.devicecheck.DeviceCheckCompletionStore
import com.chrishonson.gemmavoice.devicecheck.DeviceInfo
import com.chrishonson.gemmavoice.limits.AcknowledgementStore
import com.chrishonson.gemmavoice.limits.AndroidAcknowledgementStore
import com.chrishonson.gemmavoice.security.KeystoreVaultCipher
import com.chrishonson.gemmavoice.notes.EncryptedFileNotesRepository
import com.chrishonson.gemmavoice.notes.NotesRepository
import com.chrishonson.gemmavoice.llm.LiteRtLlmEngine
import com.chrishonson.gemmavoice.model.AndroidModelRepository
import com.chrishonson.gemmavoice.model.AndroidNetworkMonitor
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.net.FileNetworkLogSink
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.net.NetworkClient
import com.chrishonson.gemmavoice.resource.ResourceMonitor
import com.chrishonson.gemmavoice.router.AndroidIntentRouter
import com.chrishonson.gemmavoice.voice.ActivityMicPermission
import com.chrishonson.gemmavoice.voice.AndroidAudioStream
import com.chrishonson.gemmavoice.voice.AndroidSettingsStore
import com.chrishonson.gemmavoice.voice.AndroidSpeechSynthesizer
import com.chrishonson.gemmavoice.voice.CommunicationAudio
import com.chrishonson.gemmavoice.voice.MicPermission
import com.chrishonson.gemmavoice.voice.ParakeetSpeechToText
import com.chrishonson.gemmavoice.voice.PocketSpeechSynthesizer
import com.chrishonson.gemmavoice.voice.ReplyVolume
import com.chrishonson.gemmavoice.voice.SettingsStore
import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import com.chrishonson.gemmavoice.voice.SileroVad
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.chrishonson.gemmavoice.voice.startingAssistantName
import java.io.File

// Lives as long as the ChatStore, not the Activity, so rotation keeps the same native sessions the store is using.
class VoiceResources(application: Application, private val appConfig: AppConfig) : AndroidViewModel(application) {
    // Always persistent: renaming off only hides the rename UI, while the wake word, listen-at-start and
    // resource meter switches must survive a restart (card 217; the console had an in-memory store before).
    val settings: SettingsStore = AndroidSettingsStore(application)

    val acknowledgementStore: AcknowledgementStore = AndroidAcknowledgementStore(application)
    val deviceInfo: DeviceInfo = AndroidDeviceInfo(application)
    val deviceCheckCompletionStore: DeviceCheckCompletionStore = AndroidDeviceCheckCompletionStore(application)

    // Built from the name, not copied, so the default wake word follows it.
    val config = VoiceConfig(assistantName = startingAssistantName(settings, VoiceConfig.DEFAULT_ASSISTANT_NAME))
    private val callAudio = CommunicationAudio(application)
    val networkActivity = NetworkActivity(
        now = System::currentTimeMillis,
        sink = FileNetworkLogSink(File(application.filesDir, "network-log.jsonl"))
    )
    val networkClient = NetworkClient(networkActivity)
    val repo = AndroidModelRepository(application, networkClient)
    val network = AndroidNetworkMonitor(application)
    val resourceMonitor = ResourceMonitor(application)

    private val systemVoice = AndroidSpeechSynthesizer(application, callAudio)

    // The cloned computer voice once its model is downloaded; the system voice until then, or always when off.
    private val clonedVoice = if (appConfig.clonedVoice) {
        PocketSpeechSynthesizer(
            context = application,
            spec = Models.POCKET_TTS,
            modelDir = File(repo.modelPath(Models.POCKET_TTS)),
            fallback = systemVoice,
            callAudio = callAudio
        )
    } else {
        null
    }
    val synthesizer: SpeechSynthesizer = clonedVoice ?: systemVoice
    val vad = SileroVad(application)
    val stt = ParakeetSpeechToText(application)
    val replyVolume = ReplyVolume(application, callAudio)
    val audioStream = AndroidAudioStream(callAudio = callAudio, onStarted = replyVolume::matchCallToMedia)
    val notes: NotesRepository =
        EncryptedFileNotesRepository(File(application.filesDir, "notes.bin"), KeystoreVaultCipher())
    // Loaded with Gemma, off the main thread; AppConfig.intentRouter = false keeps save_note on every turn.
    private val intentRouter = if (appConfig.intentRouter) AndroidIntentRouter(application) else null
    val engine = LiteRtLlmEngine(
        application.cacheDir.path,
        config.assistantName,
        backend = config.llmBackend,
        notes = notes,
        router = intentRouter
    )

    // The permission launcher belongs to whichever Activity instance is current.
    var activityPermission: ActivityMicPermission? = null
    val micPermission = object : MicPermission {
        override suspend fun ensureGranted(): Boolean = checkNotNull(activityPermission).ensureGranted()
    }

    // The ChatStore, created after this, cancels its mic and generation and closes the engine in its own onCleared.
    override fun onCleared() {
        clonedVoice?.shutdown() ?: systemVoice.shutdown()
        vad.close()
        stt.close()
        intentRouter?.close()
    }

    companion object {
        fun factory(application: Application, appConfig: AppConfig): ViewModelProvider.Factory =
            viewModelFactory { initializer { VoiceResources(application, appConfig) } }
    }
}
