package com.chrishonson.gemmavoice.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.chrishonson.gemmavoice.core.BuildConfig
import java.io.File
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val LOG_TAG = "GemmaVoice"
private const val ENCODER_ASSET = "parakeet/encoder.int8.onnx"
private const val DECODER_ASSET = "parakeet/decoder.int8.onnx"
private const val JOINER_ASSET = "parakeet/joiner.int8.onnx"
private const val TOKENS_ASSET = "parakeet/tokens.txt"
private const val MODEL_TYPE = "nemo_transducer"
private const val FEATURE_DIM = 80
private const val NUM_THREADS = 2
private const val PCM16_MAX = 32768f
private const val BYTES_PER_SAMPLE = 2
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val DEBUG_CLIPS_DIR = "debug/live"
private const val DEBUG_CLIPS_KEPT = 30

class ParakeetSpeechToText(context: Context) : SpeechToText {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    override suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val startMs = SystemClock.elapsedRealtime()
                val activeRecognizer = recognizer ?: createRecognizer(sampleRate).also { recognizer = it }
                val stream = activeRecognizer.createStream()
                try {
                    stream.acceptWaveform(pcm16ToFloats(pcm16), sampleRate)
                    activeRecognizer.decode(stream)
                    val text = activeRecognizer.getResult(stream).text.trim()
                    val elapsedMs = SystemClock.elapsedRealtime() - startMs
                    Log.i(LOG_TAG, "Transcribed ${pcm16.size} PCM bytes in ${elapsedMs}ms")
                    if (BuildConfig.DEBUG && Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                        Log.d(LOG_TAG, "Transcript: $text")
                        saveDebugClip(pcm16, sampleRate, text)
                    }
                    text
                } finally {
                    stream.release()
                }
            }
        }

    // Debug builds only, opted in with the same log property; keeps the last clips for replay.
    private fun saveDebugClip(pcm16: ByteArray, sampleRate: Int, text: String) {
        val dir = appContext.getExternalFilesDir(DEBUG_CLIPS_DIR) ?: return
        val name = "clip-${System.currentTimeMillis()}"
        File(dir, "$name.wav").writeBytes(WavEncoder.encodePcm16Mono(pcm = pcm16, sampleRate = sampleRate))
        File(dir, "$name.txt").writeText(text)
        dir.listFiles { file -> file.extension == "wav" }
            ?.sortedByDescending { it.name }
            ?.drop(DEBUG_CLIPS_KEPT)
            ?.forEach { stale ->
                stale.delete()
                File(dir, "${stale.nameWithoutExtension}.txt").delete()
            }
    }

    fun close() {
        recognizer?.release()
        recognizer = null
    }

    private fun createRecognizer(sampleRate: Int): OfflineRecognizer {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = ENCODER_ASSET,
                    decoder = DECODER_ASSET,
                    joiner = JOINER_ASSET
                ),
                tokens = TOKENS_ASSET,
                modelType = MODEL_TYPE,
                numThreads = NUM_THREADS,
                debug = false,
                provider = "cpu"
            )
        )
        return OfflineRecognizer(appContext.assets, config)
    }
}

private fun pcm16ToFloats(pcm16: ByteArray): FloatArray {
    val samples = pcm16.size / BYTES_PER_SAMPLE
    return FloatArray(samples) { i ->
        val lo = pcm16[BYTES_PER_SAMPLE * i].toInt() and BYTE_MASK
        val hi = pcm16[BYTES_PER_SAMPLE * i + 1].toInt()
        ((hi shl BITS_PER_BYTE) or lo).toShort() / PCM16_MAX
    }
}
