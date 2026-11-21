package com.chrishonson.gemmavoice.voice

import kotlin.math.sqrt

private const val BYTES_PER_SAMPLE = 2
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val NEGATIVE_PCM_DIVISOR = 32768f
private const val POSITIVE_PCM_DIVISOR = 32767f

interface VoiceActivityDetector {
    val frameSamples: Int
    fun speechProbability(frame: FloatArray): Float
    fun reset()
}

fun pcm16ToFloats(frame: ByteArray): FloatArray {
    val sampleCount = frame.size / BYTES_PER_SAMPLE
    val floats = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
        val lo = frame[i * BYTES_PER_SAMPLE].toInt() and BYTE_MASK
        val hi = frame[i * BYTES_PER_SAMPLE + 1].toInt()
        val sample = ((hi shl BITS_PER_BYTE) or lo).toShort().toInt()
        floats[i] = if (sample < 0) {
            sample / NEGATIVE_PCM_DIVISOR
        } else {
            sample / POSITIVE_PCM_DIVISOR
        }
    }
    return floats
}

open class EnergyVad(
    private val config: VoiceConfig = VoiceConfig()
) : VoiceActivityDetector {
    override val frameSamples: Int
        get() = config.frameSamples

    override fun speechProbability(frame: FloatArray): Float {
        if (frame.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in frame) {
            val d = sample.toDouble()
            sumSquares += d * d
        }
        val rms = sqrt(sumSquares / frame.size)
        return if (rms >= config.rmsFloor) 1f else 0f
    }

    override fun reset() = Unit
}
