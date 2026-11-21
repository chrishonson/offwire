package com.chrishonson.gemmavoice.bench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkDataTest {

    @Test
    fun testReferenceSentencesCountAndExactValues() {
        assertEquals(15, REFERENCE_SENTENCES.size)
        assertEquals("Computer, what time is it?", REFERENCE_SENTENCES[0])
        assertEquals("Thank you, that is all for now.", REFERENCE_SENTENCES[14])
        assertEquals("My doctor prescribed metformin twice a day with meals.", REFERENCE_SENTENCES[2])
    }

    @Test
    fun testFormatRecordingFileName() {
        assertEquals("01.wav", formatRecordingFileName(1))
        assertEquals("09.wav", formatRecordingFileName(9))
        assertEquals("10.wav", formatRecordingFileName(10))
        assertEquals("15.wav", formatRecordingFileName(15))
    }

    @Test
    fun testCandidateSpecs() {
        assertEquals(4, CANDIDATE_SPECS.size)
        val tiny = CANDIDATE_SPECS.first { it.id == "moonshine-tiny" }
        assertEquals(CandidateType.MOONSHINE, tiny.type)
        assertEquals(listOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt"), tiny.requiredFiles)

        val parakeet = CANDIDATE_SPECS.first { it.id == "parakeet-110m" }
        assertEquals(CandidateType.NEMO_TRANSDUCER, parakeet.type)
        val expectedFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
        assertEquals(expectedFiles, parakeet.requiredFiles)
    }

    @Test
    fun testBenchmarkReportToJson() {
        val report = BenchmarkReport(
            deviceModel = "Pixel 8 Pro",
            gemmaPssKb = 1048576L,
            candidates = listOf(
                CandidateResult(
                    candidate = "moonshine-tiny",
                    loadMs = 150L,
                    pssDeltaKb = 32000L,
                    meanMs = 120.5,
                    p90Ms = 180.0,
                    maxMs = 210L,
                    overallWer = 0.05,
                    perSentence = listOf(
                        SentenceResult(
                            index = 1,
                            ms = 110L,
                            text = "computer what time is it",
                            wer = 0.0
                        )
                    )
                )
            ),
            audioVsText = listOf(
                AudioVsTextTiming(
                    index = 3,
                    audioFirstEmissionMs = 350L,
                    audioTotalMs = 950L,
                    textFirstEmissionMs = 100L,
                    textTotalMs = 700L
                )
            )
        )

        val json = report.toJson()
        assertTrue(json.contains("\"deviceModel\": \"Pixel 8 Pro\""))
        assertTrue(json.contains("\"gemmaPssKb\": 1048576"))
        assertTrue(json.contains("\"candidate\": \"moonshine-tiny\""))
        assertTrue(json.contains("\"loadMs\": 150"))
        assertTrue(json.contains("\"overallWer\": 0.05"))
        assertTrue(json.contains("\"audioFirstEmissionMs\": 350"))
    }

    @Test
    fun testBenchmarkReportToJsonWithNullGemmaPss() {
        val report = BenchmarkReport(
            deviceModel = "Test Device",
            gemmaPssKb = null,
            candidates = emptyList(),
            audioVsText = emptyList()
        )
        val json = report.toJson()
        assertTrue(json.contains("\"gemmaPssKb\": null"))
        assertTrue(json.contains("\"candidates\": []"))
        assertTrue(json.contains("\"audioVsText\": []"))
    }
}
