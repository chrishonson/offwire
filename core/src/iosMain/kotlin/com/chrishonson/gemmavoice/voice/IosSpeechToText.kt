package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

private const val MILLIS_PER_SECOND = 1000

class IosSpeechToText(private val bridge: SttBridge) : SpeechToText {
    private val mutex = Mutex()

    override val isAvailable: Boolean
        get() = true

    override suspend fun transcribe(pcm16: ByteArray, sampleRate: Int): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val startMs = nowMs()
                val text = bridge.transcribe(pcm16, sampleRate).trim()
                val elapsedMs = nowMs() - startMs
                NSLog("Offwire: transcribed ${pcm16.size} PCM bytes to ${text.length} chars in ${elapsedMs}ms")
                text
            }
        }

    private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
}
