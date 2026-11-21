package com.chrishonson.gemmavoice.voice

import kotlin.math.abs
import kotlin.math.max

private const val DEFAULT_THRESHOLD = 0.02f
private const val DEFAULT_PAD_SECONDS = 0.05f

/**
 * Drops the silence a TTS model puts before and after each clause, which otherwise delays first sound and
 * leaves gaps between clauses. Quiet chunks are held back and released only if speech follows, so trailing
 * silence is cut without lookahead; [flush] keeps a short pad of it so clauses do not run together.
 */
class SilenceTrimmer(
    sampleRate: Int,
    private val threshold: Float = DEFAULT_THRESHOLD,
    padSeconds: Float = DEFAULT_PAD_SECONDS
) {
    private val pad = (sampleRate * padSeconds).toInt()
    private var started = false
    private val held = mutableListOf<FloatArray>()

    fun feed(chunk: FloatArray): List<FloatArray> {
        val firstLoud = chunk.indexOfFirst { abs(it) > threshold }
        return when {
            firstLoud < 0 -> {
                if (started) held.add(chunk)
                emptyList()
            }
            started -> release(chunk)
            else -> {
                started = true
                release(chunk.copyOfRange(max(0, firstLoud - pad), chunk.size))
            }
        }
    }

    fun flush(): FloatArray? {
        if (held.isEmpty()) return null
        val total = held.sumOf { it.size }
        val joined = FloatArray(total)
        var offset = 0
        for (chunk in held) {
            chunk.copyInto(joined, offset)
            offset += chunk.size
        }
        held.clear()
        return joined.copyOfRange(0, minOf(pad, total))
    }

    private fun release(chunk: FloatArray): List<FloatArray> {
        val out = held.toList() + listOf(chunk)
        held.clear()
        return out
    }
}
