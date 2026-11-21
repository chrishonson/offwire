@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.chrishonson.gemmavoice.voice

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNodeCompletionDataPlayedBack
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.currentLocale
import platform.Foundation.countryCode
import platform.Foundation.languageCode
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Speaks with `AVSpeechSynthesizer`. While the mic is open ([IosVoiceRoute.player] set), speech is rendered to
 * buffers and played through the mic's voice-processing engine, so echo cancellation removes it from the mic and
 * the app does not hear itself. Otherwise it plays directly.
 */
class IosSpeechSynthesizer(private val route: IosVoiceRoute = IosVoiceRoute()) : SpeechSynthesizer {
    private val state = SpeakingState()
    override val isSpeaking: StateFlow<Boolean> = state.isSpeaking
    override val offlineVoiceAvailable: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val voice: AVSpeechSynthesisVoice? = selectBestVoice()

    // Speech sent to the engine is done when its last buffer has played, not when the synthesizer finishes writing.
    private val enginePlayback = mutableMapOf<AVSpeechUtterance, EnginePlayback>()
    private var generation = 0
    private val converter = PcmConverter(route.speechFormat)

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            utteranceDone(didFinishSpeechUtterance)
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
            utteranceDone(didCancelSpeechUtterance)
        }
    }

    private val synthesizer: AVSpeechSynthesizer = AVSpeechSynthesizer().apply {
        // Never swap sessions under the mic. Direct playback is not cancelled from the mic, see play().
        usesApplicationAudioSession = true
        delegate = this@IosSpeechSynthesizer.delegate
    }

    init {
        route.onPlayerStopped = ::dropEnginePlayback
    }

    override fun speak(text: String) {
        val cleaned = speakableText(text)
        if (cleaned.isBlank()) return
        halt()
        state.speak()
        play(utterance(cleaned))
    }

    override fun enqueue(text: String) {
        val cleaned = speakableText(text)
        if (cleaned.isBlank()) return
        state.enqueue()
        play(utterance(cleaned))
    }

    override fun stop() {
        state.stop()
        halt()
    }

    private fun halt() {
        generation += 1
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        route.player?.let { player ->
            player.stop()
            player.play()
        }
    }

    private fun play(utterance: AVSpeechUtterance) {
        val player = route.player
        if (player == null) {
            synthesizer.speakUtterance(utterance)
            return
        }
        val playback = EnginePlayback(generation)
        enginePlayback[utterance] = playback
        synthesizer.writeUtterance(utterance) { buffer ->
            val pcm = buffer as? AVAudioPCMBuffer
            // Speech halted or dropped while still being written must not reach the player once it plays again.
            if (pcm != null && pcm.frameLength > 0u && playback.generation == generation) {
                converter.convert(pcm)?.let { out ->
                    onMain { playback.scheduled += 1 }
                    player.scheduleBuffer(out, AVAudioPlayerNodeCompletionDataPlayedBack) { _ ->
                        onMain {
                            playback.scheduled -= 1
                            finishIfPlayed(utterance, playback)
                        }
                    }
                }
            }
        }
    }

    // Speech on a stopped player never plays back, so it ends here instead of waiting for completions.
    private fun dropEnginePlayback() {
        if (enginePlayback.isEmpty()) return
        generation += 1
        enginePlayback.clear()
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        state.stop()
    }

    private fun utteranceDone(utterance: AVSpeechUtterance) {
        val playback = enginePlayback[utterance]
        if (playback == null) {
            state.finished()
        } else {
            playback.written = true
            finishIfPlayed(utterance, playback)
        }
    }

    // Speech cut off by speak or stop is dropped without touching the state, which they already reset.
    private fun finishIfPlayed(utterance: AVSpeechUtterance, playback: EnginePlayback) {
        if (playback.generation != generation) {
            if (playback.written) enginePlayback.remove(utterance)
            return
        }
        if (!playback.written || playback.scheduled > 0) return
        if (enginePlayback.remove(utterance) != null) state.finished()
    }

    fun shutdown() {
        stop()
        synthesizer.delegate = null
    }

    private fun utterance(text: String): AVSpeechUtterance = AVSpeechUtterance(string = text).apply {
        voice = this@IosSpeechSynthesizer.voice
        prefersAssistiveTechnologySettings = false
        volume = 1.0f
        preUtteranceDelay = 0.0
        postUtteranceDelay = 0.0
    }
}

private class EnginePlayback(val generation: Int) {
    var scheduled = 0
    var written = false
}

private fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

private fun selectBestVoice(): AVSpeechSynthesisVoice? {
    val locale = NSLocale.currentLocale
    val language = locale.languageCode
    val region = locale.countryCode
    val candidates = AVSpeechSynthesisVoice.speechVoices()
        .filterIsInstance<AVSpeechSynthesisVoice>()
        .filter { it.language.startsWith(language) }
    val best = candidates.maxWithOrNull(
        compareBy<AVSpeechSynthesisVoice>({ it.quality }, { it.language == "$language-$region" })
    ) ?: AVSpeechSynthesisVoice.voiceWithLanguage(null)
    NSLog("TTS chose voice ${best?.identifier} among ${candidates.size} for $language")
    return best
}
