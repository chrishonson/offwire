package com.chrishonson.gemmavoice.bench

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.GemmaVoiceTheme
import com.chrishonson.gemmavoice.llm.LiteRtLlmEngine
import com.chrishonson.gemmavoice.llm.CONTEXT_TOKENS
import com.chrishonson.gemmavoice.llm.LlmBackend
import com.chrishonson.gemmavoice.voice.VoiceConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val LOG_TAG = "GemmaVoiceBench"
private const val DEFAULT_RUNS = 3
private const val DEFAULT_TOP_K = 64
private const val DEFAULT_TOP_P = 0.95
private const val DEFAULT_TEMPERATURE = 1.0
private const val DEFAULT_SEED = 0
private const val PADDING_DP = 24
private const val SYSTEM_INSTRUCTION = "Answer briefly."
private const val IMAGE_PROMPT = "What is this? Answer in one sentence."

private val TEXT_PROMPTS = listOf(
    "What is the capital of France?",
    "In two sentences, why does a GFCI outlet trip?",
    "Give me three tips for reading a furnace data plate."
)

class LlmBenchmarkActivity : ComponentActivity() {

    @Volatile
    private var engine: Engine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val backendParam = (intent.getStringExtra("backend") ?: "cpu").lowercase(Locale.US)
        val runs = intent.getIntExtra("runs", DEFAULT_RUNS)
        // "app" times the app's own engine through the runner the iOS app shares, so the two compare.
        val appHarness = intent.getStringExtra("harness") == "app"
        // Another context size, to measure what it costs; the app always uses CONTEXT_TOKENS.
        val contextTokens = intent.getIntExtra("context_tokens", CONTEXT_TOKENS)

        setContent {
            GemmaVoiceTheme {
                var statusText by remember { mutableStateOf("Loading") }

                LaunchedEffect(Unit) {
                    if (appHarness) {
                        runAppBenchmark(runs, contextTokens) { statusText = it }
                        return@LaunchedEffect
                    }
                    runBenchmark(
                        backendParam = backendParam,
                        runs = runs,
                        onStatus = { statusText = it }
                    )
                }

                BenchmarkScreen(statusText = statusText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.close()
        engine = null
    }

    private suspend fun runAppBenchmark(
        runs: Int,
        contextTokens: Int,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val appEngine = LiteRtLlmEngine(
            cacheDir.path,
            VoiceConfig.DEFAULT_ASSISTANT_NAME,
            backend = LlmBackend.GPU,
            contextTokens = contextTokens
        )
        try {
            val modelPath = File(baseDir, "models/gemma-4-E2B-it.litertlm").absolutePath
            val report = benchmarkEngine(appEngine, modelPath, Build.MODEL, "gpu", runs, onStatus = onStatus)
            val resultsFile = File(baseDir, "bench/llm-app-$contextTokens.json")
            resultsFile.parentFile?.mkdirs()
            resultsFile.writeText(formatBenchmarkReportJson(report))
            Log.i(LOG_TAG, "App benchmark complete. Results written to ${resultsFile.absolutePath}")
            onStatus("Done, wrote ${resultsFile.absolutePath}")
        } finally {
            appEngine.close()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runBenchmark(
        backendParam: String,
        runs: Int,
        onStatus: (String) -> Unit
    ) = withContext(Dispatchers.Default) {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        val modelFile = File(baseDir, "models/gemma-4-E2B-it.litertlm")
        val benchDir = File(baseDir, "bench")
        val resultsFile = File(benchDir, "llm-$backendParam.json")

        if (!modelFile.exists()) {
            val errorMsg = "Model missing at ${modelFile.absolutePath}"
            Log.e(LOG_TAG, errorMsg)
            onStatus("Error: $errorMsg")
            return@withContext
        }

        val initResult = initializeEngine(modelFile, backendParam)
        if (initResult == null) {
            val errorMsg = "Failed to initialize engine"
            Log.e(LOG_TAG, errorMsg)
            onStatus("Error: $errorMsg")
            return@withContext
        }

        val activeEngine = initResult.engine
        engine = activeEngine

        try {
            val imgDir = File(benchDir, "img")
            val imageFiles = discoverImageFiles(imgDir, initResult.visionAvailable)
            val caseResults = executeAllCases(activeEngine, imageFiles, runs, onStatus)

            val report = LlmBenchmarkReport(
                backend = backendParam,
                deviceModel = Build.MODEL,
                libraryVersion = "0.18.1",
                loadMs = initResult.loadMs,
                visionAvailable = initResult.visionAvailable,
                visionError = initResult.visionError,
                cases = caseResults
            )

            resultsFile.parentFile?.mkdirs()
            resultsFile.writeText(formatBenchmarkReportJson(report))
            Log.i(LOG_TAG, "Benchmark complete. Results written to ${resultsFile.absolutePath}")
            onStatus("Done, wrote ${resultsFile.absolutePath}")
        } finally {
            activeEngine.close()
            engine = null
        }
    }

    private fun discoverImageFiles(imgDir: File, visionAvailable: Boolean): List<File> {
        if (!visionAvailable || !imgDir.exists()) return emptyList()
        return imgDir.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", ignoreCase = true) ||
                file.extension.equals("png", ignoreCase = true))
        }?.sortedBy { it.name } ?: emptyList()
    }

    private suspend fun executeAllCases(
        activeEngine: Engine,
        imageFiles: List<File>,
        runs: Int,
        onStatus: (String) -> Unit
    ): List<LlmCaseResult> {
        val totalCases = TEXT_PROMPTS.size + imageFiles.size
        val caseResults = mutableListOf<LlmCaseResult>()
        var caseIndex = 1

        for (prompt in TEXT_PROMPTS) {
            onStatus("Running case $caseIndex of $totalCases")
            Log.i(LOG_TAG, "Running case $caseIndex of $totalCases: $prompt")
            val caseResult = runCase(
                activeEngine = activeEngine,
                name = prompt,
                kind = "text",
                contents = Contents.of(Content.Text(prompt)),
                runs = runs
            )
            caseResults.add(caseResult)
            caseIndex++
        }

        for (imgFile in imageFiles) {
            onStatus("Running case $caseIndex of $totalCases")
            Log.i(LOG_TAG, "Running case $caseIndex of $totalCases (image)")
            val contents = Contents.of(
                Content.ImageFile(imgFile.absolutePath),
                Content.Text(IMAGE_PROMPT)
            )
            val caseResult = runCase(
                activeEngine = activeEngine,
                name = imgFile.name,
                kind = "image",
                contents = contents,
                runs = runs
            )
            caseResults.add(caseResult)
            caseIndex++
        }
        return caseResults
    }

    @Suppress("TooGenericExceptionCaught")
    private fun initializeEngine(modelFile: File, backendParam: String): EngineInitResult? {
        val backendObj = if (backendParam == "gpu") Backend.GPU() else Backend.CPU()
        val loadStart = SystemClock.elapsedRealtime()

        return try {
            val configWithVision = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backendObj,
                visionBackend = backendObj,
                audioBackend = Backend.CPU(),
                maxNumImages = 1,
                cacheDir = cacheDir.path
            )
            val eng = Engine(configWithVision)
            eng.initialize()
            val loadMs = SystemClock.elapsedRealtime() - loadStart
            Log.i(LOG_TAG, "Loaded engine with vision in ${loadMs}ms")
            EngineInitResult(eng, loadMs, visionAvailable = true, visionError = null)
        } catch (e: Exception) {
            val visionError = e.message ?: e.toString()
            Log.w(LOG_TAG, "Vision init failed: $visionError. Retrying text-only.")
            try {
                val configTextOnly = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backendObj,
                    audioBackend = Backend.CPU(),
                    cacheDir = cacheDir.path
                )
                val eng = Engine(configTextOnly)
                eng.initialize()
                val loadMs = SystemClock.elapsedRealtime() - loadStart
                Log.i(LOG_TAG, "Loaded engine text-only in ${loadMs}ms")
                EngineInitResult(eng, loadMs, visionAvailable = false, visionError = visionError)
            } catch (fallbackEx: Exception) {
                Log.e(LOG_TAG, "Engine init fallback failed: ${fallbackEx.message}", fallbackEx)
                null
            }
        }
    }

