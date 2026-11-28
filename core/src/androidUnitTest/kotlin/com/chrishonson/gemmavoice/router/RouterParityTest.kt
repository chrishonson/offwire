package com.chrishonson.gemmavoice.router

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the Kotlin router to the Python one (scripts/eval/embed_router.py), case by case, on every message in both
 * eval fixtures, the training sentences, the held-out set and the long messages:
 * eval/router/parity-all-MiniLM-L6-v2-mlp.json, written by scripts/eval/export_router_parity.py. The ONNX model
 * itself is not run here (no JVM ONNX Runtime in unit tests); this checks the tokenizer and the split into pieces
 * exactly, and the scoring from Python's own embeddings.
 */
class RouterParityTest {
    private val root = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "THIRD_PARTY_NOTICES.md").exists() }
    private val assets = File(root, "core/src/androidMain/assets")
    private val parity = Json.parseToJsonElement(
        File(root, "eval/router/parity-all-MiniLM-L6-v2-mlp.json").readText()
    ).jsonObject
    private val cases = parity.getValue("cases").jsonArray.map { it.jsonObject }
    private val vocab = parseVocab(File(assets, ROUTER_VOCAB_ASSET).readText())
    private val tokenizer = WordPieceTokenizer(vocab, ::nfd)
    private val config = parseRouterConfig(File(assets, ROUTER_CONFIG_ASSET).readText())

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.ints(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.int }
    private fun JsonArray.doubles() = map { it.jsonPrimitive.double }.toDoubleArray()

    @Test
    fun theAppShipsTheFittedConfigAndModel() {
        assertEquals(
            File(root, "eval/router/embed-all-MiniLM-L6-v2-mlp.json").readText(),
            File(assets, ROUTER_CONFIG_ASSET).readText()
        )
        assertEquals("all-MiniLM-L6-v2", config.model)
        assertEquals(parity.getValue("head_words").jsonPrimitive.int, config.headWords)
        assertEquals(parity.getValue("split").jsonPrimitive.content.toBoolean(), config.split)
        assertEquals(config.headWords, config.pieceHead?.headWords)  // the piece head scores the long messages' pieces
        assertEquals(30522, vocab.size)
        assertTrue(File(assets, ROUTER_MODEL_ASSET).exists())
    }

    @Test
    fun normalizesAndTakesTheSameHeadAndPiecesAsPython() {
        assertTrue(cases.size > 1500)
        for (case in cases) {
            val normalized = normalizeForRouting(case.str("text"))
            assertEquals(case.str("normalized"), normalized, case.str("id"))
            assertEquals(case.str("head"), headWords(normalized, config.headWords), case.str("id"))
            val pieces = case.getValue("segments").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(pieces, segments(case.str("text")), case.str("id"))
        }
    }

    @Test
    fun tokenizesEveryMessageLikePython() {
        for (case in cases) {
            assertEquals(case.ints("ids"), tokenizer.encode(case.str("normalized")).toList(), case.str("id"))
            assertEquals(case.ints("head_ids"), tokenizer.encode(case.str("head")).toList(), case.str("id"))
        }
    }

    @Test
    fun tokenizesTheEdgeCasesLikePython() {
        for (edge in parity.getValue("tokenizer_edges").jsonArray.map { it.jsonObject }) {
            assertEquals(edge.ints("ids"), tokenizer.encode(edge.str("text")).toList(), edge.str("text"))
        }
    }

    @Test
    fun scoresAndRoutesPythonsEmbeddingsTheSame() {
        val router = EmbeddingIntentRouter(config, { error("not used") })
        val withVectors = cases.filter { it.containsKey("vector") }
        assertEquals(72 + 40 + 10, withVectors.size)  // both fixtures, 40 split held-out rows, 10 long messages
        var splitCases = 0
        for (case in withVectors) {
            val whole = router.margins(case.getValue("vector").jsonArray.doubles())
            val pieces = case.getValue("segment_vectors").jsonArray.map { router.pieceMargins(it.jsonArray.doubles()) }
            if (pieces.isNotEmpty()) splitCases++
            val decision = combine(whole, pieces, case.str("text"), config.thresholds)
            val expected = case.getValue("margins").jsonObject
            for (intent in ToolIntent.entries) {
                val want = expected.getValue(intent.name).jsonPrimitive.double
                assertEquals(want, decision.margins.getValue(intent), 1e-4, case.str("id"))
            }
            assertEquals(case.str("intent"), decision.label, case.str("id"))
        }
        assertTrue(splitCases >= 40)
    }
}
