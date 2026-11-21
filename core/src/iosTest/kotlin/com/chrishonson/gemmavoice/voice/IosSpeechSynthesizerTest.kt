@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.voice

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineManualRenderingModeOffline
import platform.AVFAudio.AVAudioPlayerNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosSpeechSynthesizerTest {
    @Test
    fun speechEndsWhenTheMicEngineStopsThePlayer() {
        val route = IosVoiceRoute()
        // An offline engine stands in for the mic's, so the test needs no audio hardware.
        val engine = AVAudioEngine()
        engine.enableManualRenderingMode(AVAudioEngineManualRenderingModeOffline, route.speechFormat, 4096u, null)
        val player = AVAudioPlayerNode()
        engine.attachNode(player)
        engine.connect(player, engine.mainMixerNode, route.speechFormat)
        route.player = player
        val synthesizer = IosSpeechSynthesizer(route)

        synthesizer.enqueue("A reply cut off by a route change.")
        assertTrue(synthesizer.isSpeaking.value)

        player.stop()
        route.onPlayerStopped?.invoke()

        assertFalse(synthesizer.isSpeaking.value, "a stopped player must not leave the mic ignored for good")
    }
}
