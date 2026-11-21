package com.chrishonson.gemmavoice.bench

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmBenchStatsTest {

    @Test
    fun ttftCalculatesFromSendToFirstChunk() {
        val sendNanos = 1_000_000_000L
        val firstChunkNanos = 1_050_000_000L // +50ms
        val endNanos = 1_200_000_000L

        val timing = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(firstChunkNanos, 1_100_000_000L),
            chunkTexts = listOf("Hello", " world"),
            endNanos = endNanos
        )

        val summary = summarize(timing)
        assertEquals(50.0, summary.ttftMs)
        assertEquals(200.0, summary.totalMs)
        assertEquals(2, summary.chunks)
    }

    @Test
    fun ttftReturnsNullWhenNoChunksEmitted() {
        val timing = LlmRunTiming(
            sendNanos = 1_000_000_000L,
            chunkNanos = emptyList(),
            chunkTexts = emptyList(),
            endNanos = 1_050_000_000L
        )

        val summary = summarize(timing)
        assertNull(summary.ttftMs)
        assertEquals(50.0, summary.totalMs)
        assertEquals(0, summary.chunks)
        assertNull(summary.decodeChunksPerSec)
    }

    @Test
    fun firstClausePunctuationCase() {
        val sendNanos = 0L
        val timingComma = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(20_000_000L, 40_000_000L),
            chunkTexts = listOf("Hello, ", "how are you?"),
            endNanos = 50_000_000L
        )
        val summaryComma = summarize(timingComma)
        assertEquals(20.0, summaryComma.firstClauseMs)

        val timingPeriodAtEnd = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(15_000_000L, 30_000_000L),
            chunkTexts = listOf("Yes", "."),
            endNanos = 40_000_000L
        )
        val summaryPeriod = summarize(timingPeriodAtEnd)
        assertEquals(30.0, summaryPeriod.firstClauseMs)

        val timingColon = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(25_000_000L, 50_000_000L),
            chunkTexts = listOf("Note: ", "important"),
            endNanos = 60_000_000L
        )
        assertEquals(25.0, summarize(timingColon).firstClauseMs)
    }

    @Test
    fun firstClauseFortyCharacterCase() {
        val sendNanos = 0L
        // 4 chunks of 10 characters without punctuation: reaches 40 at index 3
        val timing = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(10_000_000L, 20_000_000L, 30_000_000L, 45_000_000L),
            chunkTexts = listOf("abcdefghij", "abcdefghij", "abcdefghij", "abcdefghij"),
            endNanos = 60_000_000L
        )

        val summary = summarize(timing)
        assertEquals(45.0, summary.firstClauseMs)
    }

    @Test
    fun firstClauseNeverReachedCaseReturnsNull() {
        val sendNanos = 0L
        val timing = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(10_000_000L, 20_000_000L),
            chunkTexts = listOf("short", " text"),
            endNanos = 30_000_000L
        )

        val summary = summarize(timing)
        assertNull(summary.firstClauseMs)
    }

    @Test
    fun decodeRateWithZeroChunks() {
        val timing = LlmRunTiming(
            sendNanos = 0L,
            chunkNanos = emptyList(),
            chunkTexts = emptyList(),
            endNanos = 10_000_000L
        )
        assertNull(summarize(timing).decodeChunksPerSec)
    }

    @Test
    fun decodeRateWithOneChunk() {
        val timing = LlmRunTiming(
            sendNanos = 0L,
            chunkNanos = listOf(10_000_000L),
            chunkTexts = listOf("token"),
            endNanos = 20_000_000L
        )
        assertNull(summarize(timing).decodeChunksPerSec)
    }

    @Test
    fun decodeRateWithManyChunks() {
        val sendNanos = 0L
        // 5 chunks across 2 seconds (0.0s, 0.5s, 1.0s, 1.5s, 2.0s): (5 - 1) / 2.0s = 2.0 chunks/sec
        val timing = LlmRunTiming(
            sendNanos = sendNanos,
            chunkNanos = listOf(
                100_000_000L,
                600_000_000L,
                1_100_000_000L,
                1_600_000_000L,
                2_100_000_000L
            ),
            chunkTexts = listOf("One", " two", " three", " four", " five"),
            endNanos = 2_200_000_000L
        )
        val summary = summarize(timing)
        assertNotNull(summary.decodeChunksPerSec)
        assertEquals(2.0, summary.decodeChunksPerSec, 0.001)
    }

    @Test
    fun aggregateMedianWithOddCounts() {
        val stats = computeMetricStats(listOf(30.0, 10.0, 20.0))
        assertEquals(20.0, stats.median)
        assertEquals(10.0, stats.min)
        assertEquals(30.0, stats.max)
    }

    @Test
    fun aggregateMedianWithEvenCounts() {
        val stats = computeMetricStats(listOf(40.0, 10.0, 30.0, 20.0))
        assertEquals(25.0, stats.median)
        assertEquals(10.0, stats.min)
        assertEquals(40.0, stats.max)
    }

    @Test
    fun aggregateMedianWithNulls() {
        val statsWithNulls = computeMetricStats(listOf(null, 10.0, null, 30.0))
        assertEquals(20.0, statsWithNulls.median)
        assertEquals(10.0, statsWithNulls.min)
        assertEquals(30.0, statsWithNulls.max)

        val allNulls = computeMetricStats(listOf(null, null))
        assertNull(allNulls.median)
        assertNull(allNulls.min)
        assertNull(allNulls.max)

        val empty = computeMetricStats(emptyList())
        assertNull(empty.median)
        assertNull(empty.min)
        assertNull(empty.max)
    }

    @Test
    fun aggregateCaseStatsAggregatesAllMetrics() {
        val summary1 = LlmRunSummary(
            ttftMs = 10.0,
            firstClauseMs = 20.0,
            totalMs = 100.0,
            chunks = 5,
            decodeChunksPerSec = 50.0
        )
        val summary2 = LlmRunSummary(
            ttftMs = 30.0,
            firstClauseMs = 40.0,
            totalMs = 200.0,
            chunks = 10,
            decodeChunksPerSec = 70.0
        )
        val caseStats = aggregate(listOf(summary1, summary2))

        assertEquals(20.0, caseStats.ttftMs.median)
        assertEquals(10.0, caseStats.ttftMs.min)
        assertEquals(30.0, caseStats.ttftMs.max)

        assertEquals(30.0, caseStats.firstClauseMs.median)
        assertEquals(150.0, caseStats.totalMs.median)
        assertEquals(60.0, caseStats.decodeChunksPerSec.median)
    }

    @Test
    fun jsonSerializationRoundTrip() {
        val report = LlmBenchmarkReport(
            backend = "gpu",
            deviceModel = "Pixel 9 Pro",
            libraryVersion = "0.18.1",
            loadMs = 1234L,
            visionAvailable = true,
            visionError = null,
            cases = listOf(
                LlmCaseResult(
                    name = "What is the capital of France?",
                    kind = "text",
                    runs = listOf(
                        LlmRunSummary(
                            ttftMs = 45.2,
                            firstClauseMs = 80.5,
                            totalMs = 350.0,
                            chunks = 12,
                            decodeChunksPerSec = 35.8
                        )
                    ),
                    aggregate = LlmCaseStats(
                        ttftMs = MetricStats(45.2, 45.2, 45.2),
                        firstClauseMs = MetricStats(80.5, 80.5, 80.5),
                        totalMs = MetricStats(350.0, 350.0, 350.0),
                        decodeChunksPerSec = MetricStats(35.8, 35.8, 35.8)
                    ),
                    replyText = "Paris is the capital of France."
                )
            )
        )

        val jsonString = formatBenchmarkReportJson(report)
        assertTrue(jsonString.contains("\"backend\": \"gpu\""))
        assertTrue(jsonString.contains("\"deviceModel\": \"Pixel 9 Pro\""))
        assertTrue(jsonString.contains("\"libraryVersion\": \"0.18.1\""))

        val decoded = Json.decodeFromString<LlmBenchmarkReport>(jsonString)
        assertEquals(report.backend, decoded.backend)
        assertEquals(report.deviceModel, decoded.deviceModel)
        assertEquals(report.cases.size, decoded.cases.size)
        assertEquals(report.cases[0].name, decoded.cases[0].name)
        assertEquals(report.cases[0].replyText, decoded.cases[0].replyText)
    }
}
