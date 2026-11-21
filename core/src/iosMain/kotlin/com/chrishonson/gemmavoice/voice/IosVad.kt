package com.chrishonson.gemmavoice.voice

private const val FRAME_SAMPLES = 512

class IosVad(
    private val bridge: VadBridge,
    override val frameSamples: Int = FRAME_SAMPLES
) : VoiceActivityDetector {
    override fun speechProbability(frame: FloatArray): Float = bridge.speechProbability(frame)

    override fun reset() = bridge.reset()
}