    private suspend fun runCase(
        activeEngine: Engine,
        name: String,
        kind: String,
        contents: Contents,
        runs: Int
    ): LlmCaseResult {
        val runSummaries = mutableListOf<LlmRunSummary>()
        var firstRunReplyText: String? = null

        for (runIdx in 0 until runs) {
            val (summary, replyText) = executeRun(activeEngine, contents)
            runSummaries.add(summary)
            if (runIdx == 0) {
                firstRunReplyText = replyText
            }
            Log.i(
                LOG_TAG,
                "Run $runIdx: chunks=${summary.chunks}, ttftMs=${summary.ttftMs}, " +
                    "firstClauseMs=${summary.firstClauseMs}, totalMs=${summary.totalMs}, " +
                    "decodeChunksPerSec=${summary.decodeChunksPerSec}"
            )
        }

        val caseAggregate = aggregate(runSummaries)
        return LlmCaseResult(
            name = name,
            kind = kind,
            runs = runSummaries,
            aggregate = caseAggregate,
            replyText = firstRunReplyText
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRun(
        activeEngine: Engine,
        contents: Contents
    ): Pair<LlmRunSummary, String> {
        val conversation = activeEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(Content.Text(SYSTEM_INSTRUCTION)),
                samplerConfig = SamplerConfig(
                    topK = DEFAULT_TOP_K,
                    topP = DEFAULT_TOP_P,
                    temperature = DEFAULT_TEMPERATURE,
                    seed = DEFAULT_SEED
                )
            )
        )

        val chunkNanos = mutableListOf<Long>()
        val chunkTexts = mutableListOf<String>()
        val replyBuilder = StringBuilder()
        var runError: String? = null
        val sendNanos = SystemClock.elapsedRealtimeNanos()

        try {
            conversation.sendMessageAsync(contents).collect { message ->
                val chunkText = message.contents.contents
                    .mapNotNull { (it as? Content.Text)?.text }
                    .joinToString("")
                if (chunkText.isNotEmpty()) {
                    chunkNanos.add(SystemClock.elapsedRealtimeNanos())
                    chunkTexts.add(chunkText)
                    replyBuilder.append(chunkText)
                }
            }
        } catch (e: Exception) {
            runError = e.message ?: e.toString()
            Log.e(LOG_TAG, "Run exception: $runError", e)
        } finally {
            conversation.close()
        }

        val endNanos = SystemClock.elapsedRealtimeNanos()
        val timing = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = chunkNanos,
            chunkTexts = chunkTexts,
            endNanos = endNanos,
            error = runError
        )
        val summary = summarize(timing)
        return Pair(summary, replyBuilder.toString())
    }

    private data class EngineInitResult(
        val engine: Engine,
        val loadMs: Long,
        val visionAvailable: Boolean,
        val visionError: String?
    )
}

@Composable
private fun BenchmarkScreen(statusText: String) {
    Surface(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(PADDING_DP.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
