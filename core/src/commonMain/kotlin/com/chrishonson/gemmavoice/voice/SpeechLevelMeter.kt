package com.chrishonson.gemmavoice.voice

import kotlin.math.sqrt

// Normal TTS speech has an RMS around 0.2-0.25; scale of 3.5f reaches ~0.6-0.9 while silence stays at 0.
const val SPEECH_LEVEL_SCALE = 3.5f

private const val WINDOW_MS = 50L
private const val MS_PER_SECOND = 1000L
private const val DEFAULT_SAMPLE_RATE = 24_000

class SpeechLevelMeter(
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val scale: Float = SPEECH_LEVEL_SCALE
) {
    val windowFrames: Int = ((sampleRate.toLong() * WINDOW_MS) / MS_PER_SECOND).toInt().coerceAtLeast(1)

    private data class Window(
        val startFrame: Long,
        val endFrame: Long,
        val level: Float
    )

    private val windows = ArrayDeque<Window>()
    private var currentFrame: Long = 0L
    private val residual = FloatArray(windowFrames)
    private var residualCount = 0

    fun write(samples: FloatArray) {
        if (samples.isEmpty()) return
        var offset = 0
        var remaining = samples.size

        if (residualCount > 0) {
            val needed = windowFrames - residualCount
            val toCopy = minOf(needed, remaining)
            samples.copyInto(
                destination = residual,
                destinationOffset = residualCount,
                startIndex = offset,
                endIndex = offset + toCopy
            )
            residualCount += toCopy
            offset += toCopy
            remaining -= toCopy

            if (residualCount == windowFrames) {
                addWindow(computeRms(residual, 0, windowFrames))
                residualCount = 0
            }
        }

        while (remaining >= windowFrames) {
            addWindow(computeRms(samples, offset, windowFrames))
            offset += windowFrames
            remaining -= windowFrames
        }

        if (remaining > 0) {
            samples.copyInto(
                destination = residual,
                destinationOffset = 0,
                startIndex = offset,
                endIndex = offset + remaining
            )
            residualCount = remaining
        }
    }

    fun writeSilence(frameCount: Int) {
        if (frameCount <= 0) return
        write(FloatArray(frameCount))
    }

    fun flush() {
        if (residualCount > 0) {
            addWindow(computeRms(residual, 0, residualCount), windowSize = residualCount)
            residualCount = 0
        }
    }

    private fun addWindow(rms: Double, windowSize: Int = windowFrames) {
        val level = (rms * scale).toFloat().coerceIn(0f, 1f)
        val end = currentFrame + windowSize
        windows.addLast(Window(startFrame = currentFrame, endFrame = end, level = level))
        currentFrame = end
    }

    private fun computeRms(data: FloatArray, start: Int, length: Int): Double {
        if (length <= 0) return 0.0
        var sumSquares = 0.0
        for (i in start until start + length) {
            val sample = data[i].toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / length)
    }

    fun levelAt(playedFrames: Long): Float {
        if (windows.isEmpty()) return 0f
        if (playedFrames < windows.first().startFrame) return 0f
        if (playedFrames >= windows.last().endFrame) return 0f

        while (windows.isNotEmpty() && windows.first().endFrame <= playedFrames) {
            windows.removeFirst()
        }

        val first = windows.firstOrNull() ?: return 0f
        return if (playedFrames in first.startFrame until first.endFrame) first.level else 0f
    }

    fun reset(initialFrame: Long = 0L) {
        windows.clear()
        residualCount = 0
        currentFrame = initialFrame
    }
}
