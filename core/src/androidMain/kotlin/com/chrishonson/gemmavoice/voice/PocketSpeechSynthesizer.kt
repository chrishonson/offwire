package com.chrishonson.gemmavoice.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.chrishonson.gemmavoice.model.ModelSpec
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val LOG_TAG = "GemmaVoice"
private const val CONDITIONING_FILE = "voice-conditioning.onnx"
private const val WARM_UP_TEXT = "Ready."
private const val NUM_THREADS = 4
private const val NUM_STEPS = 1
private const val TEMPERATURE = "0.7"
private const val CHUNK_SIZE = "5"
private const val PLACEHOLDER_REFERENCE_SAMPLES = 2400
private const val PLACEHOLDER_REFERENCE_RATE = 24000
private const val TRACK_BUFFER_SECONDS = 2
// The Pixel 9 Pro's loudspeaker path swallows the first 1.5 to 2 s after an output stream starts, so one-second
// replies vanished entirely (card 213, measured 2026-11-02). The track therefore plays silence from the moment a
// reply starts generating until a while after speech ends, and speech never starts on a cold path.
private const val WARM_BEFORE_SPEECH_MS = 20_000L
private const val WARM_AFTER_SPEECH_MS = 8_000L
private const val WARM_CHUNK_MS = 100L
// At most this much silence sits in the buffer ahead of speech, so warming adds no audible delay.
private const val WARM_LEAD_MS = 200L
private const val DEFAULT_SAMPLE_RATE = 24_000
private const val BYTES_PER_FLOAT = 4
private const val DRAIN_POLL_MS = 20L
private const val DRAIN_GRACE_MS = 500L
private const val LEVEL_POLL_MS = 33L
private const val MS_PER_SECOND = 1000L
private const val FRAME_MASK = 0xFFFFFFFFL
private const val CONTINUE = 1
private const val STOP = 0

