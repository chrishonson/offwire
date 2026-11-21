package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Pending-utterance counter behind isSpeaking: true from the first queued chunk until the last one ends or stop runs.
internal class SpeakingState {
    private var pending = 0
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun speak() {
        pending = 1
        _isSpeaking.value = true
    }

    fun enqueue() {
        pending += 1
        _isSpeaking.value = true
    }

    fun finished() {
        if (pending > 0) pending -= 1
        if (pending == 0) _isSpeaking.value = false
    }

    fun stop() {
        pending = 0
        _isSpeaking.value = false
    }
}
