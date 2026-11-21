package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.voice.SpeechToText
import kotlinx.coroutines.delay

class FakeSpeechToText(
    transcripts: List<String> = emptyList(),
    var defaultTranscript: String = "speech"
) : SpeechToText {
    override var isAvailable: Boolean = true
    private val scripted = ArrayDeque(transcripts)
    var error: Throwable? = null
    var delayMs: Long = 0L

    val pcmInputs = mutableListOf<ByteArray>()
    val callCount: Int
        get() = pcmInputs.size

    fun script(vararg transcripts: String) {
        scripted.addAll(transcripts)
    }

    override suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String {
        pcmInputs.add(pcm16)
        if (delayMs > 0L) delay(delayMs)
        error?.let { throw it }
        return scripted.removeFirstOrNull() ?: defaultTranscript
    }
}
