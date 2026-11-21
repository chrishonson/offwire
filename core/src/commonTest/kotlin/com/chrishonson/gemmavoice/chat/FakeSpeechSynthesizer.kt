package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.voice.SpeechSynthesizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSpeechSynthesizer : SpeechSynthesizer {
    val spokenTexts = mutableListOf<String>()
    val enqueuedTexts = mutableListOf<String>()
    val speechRates = mutableListOf<Float>()
    var stopCount: Int = 0
    var warmUpCount: Int = 0

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _offlineVoiceAvailable = MutableStateFlow(true)
    override val offlineVoiceAvailable: StateFlow<Boolean> = _offlineVoiceAvailable.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    override fun speak(text: String) {
        spokenTexts.add(text)
        _isSpeaking.value = true
    }

    override fun enqueue(text: String) {
        enqueuedTexts.add(text)
        _isSpeaking.value = true
    }

    override fun stop() {
        stopCount++
        _isSpeaking.value = false
    }

    override fun warmUp() {
        warmUpCount++
    }

    override fun setSpeechRate(rate: Float) {
        speechRates.add(rate)
    }

    fun setSpeaking(speaking: Boolean) {
        _isSpeaking.value = speaking
    }

    fun setOfflineVoiceAvailable(available: Boolean) {
        _offlineVoiceAvailable.value = available
    }

    fun setOutputLevel(level: Float) {
        _outputLevel.value = level
    }

    fun finishSpeaking() {
        _isSpeaking.value = false
    }
}
