package com.chrishonson.gemmavoice.voice

interface SpeechToText {
    val isAvailable: Boolean
        get() = true

    suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String
}
