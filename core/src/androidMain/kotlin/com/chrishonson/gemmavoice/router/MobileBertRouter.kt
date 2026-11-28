package com.chrishonson.gemmavoice.router

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import java.nio.IntBuffer

/**
 * The MobileBERT router's model in the core assets: MediaPipe Model Maker's MobileBERT-tiny fine-tuned as a 4-way
 * classifier on the router's training sentences, int8 ONNX (scripts/eval/train_mobilebert_router.py; LFS). It reads
 * the same WordPiece vocab as MiniLM.
 */
const val MOBILEBERT_MODEL_ASSET = "$ROUTER_ASSET_DIR/mobilebert-router.onnx"

private const val INTRA_OP_THREADS = 1
private const val WORD_IDS = "input_word_ids"
private const val MASK = "input_mask"
private const val TYPE_IDS = "input_type_ids"

/** MobileBERT on ONNX Runtime: int32 ids, mask and type ids in, four logits out. One thread, no padding. */
class MobileBertClassifier(modelBytes: ByteArray) : IntentClassifier, AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(INTRA_OP_THREADS) }
    private val session: OrtSession = env.createSession(modelBytes, options)

    override fun logits(ids: IntArray): FloatArray {
        val shape = longArrayOf(1, ids.size.toLong())
        val feed = mutableMapOf<String, OnnxTensor>()
        try {
            feed[WORD_IDS] = OnnxTensor.createTensor(env, IntBuffer.wrap(ids), shape)
            feed[MASK] = OnnxTensor.createTensor(env, IntBuffer.wrap(IntArray(ids.size) { 1 }), shape)
            feed[TYPE_IDS] = OnnxTensor.createTensor(env, IntBuffer.wrap(IntArray(ids.size)), shape)
            session.run(feed).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<FloatArray>)[0]
            }
        } finally {
            feed.values.forEach { it.close() }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }
}

/**
 * The MobileBERT router the app runs, loaded from the core assets on first use (about 37 MB and an ONNX session), so
 * [warmUp] runs it once, off the main thread, while Gemma loads.
 */
class AndroidMobileBertRouter(private val context: Context) : TurnRouter, AutoCloseable {
    private var loaded: Pair<ClassifierIntentRouter, MobileBertClassifier>? = null

    @Synchronized
    override fun warmUp() {
        router().decide("hello")
    }

    @Synchronized
    override fun decide(transcript: String): RouteDecision = router().decide(transcript)

    private fun router(): ClassifierIntentRouter = loaded?.first ?: load().also { loaded = it }.first

    private fun load(): Pair<ClassifierIntentRouter, MobileBertClassifier> {
        val assets = context.assets
        val vocab = parseVocab(assets.open(ROUTER_VOCAB_ASSET).bufferedReader().use { it.readText() })
        val model = assets.open(MOBILEBERT_MODEL_ASSET).use { it.readBytes() }
        val classifier = MobileBertClassifier(model)
        return ClassifierIntentRouter(WordPieceTokenizer(vocab, ::nfd), classifier, SystemClock::elapsedRealtime) to
            classifier
    }

    @Synchronized
    override fun close() {
        loaded?.second?.close()
        loaded = null
    }
}
