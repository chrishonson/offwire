package com.chrishonson.gemmavoice.bench

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chrishonson.gemmavoice.GemmaVoiceTheme
import com.chrishonson.gemmavoice.llm.IosLlmEngine
import com.chrishonson.gemmavoice.llm.LlmBridge
import com.chrishonson.gemmavoice.model.IosModelRepository
import com.chrishonson.gemmavoice.model.Models
import com.chrishonson.gemmavoice.voice.VoiceConfig

private const val RUNS = 3

/** Times the app's own engine with the runner Android shares, then writes Documents/bench/llm-app.json. */
@Composable
fun IosBenchmarkScreen(bridge: LlmBridge) {
    var status by remember { mutableStateOf("Loading") }
    LaunchedEffect(Unit) {
        val engine = IosLlmEngine(bridge, VoiceConfig.DEFAULT_ASSISTANT_NAME)
        try {
            val modelPath = IosModelRepository().modelPath(Models.GEMMA_4_E2B)
            val host = IosBenchmarkHost()
            val report = benchmarkEngine(engine, modelPath, host.deviceModel, host.backend, RUNS) { status = it }
            status = "Done, wrote ${host.writeReport("llm-app.json", formatBenchmarkReportJson(report))}"
        } finally {
            engine.close()
        }
    }
    GemmaVoiceTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(status) }
    }
}
