package com.chrishonson.gemmavoice.bench

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CLAUSE_LENGTH_THRESHOLD = 40
private const val NANOS_PER_MILLI = 1_000_000.0
private const val NANOS_PER_SECOND = 1_000_000_000.0

private val clausePunctuationRegex = Regex("""[.!?,;:](?:\s|$)""")

@Serializable
data class MetricStats(
    val median: Double?,
    val min: Double?,
    val max: Double?
)

@Serializable
data class LlmCaseStats(
    val ttftMs: MetricStats,
    val firstClauseMs: MetricStats,
    val totalMs: MetricStats,
    val decodeChunksPerSec: MetricStats
)

@Serializable
data class LlmRunTiming(
    val sendNanos: Long,
    val chunkNanos: List<Long>,
    val chunkTexts: List<String>,
    val endNanos: Long,
    val error: String? = null
)

@Serializable
data class LlmRunSummary(
    val ttftMs: Double?,
    val firstClauseMs: Double?,
    val totalMs: Double,
    val chunks: Int,
    val decodeChunksPerSec: Double?,
    val error: String? = null
)

@Serializable
data class LlmCaseResult(
    val name: String,
    val kind: String,
    val runs: List<LlmRunSummary>,
    val aggregate: LlmCaseStats,
    val replyText: String?
)

@Serializable
data class LlmBenchmarkReport(
    val backend: String,
    val deviceModel: String,
    val model: String? = null,
    val libraryVersion: String = "0.18.1",
    val loadMs: Long,
    val visionAvailable: Boolean,
    val visionError: String? = null,
    val cases: List<LlmCaseResult>
)

private val benchJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

fun formatBenchmarkReportJson(report: LlmBenchmarkReport): String = benchJson.encodeToString(report)

fun summarize(run: LlmRunTiming): LlmRunSummary {
    val chunks = run.chunkNanos.size
    val ttftMs = if (run.chunkNanos.isNotEmpty()) {
        (run.chunkNanos.first() - run.sendNanos) / NANOS_PER_MILLI
    } else {
        null
    }
    val firstClauseMs = calculateFirstClauseMs(run.sendNanos, run.chunkNanos, run.chunkTexts)
    val totalMs = (run.endNanos - run.sendNanos) / NANOS_PER_MILLI
    val decodeChunksPerSec = if (chunks >= 2) {
        val durationSec = (run.chunkNanos.last() - run.chunkNanos.first()) / NANOS_PER_SECOND
        if (durationSec > 0.0) {
            (chunks - 1) / durationSec
        } else {
            null
        }
    } else {
        null
    }
    return LlmRunSummary(
        ttftMs = ttftMs,
        firstClauseMs = firstClauseMs,
        totalMs = totalMs,
        chunks = chunks,
        decodeChunksPerSec = decodeChunksPerSec,
        error = run.error
    )
}

private fun calculateFirstClauseMs(
    sendNanos: Long,
    chunkNanos: List<Long>,
    chunkTexts: List<String>
): Double? {
    val textBuilder = StringBuilder()
    val count = minOf(chunkNanos.size, chunkTexts.size)
    for (i in 0 until count) {
        textBuilder.append(chunkTexts[i])
        val text = textBuilder.toString()
        if (text.length >= CLAUSE_LENGTH_THRESHOLD || clausePunctuationRegex.containsMatchIn(text)) {
            return (chunkNanos[i] - sendNanos) / NANOS_PER_MILLI
        }
    }
    return null
}

fun computeMetricStats(values: List<Double?>): MetricStats {
    val nonNull = values.filterNotNull().sorted()
    if (nonNull.isEmpty()) {
        return MetricStats(median = null, min = null, max = null)
    }
    val count = nonNull.size
    val median = if (count % 2 == 1) {
        nonNull[count / 2]
    } else {
        val mid1 = nonNull[count / 2 - 1]
        val mid2 = nonNull[count / 2]
        (mid1 + mid2) / 2.0
    }
    return MetricStats(
        median = median,
        min = nonNull.first(),
        max = nonNull.last()
    )
}

fun aggregate(summaries: List<LlmRunSummary>): LlmCaseStats {
    return LlmCaseStats(
        ttftMs = computeMetricStats(summaries.map { it.ttftMs }),
        firstClauseMs = computeMetricStats(summaries.map { it.firstClauseMs }),
        totalMs = computeMetricStats(summaries.map { it.totalMs }),
        decodeChunksPerSec = computeMetricStats(summaries.map { it.decodeChunksPerSec })
    )
}
