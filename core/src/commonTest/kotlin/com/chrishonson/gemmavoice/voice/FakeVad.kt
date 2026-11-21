package com.chrishonson.gemmavoice.voice

class FakeVad(
    config: VoiceConfig = VoiceConfig(),
    probabilities: Iterable<Float> = emptyList()
) : EnergyVad(config) {
    private val scripted = ArrayDeque<Float>().apply {
        addAll(probabilities)
    }
    var fallbackProbability: Float? = null
    var resetCount: Int = 0
        private set

    fun enqueue(vararg probabilities: Float) {
        for (p in probabilities) {
            scripted.addLast(p)
        }
    }

    fun enqueueFrames(probability: Float, count: Int) {
        repeat(count) {
            scripted.addLast(probability)
        }
    }

    override fun speechProbability(frame: FloatArray): Float {
        if (scripted.isNotEmpty()) {
            return scripted.removeFirst()
        }
        return fallbackProbability ?: super.speechProbability(frame)
    }

    override fun reset() {
        resetCount++
    }
}
