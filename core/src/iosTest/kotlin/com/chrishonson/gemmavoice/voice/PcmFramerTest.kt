package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmFramerTest {
    private fun bytes(range: IntRange) = ByteArray(range.count()) { (range.first + it).toByte() }

    @Test
    fun smallChunksAccumulateUntilAFrameIsComplete() {
        val framer = PcmFramer(4)
        assertTrue(framer.push(bytes(0..0)).isEmpty())
        assertTrue(framer.push(bytes(1..2)).isEmpty())
        val frames = framer.push(bytes(3..3))
        assertEquals(1, frames.size)
        assertContentEquals(bytes(0..3), frames[0])
    }

    @Test
    fun chunkSpanningBoundariesYieldsWholeFramesAndKeepsTail() {
        val framer = PcmFramer(4)
        val frames = framer.push(bytes(0..9))
        assertEquals(2, frames.size)
        assertContentEquals(bytes(0..3), frames[0])
        assertContentEquals(bytes(4..7), frames[1])
        val next = framer.push(bytes(10..11))
        assertEquals(1, next.size)
        assertContentEquals(bytes(8..11), next[0])
    }

    @Test
    fun byteOrderIsPreservedAcrossChunks() {
        val framer = PcmFramer(3)
        val out = (framer.push(bytes(0..1)) + framer.push(bytes(2..6)) + framer.push(bytes(7..8)))
            .fold(ByteArray(0)) { acc, frame -> acc + frame }
        assertContentEquals(bytes(0..8), out)
    }

    @Test
    fun defaultVoiceFrameIs1024Bytes() {
        assertEquals(1024, VoiceConfig().frameBytes)
    }
}
