package com.chrishonson.gemmavoice.router

import kotlin.test.Test
import kotlin.test.assertEquals

class ClassifierRouterTest {
    @Test
    fun theTopClassIsTheRoute() {
        assertEquals(listOf(ToolIntent.SAVE), classifierDecision(floatArrayOf(5f, -1f, 2f, 1f)).intents)
        assertEquals(listOf(ToolIntent.ACTION), classifierDecision(floatArrayOf(-1f, 4f, 2f, 1f)).intents)
        assertEquals(emptyList(), classifierDecision(floatArrayOf(1f, 1f, 9f, 1f)).intents)
        assertEquals("NONE", classifierDecision(floatArrayOf(1f, 1f, 9f, 1f)).label)
    }

    @Test
    fun saveAndActionPutsTheHigherScoringToolFirst() {
        val actionFirst = classifierDecision(floatArrayOf(-4f, 4.7f, -3.8f, 6.3f))
        assertEquals(listOf(ToolIntent.ACTION, ToolIntent.SAVE), actionFirst.intents)
        val saveFirst = classifierDecision(floatArrayOf(3f, 1f, 0f, 6f))
        assertEquals(listOf(ToolIntent.SAVE, ToolIntent.ACTION), saveFirst.intents)
        assertEquals("SAVE+ACTION", classifierDecision(floatArrayOf(3f, 1f, 0f, 6f)).label)
    }

    @Test
    fun marginsAreEachToolsBestClassMinusNone() {
        val margins = classifierDecision(floatArrayOf(8f, -7f, -4f, 1f)).margins
        assertEquals(12.0, margins.getValue(ToolIntent.SAVE), 1e-6)
        assertEquals(5.0, margins.getValue(ToolIntent.ACTION), 1e-6)
    }

    @Test
    fun routesTheTokenizedMessage() {
        val words = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "save", "it")
        val vocab = words.withIndex().associate { it.value to it.index }
        val seen = mutableListOf<List<Int>>()
        val router = ClassifierIntentRouter(WordPieceTokenizer(vocab, { it }), { ids ->
            seen += ids.toList()
            floatArrayOf(3f, 0f, 0f, 0f)
        })
        assertEquals(listOf(ToolIntent.SAVE), router.decide("Save it").intents)
        assertEquals(listOf(listOf(2, 4, 5, 3)), seen)
    }
}
