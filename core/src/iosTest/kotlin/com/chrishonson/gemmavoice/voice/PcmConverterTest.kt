@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioChannelLayout
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.CoreAudioTypes.kAudioChannelLayoutTag_DiscreteInOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PcmConverterTest {
    private val mic16k = AVAudioFormat(
        commonFormat = AVAudioPCMFormatInt16,
        sampleRate = 16_000.0,
        channels = 1u,
        interleaved = true
    )

    @Test
    fun followsTheSourceFormatWhenItChanges() {
        val converter = PcmConverter(mic16k)
        val first = assertNotNull(converter.convert(tone(48_000.0, 1u)))
        val second = assertNotNull(converter.convert(tone(24_000.0, 1u)))
        assertTrue(first.frameLength > 0u && peak(first) > 0)
        assertTrue(second.frameLength > 0u && peak(second) > 0)
    }

    @Test
    fun keepsTheVoiceChannelOfMultichannelInput() {
        val output = assertNotNull(PcmConverter(mic16k).convert(tone(44_100.0, 5u)))
        assertTrue(peak(output) > 0, "voice on channel 0 must survive the downmix")
    }

    private fun tone(sampleRate: Double, channels: UInt): AVAudioPCMBuffer {
        val layout = AVAudioChannelLayout(layoutTag = kAudioChannelLayoutTag_DiscreteInOrder or channels)
        val format = AVAudioFormat(standardFormatWithSampleRate = sampleRate, channelLayout = layout)
        val frames = (sampleRate / 10).toInt()
        val buffer = AVAudioPCMBuffer(format, frames.toUInt())!!
        buffer.frameLength = frames.toUInt()
        val voice = buffer.floatChannelData!![0]!!
        for (i in 0 until frames) voice[i] = (0.5 * sin(2 * PI * 440 * i / sampleRate)).toFloat()
        return buffer
    }

    private fun peak(buffer: AVAudioPCMBuffer): Int {
        val samples = buffer.int16ChannelData!![0]!!
        return (0 until buffer.frameLength.toInt()).maxOf { abs(samples[it].toInt()) }
    }
}