/**
 * Speaks in a baked voice with Pocket TTS (sherpa-onnx), streaming each clause to an [AudioTrack]
 * as it is generated. Until the model files in [modelDir] exist and are loaded, [fallback] speaks instead.
 *
 * The voice is baked in rather than cloned on the phone: the reference encoder in the sherpa export was traced
 * with a one second input and returns the first 13 frames of any clip, so cloning on device loses the voice.
 * The asset from [conditioningAsset] is a stand-in encoder that ignores its audio input and emits the conditioning
 * `scripts/bake-voice-conditioning.py` computes from the full reference clip ([frames, 1024]), which the rest of the
 * model consumes unchanged. The reference audio passed at generation time is therefore a silent placeholder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class PocketSpeechSynthesizer(
    context: Context,
    private val spec: ModelSpec,
    private val modelDir: File,
    private val fallback: AndroidSpeechSynthesizer,
    private val callAudio: CommunicationAudio? = null
) : SpeechSynthesizer {
    private val appContext = context.applicationContext ?: context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Generation blocks a thread for seconds, so it gets its own instead of one of the shared Default workers.
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "pocket-tts") }
    private val worker = executor.asCoroutineDispatcher()
    private val queue = Channel<Utterance>(Channel.UNLIMITED)
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> =
        combine(_isSpeaking, fallback.isSpeaking) { own, other -> own || other }
            .stateIn(scope, SharingStarted.Eagerly, false)

    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    override val offlineVoiceAvailable: StateFlow<Boolean> =
        combine(_modelLoaded, fallback.offlineVoiceAvailable) { loaded, fallbackAvailable ->
            loaded || fallbackAvailable
        }.stateIn(scope, SharingStarted.Eagerly, fallback.offlineVoiceAvailable.value)

    // Bumped by stop(): queued utterances and in-flight generation carrying an older value are dropped.
    private val generation = AtomicInteger(0)
    private val loading = AtomicBoolean(false)

    @Volatile
    private var voice: LoadedVoice? = null

    @Volatile
    private var track: AudioTrack? = null
    private var trackUsage: Int? = null
    private var framesWritten = 0L
    // Guarded by its own lock: the speech worker holds trackLock through blocking writes, and neither the level
    // poller nor stop() may wait behind them.
    private val meterLock = Any()
    private var meter = SpeechLevelMeter(DEFAULT_SAMPLE_RATE)

    private fun <T> withMeter(block: SpeechLevelMeter.() -> T): T = synchronized(meterLock) { meter.block() }

    // Frame count at the end of the last utterance: the drain waits for this, not for warm silence written after it.
    @Volatile
    private var speechEndFrame = 0L

    // The warmer and the speech worker share the track; speech holds the lock for a whole utterance.
    private val trackLock = ReentrantLock()
    private val warmer = Executors.newSingleThreadExecutor { Thread(it, "pocket-tts-warm") }
    private val warming = AtomicBoolean(false)

    @Volatile
    private var warmUntil = 0L

    @Volatile
    private var shutDown = false

    private class Utterance(val text: String, val generation: Int)
    private class LoadedVoice(val tts: OfflineTts, val config: GenerationConfig)

    // The sherpa JNI looks up invoke([F)Integer on the callback object. Kotlin 2 compiles lambdas through
    // invokedynamic, whose classes only have the erased invoke(Object), so the callback has to be a real class.
    private class ChunkCallback(private val onChunk: (FloatArray) -> Int) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int = onChunk(samples)
    }

    init {
        loadIfPresent()
        scope.launch(worker) {
            for (utterance in queue) {
                if (utterance.generation != generation.get()) continue
                // A failure inside one utterance must not take the process down with the speech thread.
                runCatching { speakNow(utterance) }.onFailure { Log.e(LOG_TAG, "Pocket TTS failed: ${it.message}") }
                if (queue.isEmpty) finishSpeaking(utterance.generation)
            }
        }
        scope.launch {
            isSpeaking.collectLatest { speaking ->
                if (speaking) {
                    while (true) {
                        val head = track?.let {
                            runCatching { it.playbackHeadPosition.toLong() and FRAME_MASK }.getOrNull()
                        }
                        val level = if (head != null) withMeter { levelAt(head) } else 0f
                        _outputLevel.value = level
                        delay(LEVEL_POLL_MS)
                    }
                } else {
                    _outputLevel.value = 0f
                }
            }
        }
    }

    override fun speak(text: String) {
        stop()
        enqueue(text)
    }

    override fun enqueue(text: String) {
        val cleaned = speakableText(text)
        if (cleaned.isBlank()) return
        if (voice == null) {
            loadIfPresent()
            fallback.enqueue(cleaned)
            return
        }
        _isSpeaking.value = true
        queue.trySend(Utterance(cleaned, generation.get()))
    }

    override fun warmUp() = extendWarm(WARM_BEFORE_SPEECH_MS)

    private fun extendWarm(ms: Long) {
        warmUntil = maxOf(warmUntil, SystemClock.elapsedRealtime() + ms)
        if (!shutDown && warming.compareAndSet(false, true)) warmer.execute { runWarmer() }
    }

    // Writes silence in small non-blocking chunks while the warm window is open, then releases the track.
    private fun runWarmer() {
        try {
            val rate = voice?.tts?.sampleRate() ?: DEFAULT_SAMPLE_RATE
            val silence = FloatArray((rate * WARM_CHUNK_MS / MS_PER_SECOND).toInt())
            while (!shutDown && SystemClock.elapsedRealtime() < warmUntil) {
                writeWarmChunk(rate, silence)
                Thread.sleep(WARM_CHUNK_MS / 2)
            }
            releaseIfIdle()
        } catch (ignored: InterruptedException) {
            // shutdown
        } finally {
            warming.set(false)
            if (!shutDown && SystemClock.elapsedRealtime() < warmUntil) extendWarm(0L)
        }
    }

    // Tops the buffer up only when it is nearly empty: speech appended later waits behind at most WARM_LEAD_MS
    // of silence, and between clauses silence goes in only where the track was about to underrun anyway.
    private fun writeWarmChunk(rate: Int, silence: FloatArray) {
        if (!trackLock.tryLock(WARM_CHUNK_MS, TimeUnit.MILLISECONDS)) return
        try {
            val out = ensureTrack(rate)
            val buffered = framesWritten - (out.playbackHeadPosition.toLong() and FRAME_MASK)
            if (buffered > rate * WARM_LEAD_MS / MS_PER_SECOND) return
            val written = out.write(silence, 0, silence.size, AudioTrack.WRITE_NON_BLOCKING)
            if (written > 0) {
                framesWritten += written
                withMeter { write(if (written == silence.size) silence else silence.copyOf(written)) }
            }
        } finally {
            trackLock.unlock()
        }
    }

    private fun releaseIfIdle() {
        if (shutDown || !trackLock.tryLock()) return
        try {
            if (!_isSpeaking.value && SystemClock.elapsedRealtime() >= warmUntil) releaseTrack()
        } finally {
            trackLock.unlock()
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        _isSpeaking.value = false
        _outputLevel.value = 0f
        // A stop is usually a barge-in with a new reply seconds away: keep the path warm briefly, not 20 s.
        warmUntil = minOf(warmUntil, SystemClock.elapsedRealtime() + WARM_AFTER_SPEECH_MS)
        fallback.stop()
        // Paused and flushed here so barge-in cuts the audio at once; the worker releases the track later.
        track?.let {
            runCatching {
                it.pause()
                it.flush()
                // The flushed frames never play, so the lead and drain counters follow the head from here.
                framesWritten = it.playbackHeadPosition.toLong() and FRAME_MASK
                speechEndFrame = framesWritten
                withMeter { reset(framesWritten) }
            }
        } ?: withMeter { reset(framesWritten) }
    }

    fun shutdown() {
        shutDown = true
        warmUntil = 0L
        warmer.shutdownNow()
        generation.incrementAndGet()
        _outputLevel.value = 0f
        scope.cancel()
        executor.execute {
            releaseTrack()
            voice?.tts?.release()
            voice = null
            _modelLoaded.value = false
        }
        worker.close()
        fallback.shutdown()
    }

    private fun loadIfPresent() {
        if (voice != null || !loading.compareAndSet(false, true)) return
        val present = spec.unpacked.all { file ->
            File(modelDir, file.name).let { it.exists() && it.length() == file.sizeBytes }
        }
        if (!present) {
            loading.set(false)
            return
        }
        scope.launch(worker) {
            try {
                voice = load()
                _modelLoaded.value = true
            } catch (expected: RuntimeException) {
                Log.w(LOG_TAG, "Pocket TTS failed to load, keeping system voice", expected)
            } finally {
                loading.set(false)
            }
        }
    }

    private fun load(): LoadedVoice {
        val startMs = SystemClock.elapsedRealtime()
        fun path(name: String) = File(modelDir, name).absolutePath
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                pocket = OfflineTtsPocketModelConfig(
                    lmFlow = path("lm_flow.int8.onnx"),
                    lmMain = path("lm_main.int8.onnx"),
                    encoder = installConditioning().absolutePath,
                    decoder = path("decoder.int8.onnx"),
                    textConditioner = path("text_conditioner.onnx"),
                    vocabJson = path("vocab.json"),
                    tokenScoresJson = path("token_scores.json")
                ),
                numThreads = NUM_THREADS,
                debug = false,
                provider = "cpu"
            )
        )
        val tts = OfflineTts(null, config)
        val generationConfig = GenerationConfig(
            referenceAudio = FloatArray(PLACEHOLDER_REFERENCE_SAMPLES),
            referenceSampleRate = PLACEHOLDER_REFERENCE_RATE,
            numSteps = NUM_STEPS,
            extra = mapOf("temperature" to TEMPERATURE, "chunk_size" to CHUNK_SIZE)
        )
        // The voice embedding is computed on the first call and cached, so pay for it now, not on the first reply.
        tts.generateWithConfigAndCallback(WARM_UP_TEXT, generationConfig, ChunkCallback { STOP })
        Log.i(LOG_TAG, "Pocket TTS loaded in ${SystemClock.elapsedRealtime() - startMs}ms, ${tts.sampleRate()} Hz")
        return LoadedVoice(tts, generationConfig)
    }

    // sherpa-onnx loads models from file paths, so the bundled stand-in encoder is copied next to the model files.
    // Compared by content: two voices baked from clips of the same length have the same size.
    private fun installConditioning(): File {
        val target = File(modelDir, CONDITIONING_FILE)
        val asset = conditioningAsset(appContext.assets.list(LOCAL_CONDITIONING_DIR))
        val bytes = appContext.assets.open(asset).use { it.readBytes() }
        if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
            target.writeBytes(bytes)
        }
        Log.i(LOG_TAG, "Pocket TTS voice: $asset")
        return target
    }

    // The lock is taken per write, not for the whole utterance, so the warmer keeps the path fed with silence
    // while the model is still producing the first chunk (about a second) and between slow chunks.
    private fun speakNow(utterance: Utterance) {
        val loaded = voice ?: return
        val rate = loaded.tts.sampleRate()
        val (first, wasWarm, startFrame) = trackLock.withLock {
            val warm = track?.playState == AudioTrack.PLAYSTATE_PLAYING
            val created = ensureTrack(rate)
            Triple(created, warm, framesWritten)
        }
        var out = first
        val trimmer = SilenceTrimmer(sampleRate = rate)
        val startMs = SystemClock.elapsedRealtime()
        var firstAudioMs = -1L
        val callback = ChunkCallback { samples ->
            if (utterance.generation != generation.get()) return@ChunkCallback STOP
            for (chunk in trimmer.feed(samples)) {
                if (firstAudioMs < 0) firstAudioMs = SystemClock.elapsedRealtime() - startMs
                out = followRoute(out, rate, utterance.generation)
                write(out, chunk)
            }
            CONTINUE
        }
        loaded.tts.generateWithConfigAndCallback(utterance.text, loaded.config, callback)
        if (utterance.generation == generation.get()) {
            trimmer.flush()?.let { write(out, it) }
            withMeter { flush() }
        }
        // After a stop the warmer may already have released this track; a released AudioTrack throws on any read.
        val (endFrame, underruns) = trackLock.withLock {
            speechEndFrame to if (track === out) runCatching { out.underrunCount }.getOrDefault(-1) else -1
        }
        val totalMs = SystemClock.elapsedRealtime() - startMs
        val audioMs = (endFrame - startFrame) * MS_PER_SECOND / rate
        Log.i(
            LOG_TAG,
            "Pocket TTS ${utterance.text.length} chars: first audio ${firstAudioMs}ms, done ${totalMs}ms, " +
                "${audioMs}ms of audio, $underruns underruns, warm=$wasWarm"
        )
    }

    // Mute ends call audio mid-utterance, and Android then plays a call-audio track on the earpiece. Move the rest of
    // the utterance to a track that matches the new route (the old track's unplayed buffer is dropped, as on a
    // barge-in); unmute is the reverse. The warmer may already have made that switch, so follow its track too.
    private fun followRoute(out: AudioTrack, rate: Int, utteranceGeneration: Int): AudioTrack = trackLock.withLock {
        val moved = track !== out || trackUsage != usageForAudioMode()
        if (moved && utteranceGeneration == generation.get()) ensureTrack(rate) else out
    }

    private fun write(out: AudioTrack, chunk: FloatArray) = trackLock.withLock {
        // The warmer may have replaced the track after a stop; speech for a cancelled generation stops anyway.
        if (track !== out) return@withLock
        out.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
        framesWritten += chunk.size
        speechEndFrame = framesWritten
        withMeter { write(chunk) }
    }

    // Waits for the buffered audio to play out before reporting silence, unless a stop() cut it short.
    private suspend fun finishSpeaking(utteranceGeneration: Int) {
        val out = track
        if (out != null) {
            val rate = out.sampleRate
            val target = speechEndFrame
            val remaining = (target - (out.playbackHeadPosition.toLong() and FRAME_MASK)).coerceAtLeast(0L)
            val deadline = SystemClock.elapsedRealtime() + remaining * MS_PER_SECOND / rate + DRAIN_GRACE_MS
            while (
                generation.get() == utteranceGeneration &&
                (out.playbackHeadPosition.toLong() and FRAME_MASK) < target &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                delay(DRAIN_POLL_MS)
            }
        }
        if (generation.get() == utteranceGeneration) {
            _isSpeaking.value = false
            _outputLevel.value = 0f
        }
        // Keep the path warm for follow-ups; the warmer releases the track when the window closes.
        extendWarm(WARM_AFTER_SPEECH_MS)
    }

    // In live voice speech must be call audio so the echo canceller can subtract it. Outside live voice Android
    // routes call audio to the earpiece, so plain replies play as media on the loudspeaker instead.
    private fun usageForAudioMode() = if (callAudio?.isActive == true) {
        AudioAttributes.USAGE_VOICE_COMMUNICATION
    } else {
        AudioAttributes.USAGE_MEDIA
    }

    private fun ensureTrack(sampleRate: Int): AudioTrack {
        val usage = usageForAudioMode()
        track?.let { existing ->
            if (trackUsage == usage && existing.playState == AudioTrack.PLAYSTATE_PLAYING) return existing
        }
        releaseTrack()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, sampleRate * TRACK_BUFFER_SECONDS * BYTES_PER_FLOAT))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        created.play()
        framesWritten = 0L
        speechEndFrame = 0L
        synchronized(meterLock) { meter = SpeechLevelMeter(sampleRate) }
        trackUsage = usage
        track = created
        return created
    }

    private fun releaseTrack() {
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
        trackUsage = null
        framesWritten = 0L
        withMeter { reset(0L) }
    }
}
