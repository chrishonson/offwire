@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.voice

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

private const val TAP_BUFFER_FRAMES = 1024u

/**
 * Captures the microphone through `AVAudioEngine` with the voice-processing input enabled and emits
 * [VoiceConfig.frameBytes]-sized frames of 16-bit mono PCM at [VoiceConfig.sampleRateHz]. Echo cancellation only
 * removes audio played through this same engine, so while the mic is open the engine also carries a player node for
 * reply speech, handed to the synthesizer through [route]. All AVFoundation calls run on the main thread. Cancelling
 * the flow releases the session.
 */
class IosAudioStream(
    private val config: VoiceConfig = VoiceConfig(),
    private val route: IosVoiceRoute = IosVoiceRoute()
) : AudioStream {

    override fun frames(): Flow<ByteArray> = callbackFlow {
        val framer = PcmFramer(config.frameBytes)
        activateSession()
        val engine = AVAudioEngine()
        val input = engine.inputNode
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!input.setVoiceProcessingEnabled(true, error.ptr)) {
                NSLog("Voice processing unavailable: ${error.value?.localizedDescription}")
            }
        }
        // Build the whole graph before reading the input format: adding the output path reconfigures the shared
        // voice-processing I/O, which can change the input's channels and sample rate.
        val player = AVAudioPlayerNode()
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, route.speechFormat)
        val targetFormat = AVAudioFormat(
            commonFormat = AVAudioPCMFormatInt16,
            sampleRate = config.sampleRateHz.toDouble(),
            channels = 1u,
            interleaved = true
        )
        val converter = PcmConverter(targetFormat)

        fun start() {
            val inputFormat = input.outputFormatForBus(0u)
            NSLog("Mic input format: $inputFormat")
            input.installTapOnBus(0u, TAP_BUFFER_FRAMES, inputFormat) { buffer, _ ->
                if (buffer != null) {
                    val bytes = converter.convert(buffer)?.let(::pcmBytes) ?: ByteArray(0)
                    for (frame in framer.push(bytes)) {
                        if (trySend(frame).isFailure) NSLog("Mic frame dropped, collector is behind")
                    }
                }
            }
            engine.prepare()
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                check(engine.startAndReturnError(error.ptr)) {
                    "Audio engine failed: ${error.value?.localizedDescription}"
                }
            }
            player.play()
        }

        start()
        route.player = player
        // A configuration change stops the engine and leaves the mic deaf, so rebuild the tap and start again.
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioEngineConfigurationChangeNotification,
            engine,
            NSOperationQueue.mainQueue
        ) { _ ->
            NSLog("Audio engine configuration changed, restarting the mic")
            input.removeTapOnBus(0u)
            // The restarted engine leaves the player reporting it plays while it never renders what is scheduled.
            player.stop()
            route.onPlayerStopped?.invoke()
            runCatching { start() }.onFailure {
                NSLog("Mic restart failed: ${it.message}")
                close(it)
            }
        }

        awaitClose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            if (route.player === player) route.player = null
            player.stop()
            route.onPlayerStopped?.invoke()
            input.removeTapOnBus(0u)
            engine.stop()
            deactivateSession()
        }
    }.buffer(kotlinx.coroutines.channels.Channel.BUFFERED).flowOn(Dispatchers.Main)

    private fun activateSession() {
        val session = AVAudioSession.sharedInstance()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val options = AVAudioSessionCategoryOptionDefaultToSpeaker or AVAudioSessionCategoryOptionAllowBluetooth
            val categorySet = session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                AVAudioSessionModeVoiceChat,
                options,
                error.ptr
            )
            check(categorySet) { "Audio session category failed: ${error.value?.localizedDescription}" }
            check(session.setActive(true, error.ptr)) { "Audio session failed: ${error.value?.localizedDescription}" }
        }
    }

    private fun deactivateSession() {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioSession.sharedInstance()
                .setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error.ptr)
        }
    }

    private fun pcmBytes(buffer: AVAudioPCMBuffer): ByteArray {
        val samples = buffer.int16ChannelData?.get(0)
        val size = buffer.frameLength.toInt() * VoiceConfig.BYTES_PER_SAMPLE
        return samples?.reinterpret<ByteVar>()?.readBytes(size) ?: ByteArray(0)
    }
}
