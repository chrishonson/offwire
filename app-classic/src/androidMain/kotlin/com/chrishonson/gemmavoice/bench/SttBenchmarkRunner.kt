package com.chrishonson.gemmavoice.bench

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.chrishonson.gemmavoice.core.BuildConfig
import com.chrishonson.gemmavoice.chat.VOICE_INSTRUCTION
import com.chrishonson.gemmavoice.llm.LiteRtLlmEngine
import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.llm.LlmPrompt
import com.chrishonson.gemmavoice.model.AndroidModelRepository
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.net.NetworkClient
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.chrishonson.gemmavoice.voice.WavEncoder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val LOG_TAG = "GemmaBench"
private const val SAMPLE_RATE_HZ = 16000
private const val FEATURE_DIM = 80
private const val NUM_THREADS = 2
private const val CPU_PROVIDER = "cpu"
private const val NEMO_TRANSDUCER_MODEL_TYPE = "nemo_transducer"
private const val PCM16_MAX = 32768f
private const val BYTES_PER_SAMPLE = 2
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val PERCENT_MULTIPLIER = 100

class SttBenchmarkRunner(
    private val context: Context,
    private val repo: AndroidModelRepository = AndroidModelRepository(
        context,
        NetworkClient(NetworkActivity(System::currentTimeMillis))
    ),
    private val engineFactory: () -> LlmEngine = { LiteRtLlmEngine(context.cacheDir.path, VoiceConfig().assistantName) }
) {

    @Suppress("TooGenericExceptionCaught")
    suspend fun runBenchmark(
        recDir: File,
        sttDir: File,
        resultsFile: File,
        onProgress: (String) -> Unit = {}
    ): BenchmarkReport = withContext(Dispatchers.Default) {
        onProgress("Initializing benchmark...")
        Log.i(LOG_TAG, "Starting STT benchmark on ${Build.MANUFACTURER} ${Build.MODEL}")

        val engine = engineFactory()
        val gemmaPssKb = try {
            loadGemma(engine, onProgress)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to load Gemma: ${e.message}")
            null
        }

        val candidateResults = runAllCandidates(sttDir, recDir, onProgress)

        val audioVsTextResults = if (gemmaPssKb != null) {
            runAudioVsTextBenchmark(engine, recDir, onProgress)
        } else {
            Log.w(LOG_TAG, "Skipping Gemma audio-vs-text comparison: Gemma was not loaded")
            emptyList()
        }

        engine.close()

        val report = BenchmarkReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            gemmaPssKb = gemmaPssKb,
            candidates = candidateResults,
            audioVsText = audioVsTextResults
        )

        resultsFile.parentFile?.mkdirs()
        resultsFile.writeText(report.toJson())
        Log.i(LOG_TAG, "Benchmark complete. Results saved to ${resultsFile.absolutePath}")
        onProgress("Benchmark complete.")
        report
    }

    private suspend fun loadGemma(engine: LlmEngine, onProgress: (String) -> Unit): Long? {
        val modelPath = repo.modelPath(Models.GEMMA_4_E2B)
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(LOG_TAG, "Gemma 4 E2B model missing at $modelPath; skipping Gemma load")
            return null
        }
        onProgress("Loading Gemma 4 E2B...")
        Log.i(LOG_TAG, "Loading Gemma 4 E2B from $modelPath")
        engine.load(modelPath)
        val pssKb = Debug.getPss()
        Log.i(LOG_TAG, "Loaded Gemma 4 E2B. Process PSS: ${pssKb}KB")
        return pssKb
    }

    @Suppress("TooGenericExceptionCaught")
    private fun runAllCandidates(
        sttDir: File,
        recDir: File,
        onProgress: (String) -> Unit
    ): List<CandidateResult> {
        val results = mutableListOf<CandidateResult>()
        for (spec in CANDIDATE_SPECS) {
            val candDir = File(sttDir, spec.id)
            val missing = spec.requiredFiles.filter { !File(candDir, it).exists() }
            if (missing.isNotEmpty()) {
                Log.i(LOG_TAG, "Candidate ${spec.id} skipped; missing files in ${candDir.path}: $missing")
                continue
            }
            try {
                val candidateResult = evaluateCandidate(spec, candDir, recDir, onProgress)
                results.add(candidateResult)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error evaluating candidate ${spec.id}", e)
            }
        }
        return results
    }

    private fun evaluateCandidate(
        spec: CandidateSpec,
        candDir: File,
        recDir: File,
        onProgress: (String) -> Unit
    ): CandidateResult {
        onProgress("Benchmarking ${spec.id}...")
        Log.i(LOG_TAG, "Starting evaluation for ${spec.id}")
        val pssBefore = Debug.getPss()
        val loadStart = SystemClock.elapsedRealtime()
        val recognizer = createRecognizer(spec, candDir)
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val pssAfter = Debug.getPss()
        val pssDeltaKb = (pssAfter - pssBefore).coerceAtLeast(0L)
        Log.i(LOG_TAG, "${spec.id} loaded in ${loadMs}ms, PSS delta: ${pssDeltaKb}KB")

        return try {
            warmupRecognizer(recognizer, recDir, spec.id)
            val (sentenceResults, pairs, timings) = transcribeAllSentences(recognizer, recDir, spec.id, onProgress)
            val meanMs = WerCalculator.calculateMean(timings)
            val p90Ms = WerCalculator.calculateP90(timings)
            val maxMs = WerCalculator.calculateMax(timings)
            val overallWer = WerCalculator.overallWer(pairs)

            val werPercent = (overallWer * PERCENT_MULTIPLIER)
            Log.i(LOG_TAG, "${spec.id} overallWer=${overallWer} (${werPercent}%), mean=${meanMs}ms, p90=${p90Ms}ms")

            CandidateResult(
                candidate = spec.id,
                loadMs = loadMs,
                pssDeltaKb = pssDeltaKb,
                meanMs = meanMs,
                p90Ms = p90Ms,
                maxMs = maxMs,
                overallWer = overallWer,
                perSentence = sentenceResults
            )
        } finally {
            recognizer.release()
        }
    }

    private fun warmupRecognizer(recognizer: OfflineRecognizer, recDir: File, candidateId: String) {
        val warmupWav = File(recDir, formatRecordingFileName(1))
        if (warmupWav.exists()) {
            transcribeWav(recognizer, warmupWav)
            Log.i(LOG_TAG, "$candidateId warmup completed with ${warmupWav.name}")
        } else {
            Log.w(LOG_TAG, "$candidateId warmup skipped: ${warmupWav.path} missing")
        }
    }

    private fun transcribeAllSentences(
        recognizer: OfflineRecognizer,
        recDir: File,
        candidateId: String,
        onProgress: (String) -> Unit
    ): Triple<List<SentenceResult>, List<Pair<String, String>>, List<Long>> {
        val sentenceResults = mutableListOf<SentenceResult>()
        val pairs = mutableListOf<Pair<String, String>>()
        val timings = mutableListOf<Long>()

        for (i in 1..REFERENCE_SENTENCES.size) {
            onProgress("$candidateId: transcribing $i of ${REFERENCE_SENTENCES.size}...")
            val wavFile = File(recDir, formatRecordingFileName(i))
            val refText = REFERENCE_SENTENCES[i - 1]
            if (!wavFile.exists()) {
                Log.w(LOG_TAG, "$candidateId sentence $i skipped: ${wavFile.path} missing")
                continue
            }
            val startMs = SystemClock.elapsedRealtime()
            val hypothesis = transcribeWav(recognizer, wavFile)
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            val wer = WerCalculator.sentenceWer(refText, hypothesis)

            sentenceResults.add(SentenceResult(index = i, ms = elapsedMs, text = hypothesis, wer = wer))
            pairs.add(refText to hypothesis)
            timings.add(elapsedMs)

            Log.i(LOG_TAG, "$candidateId sentence $i: ${elapsedMs}ms, WER=$wer")
            if (BuildConfig.DEBUG && Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "$candidateId sentence $i transcript: $hypothesis")
            }
        }
        return Triple(sentenceResults, pairs, timings)
    }

    private suspend fun runAudioVsTextBenchmark(
        engine: LlmEngine,
        recDir: File,
        onProgress: (String) -> Unit
    ): List<AudioVsTextTiming> {
        val results = mutableListOf<AudioVsTextTiming>()
        for (index in AUDIO_VS_TEXT_SENTENCE_INDICES) {
            onProgress("Measuring Gemma audio vs text for sentence $index...")
            val wavFile = File(recDir, formatRecordingFileName(index))
            val refText = REFERENCE_SENTENCES[index - 1]
            if (!wavFile.exists()) {
                Log.w(LOG_TAG, "Sentence $index audio-vs-text skipped: ${wavFile.path} missing")
                continue
            }

            // Audio prompt
            engine.resetConversation()
            val startAudio = SystemClock.elapsedRealtime()
            var audioFirstEmissionMs: Long? = null
            engine.generate(LlmPrompt.Audio(wavFile.readBytes(), VOICE_INSTRUCTION)).collect {
                if (audioFirstEmissionMs == null) {
                    audioFirstEmissionMs = SystemClock.elapsedRealtime() - startAudio
                }
            }
            val totalAudioMs = SystemClock.elapsedRealtime() - startAudio
            val audioFirst = audioFirstEmissionMs ?: totalAudioMs

            // Text prompt
            engine.resetConversation()
            val startText = SystemClock.elapsedRealtime()
            var textFirstEmissionMs: Long? = null
            engine.generate(LlmPrompt.Text(refText)).collect {
                if (textFirstEmissionMs == null) {
                    textFirstEmissionMs = SystemClock.elapsedRealtime() - startText
                }
            }
            val totalTextMs = SystemClock.elapsedRealtime() - startText
            val textFirst = textFirstEmissionMs ?: totalTextMs

            Log.i(
                LOG_TAG,
                "Sentence $index audio: first=${audioFirst}ms, total=${totalAudioMs}ms; " +
                    "text: first=${textFirst}ms, total=${totalTextMs}ms"
            )

            results.add(
                AudioVsTextTiming(
                    index = index,
                    audioFirstEmissionMs = audioFirst,
                    audioTotalMs = totalAudioMs,
                    textFirstEmissionMs = textFirst,
                    textTotalMs = totalTextMs
                )
            )
        }
        return results
    }

    private fun createRecognizer(spec: CandidateSpec, candDir: File): OfflineRecognizer {
        val tokensPath = File(candDir, "tokens.txt").absolutePath
        val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = FEATURE_DIM)

        val modelConfig = when (spec.type) {
            CandidateType.MOONSHINE -> {
                val moonshine = OfflineMoonshineModelConfig(
                    encoder = File(candDir, "encoder_model.ort").absolutePath,
                    mergedDecoder = File(candDir, "decoder_model_merged.ort").absolutePath
                )
                OfflineModelConfig(
                    moonshine = moonshine,
                    tokens = tokensPath,
                    numThreads = NUM_THREADS,
                    debug = false,
                    provider = CPU_PROVIDER
                )
            }
            CandidateType.NEMO_TRANSDUCER -> {
                val transducer = OfflineTransducerModelConfig(
                    encoder = File(candDir, "encoder.int8.onnx").absolutePath,
                    decoder = File(candDir, "decoder.int8.onnx").absolutePath,
                    joiner = File(candDir, "joiner.int8.onnx").absolutePath
                )
                OfflineModelConfig(
                    transducer = transducer,
                    tokens = tokensPath,
                    numThreads = NUM_THREADS,
                    debug = false,
                    provider = CPU_PROVIDER,
                    modelType = NEMO_TRANSDUCER_MODEL_TYPE
                )
            }
        }
        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig
        )
        return OfflineRecognizer(config = recognizerConfig)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun transcribeWav(recognizer: OfflineRecognizer, wavFile: File): String {
        val waveData = try {
            WaveReader.readWave(wavFile.absolutePath)
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: Exception) {
            null
        }

        val samples = waveData?.samples ?: decodeWavToFloats(wavFile.readBytes())
        val sampleRate = waveData?.sampleRate ?: SAMPLE_RATE_HZ

        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun decodeWavToFloats(wavBytes: ByteArray): FloatArray {
        if (wavBytes.size <= WavEncoder.HEADER_SIZE) return FloatArray(0)
        val pcmLength = wavBytes.size - WavEncoder.HEADER_SIZE
        val sampleCount = pcmLength / BYTES_PER_SAMPLE
        return FloatArray(sampleCount) { i ->
            val lo = wavBytes[WavEncoder.HEADER_SIZE + BYTES_PER_SAMPLE * i].toInt() and BYTE_MASK
            val hi = wavBytes[WavEncoder.HEADER_SIZE + BYTES_PER_SAMPLE * i + 1].toInt()
            ((hi shl BITS_PER_BYTE) or lo).toShort() / PCM16_MAX
        }
    }
}
