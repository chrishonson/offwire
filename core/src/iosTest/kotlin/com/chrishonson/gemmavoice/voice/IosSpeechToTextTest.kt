package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SLOW_CALL_MS = 50L

class IosSpeechToTextTest {
    private class FakeBridge(private val reply: String) : SttBridge {
        val active = AtomicInt(0)
        val maxActive = AtomicInt(0)
        val calls = AtomicInt(0)

        override fun transcribe(pcm16: ByteArray, sampleRate: Int): String {
            val now = active.incrementAndGet()
            calls.incrementAndGet()
            while (true) {
                val seen = maxActive.value
                if (now <= seen || maxActive.compareAndSet(seen, now)) break
            }
            platform.posix.usleep((SLOW_CALL_MS * 1000).toUInt())
            active.decrementAndGet()
            return reply
        }

        override fun close() = Unit
    }

    @Test
    fun transcribeTrimsAndReturnsTheText() = runTest {
        val stt = IosSpeechToText(FakeBridge("  computer, what time is it?\n"))

        assertEquals("computer, what time is it?", stt.transcribe(ByteArray(4), 16000))
    }

    @Test
    fun isAvailable() {
        assertTrue(IosSpeechToText(FakeBridge("")).isAvailable)
    }

    @Test
    fun concurrentCallsRunOneAtATime() = runTest {
        val bridge = FakeBridge("ok")
        val stt = IosSpeechToText(bridge)

        List(2) { async { stt.transcribe(ByteArray(4), 16000) } }.awaitAll()

        assertEquals(2, bridge.calls.value)
        assertEquals(1, bridge.maxActive.value)
    }
}
