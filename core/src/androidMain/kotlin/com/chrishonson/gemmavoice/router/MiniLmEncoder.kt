package com.chrishonson.gemmavoice.router

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.sqrt

/**
 * The router's files in the core assets: the MiniLM ONNX model (LFS; scripts/fetch-router-model.sh puts it in
 * place), its WordPiece vocab and the fitted layer.
 */
const val ROUTER_ASSET_DIR = "router"
const val ROUTER_MODEL_ASSET = "$ROUTER_ASSET_DIR/all-MiniLM-L6-v2.onnx"
const val ROUTER_VOCAB_ASSET = "$ROUTER_ASSET_DIR/vocab.txt"
const val ROUTER_CONFIG_ASSET = "$ROUTER_ASSET_DIR/embed-all-MiniLM-L6-v2-mlp.json"

private const val INTRA_OP_THREADS = 1
private const val INPUT_IDS = "input_ids"
private const val ATTENTION_MASK = "attention_mask"
private const val TOKEN_TYPE_IDS = "token_type_ids"

/** NFD for the tokenizer's accent stripping. */
fun nfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

/**
 * all-MiniLM-L6-v2 on ONNX Runtime: WordPiece ids in, token vectors out, mean-pooled and L2-normalized, as
 * OnnxEncoder in scripts/eval/embed_router.py does. One thread, one message at a time, no padding.
 */
class MiniLmEncoder(
    modelBytes: ByteArray,
    private val tokenizer: WordPieceTokenizer
) : SentenceEncoder, AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(INTRA_OP_THREADS) }
    private val session: OrtSession = env.createSession(modelBytes, options)
    private val inputs = session.inputNames

    override fun encode(text: String): FloatArray {
        val ids = tokenizer.encode(text).map { it.toLong() }.toLongArray()
        val shape = longArrayOf(1, ids.size.toLong())
        val feed = mutableMapOf<String, OnnxTensor>()
        try {
            feed[INPUT_IDS] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape)
            if (ATTENTION_MASK in inputs) {
                feed[ATTENTION_MASK] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape)
            }
            if (TOKEN_TYPE_IDS in inputs) {
                feed[TOKEN_TYPE_IDS] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape)
            }
            session.run(feed).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = (result[0].value as Array<Array<FloatArray>>)[0]
                return meanPoolUnit(hidden)
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

/** Mean of the token vectors (no padding, so every token counts), scaled to length 1. */
fun meanPoolUnit(hidden: Array<FloatArray>): FloatArray {
    val dims = hidden.first().size
    val pooled = FloatArray(dims)
    for (token in hidden) for (i in 0 until dims) pooled[i] += token[i]
    for (i in 0 until dims) pooled[i] /= hidden.size
    val norm = sqrt(pooled.sumOf { it.toDouble() * it }).toFloat()
    if (norm > 0f) for (i in 0 until dims) pooled[i] /= norm
    return pooled
}

/**
 * The router the app runs, loaded from the core assets on first use. Loading reads about 90 MB and builds an ONNX
 * session, so [warmUp] runs it once, off the main thread, while Gemma loads.
 */
class AndroidIntentRouter(private val context: Context) : TurnRouter, AutoCloseable {
    private var loaded: Pair<EmbeddingIntentRouter, MiniLmEncoder>? = null

    @Synchronized
    override fun warmUp() {
        router().decide("hello")
    }

    @Synchronized
    override fun decide(transcript: String): RouteDecision = router().decide(transcript)

    private fun router(): EmbeddingIntentRouter = loaded?.first ?: load().also { loaded = it }.first

    private fun load(): Pair<EmbeddingIntentRouter, MiniLmEncoder> {
        val assets = context.assets
        val vocab = parseVocab(assets.open(ROUTER_VOCAB_ASSET).bufferedReader().use { it.readText() })
        val config = parseRouterConfig(assets.open(ROUTER_CONFIG_ASSET).bufferedReader().use { it.readText() })
        val model = assets.open(ROUTER_MODEL_ASSET).use { it.readBytes() }
        val encoder = MiniLmEncoder(model, WordPieceTokenizer(vocab, ::nfd))
        return EmbeddingIntentRouter(config, encoder, SystemClock::elapsedRealtime) to encoder
    }

    @Synchronized
    override fun close() {
        loaded?.second?.close()
        loaded = null
    }
}
