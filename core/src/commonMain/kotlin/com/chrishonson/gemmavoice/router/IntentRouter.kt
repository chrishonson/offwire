package com.chrishonson.gemmavoice.router

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.sqrt

/**
 * The embedding intent router from scripts/eval/embed_router.py (the embed_mlp setup), ported as it is.
 *
 * Before Gemma replies, a small sentence-embedding model (all-MiniLM-L6-v2) reads the transcript and picks which
 * tools, if any, this turn may see:
 *  1. The message is lowercased and the wake word and "can you" are dropped ([normalizeForRouting]).
 *  2. It is embedded whole and as its first [RouterConfig.headWords] words; the two vectors are averaged, because
 *     MiniLM follows the topic while the request is nearly always in the first words.
 *  3. A small layer fitted in Python (384 -> 64 ReLU -> 3, or a straight 384x3) scores SAVE, ACTION and NONE. A
 *     tool intent fires when its score beats NONE's by at least its fitted threshold.
 *  4. A longer message is also split into its sentences and clauses ([segments]), each scored the same way, and each
 *     tool takes its best score across the whole message and the pieces: a request buried at the end of a story
 *     still fires. When the config has a [RouterConfig.pieceHead], that second layer scores the pieces: it was
 *     fitted with plain clauses of long stories as NONE, so a story's statements stop reading as things to save.
 *     Both tools fire only when the message holds two requests: joined by "and", "then" and the like, or with SAVE
 *     and ACTION each winning in a different piece.
 * Nothing firing is NONE: plain chat, and Gemma sees no tool schemas at all.
 *
 * Measured on 601 held-out sentences from writers who never saw the training sentences (eval/router/heldout.jsonl,
 * scripts/eval/router_heldout.py). docs/spikes/2026-11-10-tool-calling-eval.md has the numbers.
 */
enum class ToolIntent { SAVE, ACTION }

/** Turns a message into one L2-normalized sentence vector. The MiniLM ONNX model on Android. */
fun interface SentenceEncoder {
    fun encode(text: String): FloatArray
}

class RouterConfig(
    val model: String,
    val headWords: Int,
    val scale: Double,
    val thresholds: Map<ToolIntent, Double>,
    /** inputs x 3, columns SAVE, ACTION, NONE. The inputs are the hidden layer's when there is one. */
    val weights: Array<DoubleArray>,
    val bias: DoubleArray,
    /** dims x hidden, applied first with ReLU (the mlp scorer); null for the straight linear layer. */
    val hiddenWeights: Array<DoubleArray>? = null,
    val hiddenBias: DoubleArray? = null,
    /** Also score each sentence or clause of a longer message. */
    val split: Boolean = false,
    /** Scores the pieces of a split message in place of this layer; same head words, its own thresholds. */
    val pieceHead: RouterConfig? = null
)

private val LABELS = listOf("SAVE", "ACTION", "NONE")
private const val SAVE_COLUMN = 0
private const val ACTION_COLUMN = 1
private const val NONE_COLUMN = 2

/** Reads eval/router/embed-<model>-mlp.json (or -linear.json), the files embed_router.py fit writes. */
fun parseRouterConfig(text: String): RouterConfig {
    val root = Json.parseToJsonElement(text).jsonObject
    val scorer = root.string("scorer")
    require(scorer == "linear" || scorer == "mlp") { "only the linear and mlp scorers are ported" }
    val labels = root.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
    require(labels == LABELS) { "labels must be $LABELS, got $labels" }
    val thresholds = root.getValue("thresholds").jsonObject
    val weights = root.getValue("weights").matrix()
    require(weights.all { it.size == LABELS.size }) { "weights must have ${LABELS.size} columns" }
    val hidden = if (scorer == "mlp") root.getValue("hidden_weights").matrix() else null
    val hiddenBias = if (scorer == "mlp") root.getValue("hidden_bias").jsonArray.doubles() else null
    if (hidden != null) {
        require(hidden.all { it.size == weights.size } && hiddenBias?.size == weights.size) {
            "hidden layer must have ${weights.size} units"
        }
    }
    return RouterConfig(
        model = root.string("model"),
        headWords = root.getValue("head_words").jsonPrimitive.int,
        scale = root.getValue("scale").jsonPrimitive.double,
        thresholds = ToolIntent.entries.associateWith { thresholds.getValue(it.name).jsonPrimitive.double },
        weights = weights,
        bias = root.getValue("bias").jsonArray.doubles(),
        hiddenWeights = hidden,
        hiddenBias = hiddenBias,
        split = root["split"]?.jsonPrimitive?.boolean ?: false,
        pieceHead = root["piece_head"]?.let { piece ->
            parseRouterConfig(JsonObject(piece.jsonObject + ("model" to root.getValue("model")))).also {
                require(it.headWords == root.getValue("head_words").jsonPrimitive.int) {
                    "the piece head must use the same head words, so each piece is embedded once"
                }
            }
        }
    )
}

