package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val FLAT_OUTPUT_LEVEL: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()

interface SpeechSynthesizer {
    val isSpeaking: StateFlow<Boolean>
    val offlineVoiceAvailable: StateFlow<Boolean>
    val outputLevel: StateFlow<Float>
        get() = FLAT_OUTPUT_LEVEL
    fun speak(text: String)
    fun enqueue(text: String)
    fun stop()
    fun setSpeechRate(rate: Float) {}

    // Called when a reply starts generating so the output path is already open when speech arrives.
    fun warmUp() {}
}
