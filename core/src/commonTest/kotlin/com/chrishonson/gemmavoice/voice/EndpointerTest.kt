package com.chrishonson.gemmavoice.voice

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndpointerTest {
    private val config = VoiceConfig()

    private fun makeFrame(amplitude: Double = 0.0): ByteArray {
        val pcm = ByteArray(config.frameBytes)
        val sampleCount = pcm.size / 2
        for (i in 0 until sampleCount) {
            val raw = if (i % 2 == 0) {
                (amplitude * 32767.0).roundToInt()
            } else {
                -(amplitude * 32768.0).roundToInt()
            }
            pcm[i * 2] = (raw and 0xFF).toByte()
            pcm[i * 2 + 1] = ((raw shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun frameCountForMs(ms: Int): Int =
        ((ms + config.frameMs - 1) / config.frameMs).coerceAtLeast(1)

    private fun pushProbabilities(
        endpointer: Endpointer,
        vad: FakeVad,
        probability: Float,
        ms: Int
    ): List<EndpointResult> {
        val count = frameCountForMs(ms)
        vad.enqueueFrames(probability, count)
        return List(count) { endpointer.push(makeFrame()) }
    }

    @Test
    fun rms16AndEnergyVadComputeNormalizedSpeechDecision() {
        assertEquals(0.0, rms16(ByteArray(0)))
        assertEquals(0.0, rms16(makeFrame(0.0)), 1e-4)
        assertEquals(0.2, rms16(makeFrame(0.2)), 1e-3)

        val energyVad = EnergyVad(config)
        assertEquals(config.frameSamples, energyVad.frameSamples)
        assertEquals(0f, energyVad.speechProbability(FloatArray(0)))
        assertEquals(0f, energyVad.speechProbability(pcm16ToFloats(makeFrame(0.0))))
        assertEquals(1f, energyVad.speechProbability(pcm16ToFloats(makeFrame(0.2))))
    }

    @Test
    fun thresholdBasedVoicedDetectionAndEndpointsAfter700MsHang() {
        val vad = FakeVad(config)
        val endpointer = Endpointer(config, vad)

        // Frames below vadThreshold (0.7f) do not count as voiced
        pushProbabilities(endpointer, vad, probability = 0.69f, ms = 320)
        assertEquals(0, endpointer.voicedMs)
        assertFalse(endpointer.hasSpeech)

        // 416ms (13 frames) at >= 0.7f reaches minSpeechMs (400ms)
        pushProbabilities(endpointer, vad, probability = 0.7f, ms = 416)
        assertTrue(endpointer.hasSpeech)
        assertEquals(416, endpointer.voicedMs)

        // 672ms (21 frames) of silence is still below 700ms hang -> NONE
        vad.enqueueFrames(probability = 0.1f, count = 21)
        repeat(21) {
            assertEquals(EndpointResult.NONE, endpointer.push(makeFrame()))
        }

        // 22nd silent frame reaches 704ms >= 700ms hang -> ENDPOINT
        vad.enqueue(0.1f)
        assertEquals(EndpointResult.ENDPOINT, endpointer.push(makeFrame()))
    }

    @Test
    fun dropsVoicedBlipShorterThan400MsMinSpeechAfterHang() {
        val vad = FakeVad(config)
        val endpointer = Endpointer(config, vad)

        // 4 frames = 128ms < 400ms minSpeechMs
        pushProbabilities(endpointer, vad, probability = 0.75f, ms = 128)
        assertFalse(endpointer.hasSpeech)
        assertEquals(128, endpointer.voicedMs)

        val results = pushProbabilities(endpointer, vad, probability = 0.0f, ms = 700)
        assertTrue(results.all { it == EndpointResult.NONE })
        assertFalse(endpointer.hasSpeech)
        assertEquals(0, endpointer.voicedMs)
    }

    @Test
    fun trimsLeadInSilenceBeforeSpeechAndResetsVadOnTake() {
        val vad = FakeVad(config)
        val endpointer = Endpointer(config, vad)
        pushProbabilities(endpointer, vad, probability = 0.0f, ms = 2000)
        pushProbabilities(endpointer, vad, probability = 0.9f, ms = 480)

        val pcm = endpointer.take()
        val capturedMs = pcm.size / config.bytesPerMs
        assertTrue(
            capturedMs in 480..800,
            "Expected lead-in trimmed to ~300ms + 480ms speech, got ${capturedMs}ms"
        )
        assertFalse(endpointer.hasSpeech)
        assertEquals(1, vad.resetCount)
        assertEquals(0, endpointer.take().size)
    }

    @Test
    fun endpointsAtMaxSegmentCapDuringContinuousSpeech() {
        val vad = FakeVad(config)
        val endpointer = Endpointer(config, vad)
        val results = pushProbabilities(endpointer, vad, probability = 0.9f, ms = 25_000)

        assertTrue(results.dropLast(1).all { it == EndpointResult.NONE })
        assertEquals(EndpointResult.ENDPOINT, results.last())
    }

    @Test
    fun bargeInTriggersOnlyAfterConsecutiveSpeechAboveBargeInThreshold() {
        val vad = FakeVad(config)
        val endpointer = Endpointer(config, vad)

        // Speech above vadThreshold (0.6f) but below bargeInThreshold (0.8f) does not trigger barge-in
        pushProbabilities(endpointer, vad, probability = 0.7f, ms = 500)
        assertFalse(endpointer.bargeInTriggered)

        // Short burst above bargeInThreshold (5 frames = 160ms < 200ms) then sub-threshold frame resets counter
        pushProbabilities(endpointer, vad, probability = 0.85f, ms = 160)
        assertFalse(endpointer.bargeInTriggered)
        pushProbabilities(endpointer, vad, probability = 0.5f, ms = 32)
        pushProbabilities(endpointer, vad, probability = 0.85f, ms = 96)
        assertFalse(endpointer.bargeInTriggered)

        // Reaching >= 200ms (4 more frames -> 7 frames = 224ms) of consecutive >= 0.8f triggers barge-in
        pushProbabilities(endpointer, vad, probability = 0.85f, ms = 128)
        assertTrue(endpointer.bargeInTriggered)

        endpointer.reset()
        assertFalse(endpointer.bargeInTriggered)
        assertEquals(1, vad.resetCount)
    }
}
