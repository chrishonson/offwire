package com.chrishonson.gemmavoice.bench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chrishonson.gemmavoice.llm.LlmEngine
import com.chrishonson.gemmavoice.model.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val RUNS = 3

/** What the platform supplies: who is being measured, and where the report goes. */
interface BenchmarkHost {
    val deviceModel: String
    val backend: String

    /** Writes [json] to a file called [name] and returns its path. */
    fun writeReport(name: String, json: String): String
}

sealed interface BenchmarkPhase {
    data object Idle : BenchmarkPhase
    data class Running(val status: String) : BenchmarkPhase
    data class Done(val report: LlmBenchmarkReport, val path: String) : BenchmarkPhase
    data class Failed(val message: String) : BenchmarkPhase
}

data class BenchmarkState(
    val modelName: String,
    val phase: BenchmarkPhase = BenchmarkPhase.Idle
) {
    val running: Boolean
        get() = phase is BenchmarkPhase.Running
}

sealed interface BenchmarkIntent {
    data object Run : BenchmarkIntent
}

/**
 * Times [model] on the app's own engine. The chat must leave the engine idle first, and load it again afterwards,
 * because the benchmark closes it and loads the model itself: two models never share memory.
 */
class BenchmarkStore(
    private val engine: LlmEngine,
    private val model: ModelSpec,
    private val modelPath: String,
    private val host: BenchmarkHost
) : ViewModel() {
    private val _state = MutableStateFlow(BenchmarkState(modelName = model.displayName))
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()

    fun dispatch(intent: BenchmarkIntent) {
        when (intent) {
            BenchmarkIntent.Run -> run()
        }
    }

    private fun run() {
        if (_state.value.running) return
        setPhase(BenchmarkPhase.Running("Loading"))
        viewModelScope.launch {
            try {
                engine.close()
                val report = benchmarkEngine(
                    engine = engine,
                    modelPath = modelPath,
                    deviceModel = host.deviceModel,
                    backend = host.backend,
                    runs = RUNS,
                    model = model.id
                ) { setPhase(BenchmarkPhase.Running(it)) }
                val path = host.writeReport("llm-app-${model.id}.json", formatBenchmarkReportJson(report))
                setPhase(BenchmarkPhase.Done(report, path))
            } catch (e: CancellationException) {
                throw e
            } catch (expected: Exception) {
                setPhase(BenchmarkPhase.Failed(expected.message ?: "Benchmark failed"))
            }
        }
    }

    private fun setPhase(phase: BenchmarkPhase) {
        _state.update { it.copy(phase = phase) }
    }
}
