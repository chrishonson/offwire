package com.chrishonson.gemmavoice.router

/**
 * The MobileBERT router from scripts/eval/mobilebert_router.py, ported as it is.
 *
 * One small fine-tuned classifier (MobileBERT-tiny, int8 ONNX) reads the whole transcript as it was said, with no
 * normalizing, no head words and no splitting, and scores four classes in one pass: SAVE, ACTION, NONE and
 * SAVE+ACTION. The top class is the route; there are no thresholds. Measured on the same 601 held-out sentences as
 * [EmbeddingIntentRouter] (scripts/eval/router_heldout.py --setups mobilebert).
 */
val CLASSIFIER_LABELS = listOf("SAVE", "ACTION", "NONE", "SAVE+ACTION")
private const val SAVE_CLASS = 0
private const val ACTION_CLASS = 1
private const val NONE_CLASS = 2
private const val BOTH_CLASS = 3

/** Token ids in ([CLS] ... [SEP]), one logit per [CLASSIFIER_LABELS] out. The MobileBERT ONNX model on Android. */
fun interface IntentClassifier {
    fun logits(ids: IntArray): FloatArray
}

class ClassifierIntentRouter(
    private val tokenizer: WordPieceTokenizer,
    private val classifier: IntentClassifier,
    private val clockMs: () -> Long = { 0L }
) {
    fun decide(transcript: String): RouteDecision {
        val start = clockMs()
        return classifierDecision(classifier.logits(tokenizer.encode(transcript))).copy(millis = clockMs() - start)
    }
}

/**
 * The route for one set of logits: the top class's tools, and for SAVE+ACTION both, the higher-scoring one first.
 * Margins are each tool's best class (its own or SAVE+ACTION) minus NONE, for the speed readout and the logs.
 */
fun classifierDecision(logits: FloatArray): RouteDecision {
    require(logits.size == CLASSIFIER_LABELS.size) { "expected ${CLASSIFIER_LABELS.size} logits, got ${logits.size}" }
    val top = logits.indices.maxBy { logits[it] }
    val intents = when (top) {
        SAVE_CLASS -> listOf(ToolIntent.SAVE)
        ACTION_CLASS -> listOf(ToolIntent.ACTION)
        BOTH_CLASS -> if (logits[ACTION_CLASS] > logits[SAVE_CLASS]) {
            listOf(ToolIntent.ACTION, ToolIntent.SAVE)
        } else {
            listOf(ToolIntent.SAVE, ToolIntent.ACTION)
        }
        else -> emptyList()
    }
    val none = logits[NONE_CLASS].toDouble()
    val margins = mapOf(
        ToolIntent.SAVE to maxOf(logits[SAVE_CLASS], logits[BOTH_CLASS]) - none,
        ToolIntent.ACTION to maxOf(logits[ACTION_CLASS], logits[BOTH_CLASS]) - none
    )
    return RouteDecision(intents, margins)
}
