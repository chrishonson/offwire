package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IosVadTest {
    private class FakeBridge : VadBridge {
        var lastFrame: FloatArray? = null
        var resets = 0

        override fun speechProbability(frame: FloatArray): Float {
            lastFrame = frame
            return 0.75f
        }

        override fun reset() {
            resets++
        }

        override fun close() = Unit
    }

    @Test
    fun frameSamplesIs512() {
        assertEquals(512, IosVad(FakeBridge()).frameSamples)
    }

    @Test
    fun speechProbabilityDelegates() {
        val bridge = FakeBridge()
        val frame = floatArrayOf(0.1f, -0.2f)

        assertEquals(0.75f, IosVad(bridge).speechProbability(frame))
        assertContentEquals(frame, bridge.lastFrame)
    }

    @Test
    fun resetDelegates() {
        val bridge = FakeBridge()

        IosVad(bridge).reset()

        assertEquals(1, bridge.resets)
    }
}