private fun parseRouterConfig(root: JsonObject): RouterConfig = parseRouterConfig(root.toString())

private fun JsonElement.matrix() = jsonArray.map { row -> (row as JsonArray).doubles() }.toTypedArray()

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonArray.doubles() = map { it.jsonPrimitive.double }.toDoubleArray()

/** One routing decision. [intents] best first; empty is NONE. [margins] are each intent's score minus NONE's. */
data class RouteDecision(
    val intents: List<ToolIntent>,
    val margins: Map<ToolIntent, Double>,
    val millis: Long = 0L
) {
    /** "NONE", "SAVE", "ACTION" or "SAVE+ACTION", as the Python eval writes it. */
    val label: String
        get() = intents.sortedBy { it.ordinal }.joinToString("+") { it.name }.ifEmpty { "NONE" }
}

class EmbeddingIntentRouter(
    private val config: RouterConfig,
    private val encoder: SentenceEncoder,
    private val clockMs: () -> Long = { 0L }
) {
    fun decide(transcript: String): RouteDecision {
        val start = clockMs()
        val whole = margins(embed(transcript))
        val pieces = if (config.split) segments(transcript).map { pieceMargins(embed(it)) } else emptyList()
        return combine(whole, pieces, transcript, config.thresholds).copy(millis = clockMs() - start)
    }

    /** The whole message averaged with its first head words, L2-normalized. */
    fun embed(transcript: String): DoubleArray {
        val text = normalizeForRouting(transcript)
        val full = encoder.encode(text)
        if (config.headWords == 0) return full.toDoubles()
        val head = headWords(text, config.headWords)
        val headVector = if (head == text) full else encoder.encode(head)
        return unit(DoubleArray(full.size) { full[it].toDouble() + headVector[it].toDouble() })
    }

    fun margins(vector: DoubleArray): Map<ToolIntent, Double> = scoreMargins(config, vector)

    /**
     * A piece's margins. With a piece head, they are moved onto this layer's scale (how far each clears the piece
     * head's own threshold, plus this layer's), so [combine] checks one set of thresholds for both.
     */
    fun pieceMargins(vector: DoubleArray): Map<ToolIntent, Double> {
        val head = config.pieceHead ?: return margins(vector)
        return scoreMargins(head, vector).mapValues { (intent, margin) ->
            margin - head.thresholds.getValue(intent) + config.thresholds.getValue(intent)
        }
    }
}

private fun scoreMargins(config: RouterConfig, vector: DoubleArray): Map<ToolIntent, Double> {
    val hidden = config.hiddenWeights
    val hiddenBias = config.hiddenBias
    val z = if (hidden == null || hiddenBias == null) {
        layer(vector, config.weights, config.bias, config.scale)
    } else {
        val h = layer(vector, hidden, hiddenBias, config.scale)
        for (j in h.indices) h[j] = maxOf(0.0, h[j])
        layer(h, config.weights, config.bias, 1.0)
    }
    return mapOf(
        ToolIntent.SAVE to z[SAVE_COLUMN] - z[NONE_COLUMN],
        ToolIntent.ACTION to z[ACTION_COLUMN] - z[NONE_COLUMN]
    )
}

