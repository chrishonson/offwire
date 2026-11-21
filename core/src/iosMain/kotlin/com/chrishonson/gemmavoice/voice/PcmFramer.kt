package com.chrishonson.gemmavoice.voice

/** Regroups PCM byte chunks of any length into frames of exactly [frameBytes] bytes, keeping the remainder. */
class PcmFramer(private val frameBytes: Int) {
    private var pending = ByteArray(0)

    init {
        require(frameBytes > 0) { "frameBytes must be positive" }
    }

    fun push(chunk: ByteArray): List<ByteArray> {
        val data = pending + chunk
        val frames = ArrayList<ByteArray>(data.size / frameBytes)
        var offset = 0
        while (data.size - offset >= frameBytes) {
            frames += data.copyOfRange(offset, offset + frameBytes)
            offset += frameBytes
        }
        pending = data.copyOfRange(offset, data.size)
        return frames
    }
}
