package com.chrishonson.gemmavoice.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.chrishonson.gemmavoice.core.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

private const val UTTERANCE_PREFIX = "gemma_reply_"
private const val LOG_TAG = "GemmaVoice"
private const val STOP_CALLER_FRAMES = 4
private const val STOP_STACK_SKIP = 3

class AndroidSpeechSynthesizer(
    context: Context,
    private val callAudio: CommunicationAudio? = null
) : SpeechSynthesizer {
    private var currentUsage: Int? = null
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _offlineVoiceAvailable = MutableStateFlow(true)
    override val offlineVoiceAvailable: StateFlow<Boolean> = _offlineVoiceAvailable.asStateFlow()

    private val utteranceCounter = AtomicInteger(0)

    // Handed to the engine but not finished, oldest first, so a route change can say them again on the new stream.
    private val unfinished = LinkedHashMap<String, String>()
    private val pendingQueue = mutableListOf<Pair<String, Int>>()
    private var isReady: Boolean = false
    private var speechRate: Float = 1.0f

    init {
        callAudio?.addRouteListener(::followRoute)
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext ?: context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            configureTts()
        } else {
            _offlineVoiceAvailable.value = false
            pendingQueue.clear()
            synchronized(unfinished) { unfinished.clear() }
            _isSpeaking.value = false
        }
    }

    override fun speak(text: String) {
        val cleaned = speakableText(text)
        if (cleaned.isBlank()) return
        if (!_offlineVoiceAvailable.value) {
            pendingQueue.clear()
            synchronized(unfinished) { unfinished.clear() }
            _isSpeaking.value = false
            return
        }
        _isSpeaking.value = true
        pendingQueue.clear()
        if (isReady) {
            applyUsageForAudioMode()
            say(cleaned, TextToSpeech.QUEUE_FLUSH)
        } else {
            pendingQueue.add(cleaned to TextToSpeech.QUEUE_FLUSH)
        }
    }

    override fun enqueue(text: String) {
        val cleaned = speakableText(text)
        if (cleaned.isBlank()) return
        if (!_offlineVoiceAvailable.value) {
            pendingQueue.clear()
            synchronized(unfinished) { unfinished.clear() }
            _isSpeaking.value = false
            return
        }
        Log.i(LOG_TAG, "TTS enqueue ${cleaned.length} chars")
        _isSpeaking.value = true
        if (isReady) {
            applyUsageForAudioMode()
            say(cleaned, TextToSpeech.QUEUE_ADD)
        } else {
            pendingQueue.add(cleaned to TextToSpeech.QUEUE_ADD)
        }
    }

    override fun stop() {
        val caller = Thread.currentThread().stackTrace.drop(STOP_STACK_SKIP).take(STOP_CALLER_FRAMES)
            .joinToString { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        Log.i(LOG_TAG, "TTS stop() called from $caller")
        pendingQueue.clear()
        synchronized(unfinished) { unfinished.clear() }
        _isSpeaking.value = false
        tts?.stop()
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun shutdown() {
        pendingQueue.clear()
        synchronized(unfinished) { unfinished.clear() }
        _isSpeaking.value = false
        isReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun configureTts() {
        val engine = tts ?: return
        val bestVoice = selectBestVoice(engine)
        if (bestVoice == null) {
            _offlineVoiceAvailable.value = false
            pendingQueue.clear()
            synchronized(unfinished) { unfinished.clear() }
            _isSpeaking.value = false
            return
        }
        engine.voice = bestVoice
        engine.setSpeechRate(speechRate)
        _offlineVoiceAvailable.value = true
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.i(LOG_TAG, "TTS start $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.i(LOG_TAG, "TTS done $utteranceId")
                    onUtteranceFinished(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onUtteranceFinished(utteranceId)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(LOG_TAG, "TTS error $utteranceId code=$errorCode")
                    onUtteranceFinished(utteranceId)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.i(LOG_TAG, "TTS stopped $utteranceId interrupted=$interrupted")
                    onUtteranceFinished(utteranceId)
                }
            }
        )
        isReady = true
        applyUsageForAudioMode()
        val queued = pendingQueue.toList()
        pendingQueue.clear()
        for ((text, mode) in queued) {
            say(text, mode)
        }
    }

    // In live voice speech must be call audio so the echo canceller can subtract it. Outside live voice Android
    // routes call audio to the earpiece, so plain replies play as media on the loudspeaker instead, a stream the
    // volume buttons can reach (the assistant stream has no public volume control).
    private fun applyUsageForAudioMode() {
        val engine = tts ?: return
        val usage = if (callAudio?.isActive == true) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        if (usage == currentUsage) return
        currentUsage = usage
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    // The engine default is often a low-quality legacy voice; pick the best offline one for the user's language.
    private fun selectBestVoice(engine: TextToSpeech): Voice? {
        val locale = Locale.getDefault()
        val candidates = engine.voices.orEmpty().filter { voice ->
            voice.locale.language == locale.language &&
                !voice.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
        }
        val best = candidates.maxWithOrNull(
            compareBy<Voice>({ it.quality }, { it.locale.country == locale.country }, { it.latency.unaryMinus() })
        )
        Log.i(LOG_TAG, "TTS voices: ${candidates.joinToString { "${it.name}(q=${it.quality})" }}; chose ${best?.name}")
        return best
    }

    private fun say(text: String, mode: Int) = synchronized(unfinished) {
        val id = nextUtteranceId()
        if (mode == TextToSpeech.QUEUE_FLUSH) unfinished.clear()
        unfinished[id] = text
        // Debug builds only; opt in with adb shell setprop log.tag.GemmaVoice DEBUG.
        if (BuildConfig.DEBUG && Log.isLoggable(LOG_TAG, Log.DEBUG)) Log.d(LOG_TAG, "TTS queued $id: $text")
        tts?.speak(text, mode, null, id)
    }

    // Mute ends call audio mid-reply, and the engine keeps queued speech on the stream it was given, which Android
    // then plays on the earpiece. Say the rest again on the stream that matches the new route; unmute is the reverse.
    private fun followRoute() = synchronized(unfinished) {
        if (!isReady) return@synchronized
        applyUsageForAudioMode()
        if (unfinished.isEmpty()) return@synchronized
        val rest = unfinished.values.toList()
        Log.i(LOG_TAG, "TTS moving ${rest.size} utterances to the new audio route")
        unfinished.clear()
        tts?.stop()
        rest.forEach { say(it, TextToSpeech.QUEUE_ADD) }
    }

    // Ids not in the map were flushed or moved; their late callbacks must not end the current speech.
    private fun onUtteranceFinished(utteranceId: String?) {
        val remaining = synchronized(unfinished) {
            if (unfinished.remove(utteranceId) == null) return
            unfinished.size
        }
        if (remaining == 0) {
            _isSpeaking.value = false
        }
    }

    private fun nextUtteranceId(): String =
        "$UTTERANCE_PREFIX${utteranceCounter.incrementAndGet()}"
}