// Said before the request itself: spoken lead-ins, the wake word and politeness. router.py's _FILLERS and _PREFIX,
// unchanged.
private val PREFIX = Regex(
    """^\W*(?:(?:uh|um|umm|eh|ugh|yo|hey|ok|okay|alright|all right|basically|buddy)\b\W*)*""" +
        """(?:(?:hey|ok|okay)\s+)?(?:computer\b\W*)?""" +
        """(?:(?:can|could|would|will) you\s+|please\s+|go ahead and\s+|i want you to\s+|i need you to\s+)*"""
)
private val TWO_REQUESTS = Regex("""\b(?:and|then|also|plus)\b""", RegexOption.IGNORE_CASE)
private val WHITESPACE = Regex("""\s+""")

/** Drops the wake word and "can you", as embed_router.normalize does. */
fun normalizeForRouting(transcript: String): String {
    val lower = transcript.trim().lowercase()
    return PREFIX.replaceFirst(lower, "").trim().ifEmpty { lower }
}

fun headWords(text: String, count: Int): String =
    text.split(WHITESPACE).filter { it.isNotEmpty() }.take(count).joinToString(" ")

// Sentence ends, a comma before a joining word, joining phrases that start a new request, and "and" before a request
// verb. embed_router.SEGMENT.
private val SEGMENT = Regex(
    """[.!?;]+\s+|,\s*(?=(?:and|then|also|plus|oh|so|but)\b)|\s+(?:and then|and also|oh and|then|also)\s+""" +
        """|\s+and\s+(?=(?:save|jot|write|note|make|take|keep|add|put|set|start|remind|navigate|get|show|""" +
        """text|email|send|tell|give|find|directions)\b)""",
    RegexOption.IGNORE_CASE
)

/** The message's sentences and clauses of two words or more, when there are at least two; otherwise none. */
fun segments(transcript: String): List<String> {
    val parts = SEGMENT.split(normalizeForRouting(transcript))
        .map { it.trim(' ', ',') }
        .filter { part -> part.split(WHITESPACE).count { it.isNotEmpty() } >= 2 }
    return if (parts.size > 1) parts else emptyList()
}

/**
 * The decision from the whole message's margins and its pieces': each tool's margin is its best across them. Two
 * requests when the message joins them with a word, or when SAVE and ACTION each win in a different piece.
 */
fun combine(
    whole: Map<ToolIntent, Double>,
    pieces: List<Map<ToolIntent, Double>>,
    transcript: String,
    thresholds: Map<ToolIntent, Double>
): RouteDecision {
    val best = whole.toMutableMap()
    val bestPiece = ToolIntent.entries.associateWith { 0 }.toMutableMap()  // 0 is the whole message
    pieces.forEachIndexed { index, margins ->
        for (intent in ToolIntent.entries) {
            if (margins.getValue(intent) > best.getValue(intent)) {
                best[intent] = margins.getValue(intent)
                bestPiece[intent] = index + 1
            }
        }
    }
    val two = TWO_REQUESTS.containsMatchIn(transcript) ||
        bestPiece.getValue(ToolIntent.SAVE) != bestPiece.getValue(ToolIntent.ACTION)
    return RouteDecision(fire(best, thresholds, two), best)
}

/**
 * Tool intents over their thresholds, best first. Both only when the message holds two requests ([two]): a single
 * request often scores over both ("Remind me in 20 minutes to check the oven" sits near the SAVE examples too).
 */
fun fire(margins: Map<ToolIntent, Double>, thresholds: Map<ToolIntent, Double>, two: Boolean): List<ToolIntent> {
    val fired = ToolIntent.entries
        .filter { margins.getValue(it) >= thresholds.getValue(it) }
        .sortedByDescending { margins.getValue(it) }
    return if (fired.size < 2 || two) fired else fired.take(1)
}

private fun FloatArray.toDoubles() = DoubleArray(size) { this[it].toDouble() }

/** scale * (input . weights) + bias, one output per weights column. */
private fun layer(input: DoubleArray, weights: Array<DoubleArray>, bias: DoubleArray, scale: Double): DoubleArray {
    require(input.size == weights.size) { "input has ${input.size} dims, weights ${weights.size}" }
    return DoubleArray(bias.size) { column ->
        var sum = 0.0
        for (i in input.indices) sum += input[i] * weights[i][column]
        scale * sum + bias[column]
    }
}

private fun unit(v: DoubleArray): DoubleArray {
    val norm = sqrt(v.sumOf { it * it })
    return if (norm == 0.0) v else DoubleArray(v.size) { v[it] / norm }
}
