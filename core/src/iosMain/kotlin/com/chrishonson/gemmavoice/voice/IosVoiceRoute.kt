@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.voice

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.Foundation.NSError
import platform.Foundation.NSLog

private const val CONVERT_HEADROOM_FRAMES = 32
private const val SPEECH_SAMPLE_RATE = 24_000.0

/**
 * Where reply speech plays while the microphone is open. iOS echo cancellation removes only audio that plays through
 * the same voice-processing engine as the mic, so [IosAudioStream] attaches [player] to its engine and
 * [IosSpeechSynthesizer] sends speech there. With the mic closed, [player] is null and speech plays directly.
 * Main thread only.
 */
class IosVoiceRoute {
    val speechFormat: AVAudioFormat = AVAudioFormat(standardFormatWithSampleRate = SPEECH_SAMPLE_RATE, channels = 1u)

    var player: AVAudioPlayerNode? = null
        internal set

    /** Called when the mic engine stops [player], which drops scheduled speech without reliably reporting it played. */
    internal var onPlayerStopped: (() -> Unit)? = null
}

/** Converts [input] into a new buffer of [targetFormat], resampling as needed. Null if conversion fails. */
internal fun convertPcm(
    converter: AVAudioConverter,
    targetFormat: AVAudioFormat,
    input: AVAudioPCMBuffer
): AVAudioPCMBuffer? {
    val ratio = targetFormat.sampleRate / input.format.sampleRate
    val capacity = (input.frameLength.toDouble() * ratio).toInt() + CONVERT_HEADROOM_FRAMES
    val output = AVAudioPCMBuffer(targetFormat, capacity.toUInt()) ?: return null
    var supplied = false
    val failed = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val status = converter.convertToBuffer(output, error.ptr) { _, inputStatus ->
            val data = if (supplied) null else input
            supplied = true
            inputStatus?.pointed?.value =
                if (data == null) AVAudioConverterInputStatus_NoDataNow else AVAudioConverterInputStatus_HaveData
            data
        }
        val failed = status == AVAudioConverterOutputStatus_Error
        if (failed) NSLog("PCM conversion failed: ${error.value?.localizedDescription}")
        failed
    }
    return if (failed) null else output
}

/**
 * Converts buffers to [target], rebuilding the converter whenever the source format changes. Voice-processing input
 * can arrive with several channels, the processed voice on the first, and a plain downmix of those is silent, so
 * only the first channel is kept.
 */
internal class PcmConverter(private val target: AVAudioFormat) {
    private var converter: AVAudioConverter? = null

    fun convert(input: AVAudioPCMBuffer): AVAudioPCMBuffer? {
        val source = input.format
        if (converter?.inputFormat?.isEqual(source) != true) {
            converter = AVAudioConverter(source, target)?.apply {
                if (source.channelCount > target.channelCount) channelMap = listOf(0)
            }
        }
        return converter?.let { convertPcm(it, target, input) }
    }
}
