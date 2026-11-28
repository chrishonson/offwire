package com.chrishonson.gemmavoice.router

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the Kotlin MobileBERT router to the Python one (scripts/eval/mobilebert_router.py) on every training and
 * held-out sentence: eval/router/parity-mobilebert-router.json, written by `mobilebert_router.py --parity`. The ONNX
 * model itself is not run here (no JVM ONNX Runtime in unit tests); this checks the token ids exactly, on the raw
 * text the classifier reads, and the route from Python's own logits.
 */
class MobileBertParityTest {
    private val root = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "THIRD_PARTY_NOTICES.md").exists() }
    private val assets = File(root, "core/src/androidMain/assets")
    private val parity = Json.parseToJsonElement(
        File(root, "eval/router/parity-mobilebert-router.json").readText()
    ).jsonObject
    private val cases = parity.getValue("cases").jsonArray.map { it.jsonObject }
    private val tokenizer = WordPieceTokenizer(parseVocab(File(assets, ROUTER_VOCAB_ASSET).readText()), ::nfd)

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun theAppShipsTheModelTheParityFileWasMadeWith() {
        assertEquals(File(MOBILEBERT_MODEL_ASSET).name, parity.str("model"))
        assertEquals(CLASSIFIER_LABELS, parity.getValue("labels").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(128, parity.getValue("max_tokens").jsonPrimitive.int)
        assertTrue(File(assets, MOBILEBERT_MODEL_ASSET).exists())
    }

    @Test
    fun tokenizesTheRawMessageLikePython() {
        assertTrue(cases.size > 1800)
        for (case in cases) {
            val want = case.getValue("ids").jsonArray.map { it.jsonPrimitive.int }
            assertEquals(want, tokenizer.encode(case.str("text")).toList(), case.str("text"))
        }
    }

    @Test
    fun routesPythonsLogitsTheSame() {
        for (case in cases) {
            val logits = case.getValue("logits").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
            val want = case.getValue("intents").jsonArray.map { ToolIntent.valueOf(it.jsonPrimitive.content) }
            assertEquals(want, classifierDecision(logits).intents, case.str("text"))
        }
    }
}
