package com.chrishonson.gemmavoice.voice

/** Native speech recognizer. Called from any thread; must be safe to call once at a time. */
interface SttBridge {
    /** Returns the transcript of 16-bit mono PCM at [sampleRate], or throws. Blocking. */
    fun transcribe(pcm16: ByteArray, sampleRate: Int): String
    fun close()
}

/** Native voice activity detector over 512-sample 16 kHz frames. */
interface VadBridge {
    /** Speech probability for one frame of samples in -1..1, in 0..1. Keeps state between calls. */
    fun speechProbability(frame: FloatArray): Float
    fun reset()
    fun close()
}
