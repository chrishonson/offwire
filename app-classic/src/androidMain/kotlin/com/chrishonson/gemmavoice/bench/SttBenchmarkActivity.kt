package com.chrishonson.gemmavoice.bench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrishonson.gemmavoice.GemmaVoiceTheme
import com.chrishonson.gemmavoice.voice.ActivityMicPermission
import com.chrishonson.gemmavoice.voice.AndroidAudioStream
import com.chrishonson.gemmavoice.voice.CommunicationAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val PERCENT_FACTOR = 100
private const val KB_PER_MB = 1024f
private const val COLUMN_WEIGHT_CANDIDATE = 2.4f
private const val COLUMN_WEIGHT_STAT = 1.4f

class SttBenchmarkActivity : ComponentActivity() {

    private lateinit var benchDir: File
    private lateinit var recDir: File
    private lateinit var sttDir: File
    private lateinit var resultsFile: File

    private lateinit var recordController: BenchRecordController
    private lateinit var benchmarkRunner: SttBenchmarkRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val baseDir = getExternalFilesDir(null) ?: filesDir
        benchDir = File(baseDir, "bench")
        recDir = File(benchDir, "rec").apply { mkdirs() }
        sttDir = File(baseDir, "stt").apply { mkdirs() }
        resultsFile = File(benchDir, "results.json")

        val audioStream = AndroidAudioStream(callAudio = CommunicationAudio(applicationContext))
        recordController = BenchRecordController(audioStream, benchDir, recDir)
        benchmarkRunner = SttBenchmarkRunner(applicationContext)

        val micPermission = ActivityMicPermission(this)
        val autoRun = intent.getBooleanExtra("run", false)

        setContent {
            GemmaVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BenchmarkRootScreen(
                        recordController = recordController,
                        micPermission = micPermission,
                        autoRun = autoRun,
                        onRunBenchmark = { onProgress ->
                            benchmarkRunner.runBenchmark(recDir, sttDir, resultsFile, onProgress)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkRootScreen(
    recordController: BenchRecordController,
    micPermission: ActivityMicPermission,
    autoRun: Boolean,
    onRunBenchmark: suspend ((String) -> Unit) -> BenchmarkReport
) {
    var isRunning by remember { mutableStateOf(autoRun) }
    var progressText by remember { mutableStateOf("Initializing...") }
    var benchmarkReport by remember { mutableStateOf<BenchmarkReport?>(null) }
    val scope = rememberCoroutineScope()

    if (autoRun && benchmarkReport == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val report = onRunBenchmark { progressText = it }
            benchmarkReport = report
            isRunning = false
        }
    }

    if (isRunning) {
        RunningProgressScreen(progressText = progressText)
    } else if (benchmarkReport != null) {
        BenchmarkReportScreen(report = checkNotNull(benchmarkReport))
    } else {
        RecordModeScreen(
            controller = recordController,
            micPermission = micPermission,
            onStartBenchmark = {
                isRunning = true
                scope.launch(Dispatchers.Default) {
                    val report = onRunBenchmark { progressText = it }
                    benchmarkReport = report
                    isRunning = false
                }
            }
        )
    }
}

@Composable
private fun RunningProgressScreen(progressText: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Running Benchmark...",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordModeScreen(
    controller: BenchRecordController,
    micPermission: ActivityMicPermission,
    onStartBenchmark: () -> Unit
) {
    val currentIndex by controller.currentIndex.collectAsState()
    val isRecording by controller.isRecording.collectAsState()
    val allRecorded by controller.allRecorded.collectAsState()
    val isRecorded = controller.isCurrentSentenceRecorded()
    val scope = rememberCoroutineScope()

    val sentence = REFERENCE_SENTENCES[currentIndex - 1]

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Sentence $currentIndex of ${REFERENCE_SENTENCES.size}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = sentence,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (allRecorded) {
                Text(
                    text = "Done. Ready to run.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                Button(
                    onClick = { scope.launch { controller.stopRecording() } },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            if (micPermission.ensureGranted()) {
                                controller.startRecording(this)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text("Record", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { controller.back() },
                    enabled = currentIndex > 1
                ) {
                    Text("Back")
                }

                OutlinedButton(
                    onClick = { controller.redo() },
                    enabled = isRecorded
                ) {
                    Text("Redo")
                }

                OutlinedButton(
                    onClick = { controller.next() },
                    enabled = currentIndex < REFERENCE_SENTENCES.size
                ) {
                    Text("Next")
                }
            }

            if (allRecorded) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onStartBenchmark,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Run Benchmark")
                }
            }
        }
    }
}

@Composable
private fun BenchmarkReportScreen(report: BenchmarkReport) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)
    ) {
        Text("Benchmark Results", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Device: ${report.deviceModel}", style = MaterialTheme.typography.bodyMedium)
        val pssFormatted = report.gemmaPssKb?.let {
            String.format(Locale.US, "%.1f MB", it.toFloat() / KB_PER_MB)
        } ?: "Not loaded"
        Text("Gemma PSS: $pssFormatted", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(20.dp))

        if (report.candidates.isEmpty()) {
            Text(
                "No candidate models were evaluated (missing files in stt/).",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            // Table Header
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    "Candidate",
                    modifier = Modifier.weight(COLUMN_WEIGHT_CANDIDATE),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "WER",
                    modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "Mean",
                    modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "P90",
                    modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "PSS",
                    modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            TableDivider()

            report.candidates.forEach { cand ->
                val werText = String.format(Locale.US, "%.1f%%", cand.overallWer * PERCENT_FACTOR)
                val pssDeltaMb = cand.pssDeltaKb.toFloat() / KB_PER_MB
                val pssText = String.format(Locale.US, "%.1f MB", pssDeltaMb)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        cand.candidate,
                        modifier = Modifier.weight(COLUMN_WEIGHT_CANDIDATE),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        werText,
                        modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${cand.meanMs.toInt()}ms",
                        modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "${cand.p90Ms.toInt()}ms",
                        modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        pssText,
                        modifier = Modifier.weight(COLUMN_WEIGHT_STAT),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TableDivider()
            }
        }
    }
}

@Composable
private fun TableDivider() {
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)
    )
}
