package com.chrishonson.gemmavoice.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

private const val ASSET_FILE_NAME = "silero_vad.onnx"
private const val FRAME_SAMPLES = 512
private const val CONTEXT_SAMPLES = 64
private const val WINDOW_SAMPLES = CONTEXT_SAMPLES + FRAME_SAMPLES
private const val STATE_LAYERS = 2L
private const val STATE_BATCH = 1L
private const val STATE_HIDDEN = 128L
private const val STATE_SIZE = 256
private const val SAMPLE_RATE = 16000L
private const val INTRA_OP_THREADS = 1
private const val INPUT_NAME = "input"
private const val STATE_NAME = "state"
private const val SR_NAME = "sr"
private const val OUTPUT_NAME = "output"
private const val STATE_N_NAME = "stateN"

class SileroVad(context: Context) : VoiceActivityDetector {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionOptions: OrtSession.SessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(INTRA_OP_THREADS)
    }
    private val session: OrtSession = env.createSession(
        context.assets.open(ASSET_FILE_NAME).use { it.readBytes() },
        sessionOptions
    )

    private val state = FloatArray(STATE_SIZE)
    private val contextWindow = FloatArray(CONTEXT_SAMPLES)
    private val inputWindow = FloatArray(WINDOW_SAMPLES)

    override val frameSamples: Int = FRAME_SAMPLES

    override fun speechProbability(frame: FloatArray): Float {
        contextWindow.copyInto(inputWindow, destinationOffset = 0)
        inputWindow.fill(0f, fromIndex = CONTEXT_SAMPLES, toIndex = WINDOW_SAMPLES)
        val copyCount = minOf(frame.size, FRAME_SAMPLES)
        frame.copyInto(
            destination = inputWindow,
            destinationOffset = CONTEXT_SAMPLES,
            startIndex = 0,
            endIndex = copyCount
        )
        inputWindow.copyInto(
            destination = contextWindow,
            destinationOffset = 0,
            startIndex = WINDOW_SAMPLES - CONTEXT_SAMPLES,
            endIndex = WINDOW_SAMPLES
        )

        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(inputWindow),
            longArrayOf(1L, WINDOW_SAMPLES.toLong())
        )
        val stateTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(state),
            longArrayOf(STATE_LAYERS, STATE_BATCH, STATE_HIDDEN)
        )
        val srTensor = OnnxTensor.createTensor(env, SAMPLE_RATE)

        return try {
            val inputs = mapOf(
                INPUT_NAME to inputTensor,
                STATE_NAME to stateTensor,
                SR_NAME to srTensor
            )
            session.run(inputs).use { result ->
                val outputTensor = result.get(OUTPUT_NAME).get() as OnnxTensor
                val stateNTensor = result.get(STATE_N_NAME).get() as OnnxTensor
                val probability = outputTensor.floatBuffer.get(0)
                stateNTensor.floatBuffer.get(state)
                probability
            }
        } finally {
            inputTensor.close()
            stateTensor.close()
            srTensor.close()
        }
    }

    override fun reset() {
        state.fill(0f)
        contextWindow.fill(0f)
    }

    fun close() {
        session.close()
        sessionOptions.close()
    }
}
