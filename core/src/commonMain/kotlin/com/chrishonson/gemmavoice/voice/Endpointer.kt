package com.chrishonson.gemmavoice.voice

import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val BYTES_PER_SAMPLE = 2
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val NEGATIVE_PCM_DIVISOR = 32768.0
private const val POSITIVE_PCM_DIVISOR = 32767.0

enum class EndpointResult {
    NONE,
    ENDPOINT
}

fun rms16(frame: ByteArray): Double {
    val sampleCount = frame.size / BYTES_PER_SAMPLE
    if (sampleCount == 0) return 0.0
    var sumSquares = 0.0
    for (i in 0 until sampleCount) {
        val lo = frame[i * BYTES_PER_SAMPLE].toInt() and BYTE_MASK
        val hi = frame[i * BYTES_PER_SAMPLE + 1].toInt()
        val sample = ((hi shl BITS_PER_BYTE) or lo).toShort().toInt()
        val normalized = if (sample < 0) {
            sample / NEGATIVE_PCM_DIVISOR
        } else {
            sample / POSITIVE_PCM_DIVISOR
        }
        sumSquares += normalized * normalized
    }
    return sqrt(sumSquares / sampleCount)
}

class Endpointer(
    private val config: VoiceConfig = VoiceConfig(),
    private val vad: VoiceActivityDetector = EnergyVad(config)
) {
    private val chunks = ArrayDeque<ByteArray>()
    private var bytes = 0
    private var voicedBytes = 0
    private var silentBytes = 0
    private var bargeInVoicedBytes = 0

    private val bytesPerMs: Int
        get() = config.bytesPerMs

    val hasSpeech: Boolean
        get() = voicedBytes >= config.minSpeechMs * bytesPerMs

    val voicedMs: Int
        get() = (voicedBytes.toDouble() / bytesPerMs).roundToInt()

    val bargeInTriggered: Boolean
        get() = bargeInVoicedBytes >= config.bargeInMinSpeechMs * bytesPerMs

    fun push(
        frame: ByteArray,
        probability: Float = vad.speechProbability(pcm16ToFloats(frame))
    ): EndpointResult {
        chunks.addLast(frame)
        bytes += frame.size
        if (probability >= config.vadThreshold) {
            voicedBytes += frame.size
            silentBytes = 0
        } else {
            silentBytes += frame.size
        }
        if (probability >= config.bargeInThreshold) {
            bargeInVoicedBytes += frame.size
        } else {
            bargeInVoicedBytes = 0
        }

        val paused = silentBytes >= config.endpointHangMs * bytesPerMs
        return when {
            voicedBytes == 0 -> {
                trimLeadIn()
                EndpointResult.NONE
            }
            paused && !hasSpeech -> {
                voicedBytes = 0
                trimLeadIn()
                EndpointResult.NONE
            }
            paused || bytes >= config.maxSegmentMs * bytesPerMs -> EndpointResult.ENDPOINT
            else -> EndpointResult.NONE
        }
    }

    fun take(): ByteArray {
        val pcm = ByteArray(bytes)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(pcm, destinationOffset = offset)
            offset += chunk.size
        }
        reset()
        return pcm
    }

    fun reset() {
        chunks.clear()
        bytes = 0
        voicedBytes = 0
        silentBytes = 0
        bargeInVoicedBytes = 0
        vad.reset()
    }

    private fun trimLeadIn() {
        val keep = config.leadInMs * bytesPerMs
        while (chunks.size > 1 && bytes - chunks.first().size >= keep) {
            bytes -= chunks.removeFirst().size
        }
    }
}
