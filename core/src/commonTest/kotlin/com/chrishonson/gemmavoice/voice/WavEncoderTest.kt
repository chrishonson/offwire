package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WavEncoderTest {
    @Test
    fun encodesCanonical44ByteRiffWaveHeaderAndPcmPayload() {
        val pcm = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val wav = WavEncoder.encodePcm16Mono(pcm = pcm, sampleRate = 16000)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", wav.sliceArray(0..3).decodeToString())
        assertEquals(36 + pcm.size, readIntLe(wav, 4))
        assertEquals("WAVE", wav.sliceArray(8..11).decodeToString())
        assertEquals("fmt ", wav.sliceArray(12..15).decodeToString())
        assertEquals(16, readIntLe(wav, 16))
        assertEquals(1, readShortLe(wav, 20))
        assertEquals(1, readShortLe(wav, 22))
        assertEquals(16000, readIntLe(wav, 24))
        assertEquals(32000, readIntLe(wav, 28))
        assertEquals(2, readShortLe(wav, 32))
        assertEquals(16, readShortLe(wav, 34))
        assertEquals("data", wav.sliceArray(36..39).decodeToString())
        assertEquals(pcm.size, readIntLe(wav, 40))
        assertContentEquals(pcm, wav.sliceArray(44 until wav.size))
    }

    @Test
    fun encodesEmptyPcmToExact44ByteHeader() {
        val wav = WavEncoder.encodePcm16Mono(pcm = ByteArray(0), sampleRate = 16000)

        assertEquals(44, wav.size)
        assertEquals(36, readIntLe(wav, 4))
        assertEquals(0, readIntLe(wav, 40))
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}
