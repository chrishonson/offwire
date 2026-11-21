package com.chrishonson.gemmavoice.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private val ScreenPadding = 16.dp
private val Spacing = 12.dp
private val TouchTargetHeight = 60.dp
private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

@Composable
fun BenchmarkScreen(store: BenchmarkStore, onBack: () -> Unit) {
    val state by store.state.collectAsState()
    BenchmarkContent(state = state, onRun = { store.dispatch(BenchmarkIntent.Run) }, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BenchmarkContent(state: BenchmarkState, onRun: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Benchmark") },
                actions = {
                    Button(
                        onClick = onBack,
                        enabled = !state.running,
                        modifier = Modifier.heightIn(min = TouchTargetHeight),
                        contentPadding = ButtonPadding
                    ) {
                        Text("Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing)
        ) {
            Text(state.modelName, style = MaterialTheme.typography.titleLarge)
            Text(
                "Loads the model, then answers ${ENGINE_BENCH_PROMPTS.size} prompts three times each. " +
                    "The chat starts over afterwards.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onRun,
                enabled = !state.running,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTargetHeight)
                    .testTag("runBenchmark"),
                contentPadding = ButtonPadding
            ) {
                Text(if (state.phase is BenchmarkPhase.Idle) "Run benchmark" else "Run again")
            }
            when (val phase = state.phase) {
                BenchmarkPhase.Idle -> Unit
                is BenchmarkPhase.Running -> Text(phase.status, modifier = Modifier.testTag("benchmarkStatus"))
                is BenchmarkPhase.Failed -> Text(
                    phase.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("benchmarkStatus")
                )
                is BenchmarkPhase.Done -> BenchmarkResults(phase)
            }
        }
    }
}

@Composable
private fun BenchmarkResults(done: BenchmarkPhase.Done) {
    Text("Load ${done.report.loadMs} ms", style = MaterialTheme.typography.titleMedium)
    done.report.cases.forEach { case ->
        Column {
            Text(case.name, style = MaterialTheme.typography.titleSmall)
            Text(caseSummary(case), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        "Saved to ${done.path}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Medians across the runs of one prompt. */
internal fun caseSummary(case: LlmCaseResult): String {
    val stats = case.aggregate
    return "First text ${ms(stats.ttftMs.median)}, first clause ${ms(stats.firstClauseMs.median)}, " +
        "whole reply ${ms(stats.totalMs.median)}"
}

private fun ms(value: Double?): String = value?.let { "${it.roundToLong()} ms" } ?: "n/a"
