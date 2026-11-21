package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.VoiceConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt

class FakeAudioStream(
    private val config: VoiceConfig = VoiceConfig()
) : AudioStream {
    val channel = Channel<ByteArray>(Channel.UNLIMITED)
    var activeCollectors: Int = 0
        private set

    override fun frames(): Flow<ByteArray> = flow {
        activeCollectors++
        try {
            for (frame in channel) {
                emit(frame)
            }
        } finally {
            activeCollectors--
        }
    }

    fun push(bytes: ByteArray) {
        val frameBytes = config.frameBytes
        if (bytes.size <= frameBytes) {
            channel.trySend(bytes)
            return
        }
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + frameBytes, bytes.size)
            channel.trySend(bytes.copyOfRange(offset, end))
            offset = end
        }
    }

    fun silence(ms: Int): ByteArray = silence(ms, config)

    fun tone(ms: Int, amplitude: Double = DEFAULT_TONE_AMPLITUDE): ByteArray =
        tone(ms, amplitude, config)

    companion object {
        private const val DEFAULT_TONE_AMPLITUDE = 0.2
        private const val POSITIVE_PCM_MAX = 32767.0
        private const val NEGATIVE_PCM_MAX = 32768.0
        private const val BYTE_MASK = 0xFF
        private const val BITS_PER_BYTE = 8

        fun silence(ms: Int, config: VoiceConfig = VoiceConfig()): ByteArray {
            val frameCount = ((ms + config.frameMs - 1) / config.frameMs).coerceAtLeast(1)
            return ByteArray(frameCount * config.frameBytes)
        }

        fun tone(
            ms: Int,
            amplitude: Double = DEFAULT_TONE_AMPLITUDE,
            config: VoiceConfig = VoiceConfig()
        ): ByteArray {
            val frameCount = ((ms + config.frameMs - 1) / config.frameMs).coerceAtLeast(1)
            val pcm = ByteArray(frameCount * config.frameBytes)
            val sampleCount = pcm.size / 2
            for (i in 0 until sampleCount) {
                val raw = if (i % 2 == 0) {
                    (amplitude * POSITIVE_PCM_MAX).roundToInt()
                } else {
                    -(amplitude * NEGATIVE_PCM_MAX).roundToInt()
                }
                pcm[i * 2] = (raw and BYTE_MASK).toByte()
                pcm[i * 2 + 1] = ((raw shr BITS_PER_BYTE) and BYTE_MASK).toByte()
            }
            return pcm
        }
    }
}
