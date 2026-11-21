package com.chrishonson.gemmavoice.voice

object WavEncoder {
    const val HEADER_SIZE = 44
    private const val RIFF_CHUNK_OVERHEAD = 36
    private const val FMT_CHUNK_SIZE = 16
    private const val PCM_AUDIO_FORMAT = 1
    private const val MONO_CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = 2
    private const val BYTE_MASK = 0xFF
    private const val BYTE_INDEX_3 = 3
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val SHIFT_24 = 24

    private const val OFFSET_CHUNK_ID = 0
    private const val OFFSET_CHUNK_SIZE = 4
    private const val OFFSET_FORMAT = 8
    private const val OFFSET_SUBCHUNK1_ID = 12
    private const val OFFSET_SUBCHUNK1_SIZE = 16
    private const val OFFSET_AUDIO_FORMAT = 20
    private const val OFFSET_NUM_CHANNELS = 22
    private const val OFFSET_SAMPLE_RATE = 24
    private const val OFFSET_BYTE_RATE = 28
    private const val OFFSET_BLOCK_ALIGN = 32
    private const val OFFSET_BITS_PER_SAMPLE = 34
    private const val OFFSET_SUBCHUNK2_ID = 36
    private const val OFFSET_SUBCHUNK2_SIZE = 40

    fun encodePcm16Mono(pcm: ByteArray, sampleRate: Int): ByteArray {
        val output = ByteArray(HEADER_SIZE + pcm.size)
        val byteRate = sampleRate * MONO_CHANNELS * BYTES_PER_SAMPLE
        val blockAlign = MONO_CHANNELS * BYTES_PER_SAMPLE

        writeAscii(output, OFFSET_CHUNK_ID, "RIFF")
        writeIntLe(output, OFFSET_CHUNK_SIZE, RIFF_CHUNK_OVERHEAD + pcm.size)
        writeAscii(output, OFFSET_FORMAT, "WAVE")
        writeAscii(output, OFFSET_SUBCHUNK1_ID, "fmt ")
        writeIntLe(output, OFFSET_SUBCHUNK1_SIZE, FMT_CHUNK_SIZE)
        writeShortLe(output, OFFSET_AUDIO_FORMAT, PCM_AUDIO_FORMAT)
        writeShortLe(output, OFFSET_NUM_CHANNELS, MONO_CHANNELS)
        writeIntLe(output, OFFSET_SAMPLE_RATE, sampleRate)
        writeIntLe(output, OFFSET_BYTE_RATE, byteRate)
        writeShortLe(output, OFFSET_BLOCK_ALIGN, blockAlign)
        writeShortLe(output, OFFSET_BITS_PER_SAMPLE, BITS_PER_SAMPLE)
        writeAscii(output, OFFSET_SUBCHUNK2_ID, "data")
        writeIntLe(output, OFFSET_SUBCHUNK2_SIZE, pcm.size)

        pcm.copyInto(output, destinationOffset = HEADER_SIZE)
        return output
    }

    private fun writeAscii(buffer: ByteArray, offset: Int, tag: String) {
        for (i in tag.indices) {
            buffer[offset + i] = tag[i].code.toByte()
        }
    }

    private fun writeIntLe(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value ushr SHIFT_8) and BYTE_MASK).toByte()
        buffer[offset + 2] = ((value ushr SHIFT_16) and BYTE_MASK).toByte()
        buffer[offset + BYTE_INDEX_3] = ((value ushr SHIFT_24) and BYTE_MASK).toByte()
    }

    private fun writeShortLe(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value ushr SHIFT_8) and BYTE_MASK).toByte()
    }
}
