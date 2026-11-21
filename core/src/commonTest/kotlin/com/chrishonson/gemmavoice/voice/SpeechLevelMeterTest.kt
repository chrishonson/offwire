package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechLevelMeterTest {
    private val sampleRate = 24_000
    private val windowFrames = sampleRate * 50 / 1000 // 1200 frames

    private fun quiet(n: Int) = FloatArray(n)
    private fun constant(n: Int, amplitude: Float) = FloatArray(n) { amplitude }

    @Test
    fun silenceGivesZero() {
        val meter = SpeechLevelMeter(sampleRate)
        meter.write(quiet(windowFrames))
        assertEquals(0f, meter.levelAt(0))
        assertEquals(0f, meter.levelAt(windowFrames / 2L))
        assertEquals(0f, meter.levelAt((windowFrames - 1).toLong()))
    }

    @Test
    fun loudWindowGivesExpectedScaledLevel() {
        val meter = SpeechLevelMeter(sampleRate)
        val amplitude = 0.2f
        meter.write(constant(windowFrames, amplitude))
        val expected = (amplitude * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)
        assertEquals(expected, meter.levelAt(0), 1e-4f)
        assertEquals(expected, meter.levelAt((windowFrames / 2).toLong()), 1e-4f)
    }

    @Test
    fun levelFollowsPlayedFramesAcrossConsecutiveWindows() {
        val meter = SpeechLevelMeter(sampleRate)
        val loud1 = constant(windowFrames, 0.15f)
        val silent = quiet(windowFrames)
        val loud2 = constant(windowFrames, 0.25f)

        meter.write(loud1)
        meter.write(silent)
        meter.write(loud2)

        val expectedLoud1 = (0.15f * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)
        val expectedLoud2 = (0.25f * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)

        // First window: [0, 1200)
        assertEquals(expectedLoud1, meter.levelAt(0), 1e-4f)
        assertEquals(expectedLoud1, meter.levelAt(600), 1e-4f)

        // Second window: [1200, 2400)
        assertEquals(0f, meter.levelAt(1200), 1e-4f)
        assertEquals(0f, meter.levelAt(1800), 1e-4f)

        // Third window: [2400, 3600)
        assertEquals(expectedLoud2, meter.levelAt(2400), 1e-4f)
        assertEquals(expectedLoud2, meter.levelAt(3000), 1e-4f)
    }

    @Test
    fun playedFramesBeforeFirstAndAfterLastGivesZero() {
        val meter = SpeechLevelMeter(sampleRate)
        meter.write(constant(windowFrames, 0.2f))

        // Before first window
        assertEquals(0f, meter.levelAt(-100))
        assertEquals(0f, meter.levelAt(-1))

        // After last window
        assertEquals(0f, meter.levelAt(windowFrames.toLong()))
        assertEquals(0f, meter.levelAt(windowFrames.toLong() + 100))
    }

    @Test
    fun windowsAreKeyedToEndFramesSoWritingAheadDoesNotChangeCurrentHead() {
        val meter = SpeechLevelMeter(sampleRate)
        val expected1 = (0.1f * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)
        meter.write(constant(windowFrames, 0.1f))

        val levelBeforeMoreWrites = meter.levelAt(100)
        assertEquals(expected1, levelBeforeMoreWrites, 1e-4f)

        // Write several loud chunks ahead
        meter.write(constant(windowFrames, 0.8f))
        meter.write(constant(windowFrames, 0.9f))

        // Head is still at frame 100
        assertEquals(levelBeforeMoreWrites, meter.levelAt(100), 1e-4f)
    }

    @Test
    fun resetClearsAllState() {
        val meter = SpeechLevelMeter(sampleRate)
        meter.write(constant(windowFrames, 0.25f))
        val expected = (0.25f * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)
        assertEquals(expected, meter.levelAt(100), 1e-4f)

        meter.reset()
        assertEquals(0f, meter.levelAt(100))
        assertEquals(0f, meter.levelAt(0))
    }

    @Test
    fun clampingAtOne() {
        val meter = SpeechLevelMeter(sampleRate)
        meter.write(constant(windowFrames, 1.0f))
        assertEquals(1.0f, meter.levelAt(0))
    }

    @Test
    fun chunkStreamingWithPartialResidualAndFlush() {
        val meter = SpeechLevelMeter(sampleRate)
        // Feed in chunks smaller than a window (e.g. 500 samples each)
        val chunkSize = 500
        val amp = 0.2f
        meter.write(constant(chunkSize, amp)) // 500 buffered
        assertEquals(0f, meter.levelAt(0)) // no window complete yet

        meter.write(constant(chunkSize, amp)) // 1000 buffered
        assertEquals(0f, meter.levelAt(0))

        meter.write(constant(chunkSize, amp)) // 1500 total: 1 window (1200), 300 residual
        val expected = (amp * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)
        assertEquals(expected, meter.levelAt(0), 1e-4f)
        assertEquals(expected, meter.levelAt(1199), 1e-4f)
        assertEquals(0f, meter.levelAt(1200)) // residual not yet a window

        meter.flush() // residual 300 becomes a window [1200, 1500)
        assertEquals(expected, meter.levelAt(1200), 1e-4f)
        assertEquals(expected, meter.levelAt(1499), 1e-4f)
        assertEquals(0f, meter.levelAt(1500))
    }

    @Test
    fun resetWithInitialFrameOffset() {
        val meter = SpeechLevelMeter(sampleRate)
        val initialOffset = 5000L
        meter.reset(initialOffset)

        val amp = 0.2f
        meter.write(constant(windowFrames, amp))
        val expected = (amp * SPEECH_LEVEL_SCALE).coerceIn(0f, 1f)

        // Frames before initialOffset should give 0
        assertEquals(0f, meter.levelAt(0))
        assertEquals(0f, meter.levelAt(4999))

        // Frames within [5000, 5000 + windowFrames)
        assertEquals(expected, meter.levelAt(5000), 1e-4f)
        assertEquals(expected, meter.levelAt(5000 + windowFrames / 2L), 1e-4f)

        // Frames past window
        assertEquals(0f, meter.levelAt(5000 + windowFrames.toLong()))
    }

    @Test
    fun writeSilenceProducesZeroLevelWindows() {
        val meter = SpeechLevelMeter(sampleRate)
        meter.writeSilence(windowFrames * 2)
        assertEquals(0f, meter.levelAt(0))
        assertEquals(0f, meter.levelAt((windowFrames + 10).toLong()))
    }
}
