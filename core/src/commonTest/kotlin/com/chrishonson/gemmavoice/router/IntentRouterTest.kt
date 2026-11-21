package com.chrishonson.gemmavoice.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntentRouterTest {
    // Two dims. Column order SAVE, ACTION, NONE: dim 0 votes SAVE, dim 1 votes ACTION, NONE has a fixed bias.
    private val config = RouterConfig(
        model = "fake",
        headWords = 2,
        scale = 10.0,
        thresholds = mapOf(ToolIntent.SAVE to 0.5, ToolIntent.ACTION to 1.0),
        weights = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0)),
        bias = doubleArrayOf(0.0, 0.0, 5.0)
    )

    private val seen = mutableListOf<String>()
    private val encoder = SentenceEncoder { text ->
        seen += text
        when {
            text.startsWith("save") -> floatArrayOf(1f, 0f)
            text.startsWith("remind") -> floatArrayOf(0f, 1f)
            else -> floatArrayOf(0f, 0f)
        }
    }

    @Test
    fun normalizeDropsTheWakeWordAndPoliteness() {
        val spoken = "  Hey Computer, can you please remember the gate code "
        assertEquals("remember the gate code", normalizeForRouting(spoken))
        assertEquals("what time is it?", normalizeForRouting("OK computer what time is it?"))
        assertEquals("computer", normalizeForRouting("Computer"))
    }

    @Test
    fun headWordsTakesTheFirstWordsAcrossAnyWhitespace() {
        assertEquals("a b c", headWords("a  b\tc d", 3))
        assertEquals("", headWords("", 3))
    }

    @Test
    fun embedAveragesTheWholeMessageWithItsHeadAndNormalizes() {
        val router = EmbeddingIntentRouter(config, encoder)
        val vector = router.embed("Computer, save the date for later")
        assertEquals(listOf("save the date for later", "save the"), seen)
        assertEquals(1.0, vector[0] * vector[0] + vector[1] * vector[1], 1e-9)
    }

    @Test
    fun aShortMessageIsEncodedOnce() {
        EmbeddingIntentRouter(config, encoder).embed("save it")
        assertEquals(listOf("save it"), seen)
    }

    @Test
    fun marginsAreEachScoreMinusNone() {
        val margins = EmbeddingIntentRouter(config, encoder).margins(doubleArrayOf(1.0, 0.0))
        assertEquals(5.0, margins.getValue(ToolIntent.SAVE), 1e-9)
        assertEquals(-5.0, margins.getValue(ToolIntent.ACTION), 1e-9)
    }

    @Test
    fun decideRoutesSaveActionAndChat() {
        val router = EmbeddingIntentRouter(config, encoder)
        assertEquals("SAVE", router.decide("save the gate code").label)
        assertEquals("ACTION", router.decide("remind me in ten minutes").label)
        assertEquals("NONE", router.decide("how far is the moon").label)
    }

    @Test
    fun decideTimesItself() {
        var now = 100L
        val router = EmbeddingIntentRouter(config, { now += 40; floatArrayOf(0.3f, 0.3f) }, clockMs = { now })
        assertEquals(80L, router.decide("one two three").millis)
    }

    @Test
    fun bothFireOnlyWhenTheMessageHoldsTwoRequests() {
        val margins = mapOf(ToolIntent.SAVE to 2.0, ToolIntent.ACTION to 3.0)
        assertEquals(listOf(ToolIntent.ACTION), fire(margins, config.thresholds, two = false))
        assertEquals(listOf(ToolIntent.ACTION, ToolIntent.SAVE), fire(margins, config.thresholds, two = true))
        assertEquals(emptyList(), fire(margins, mapOf(ToolIntent.SAVE to 9.0, ToolIntent.ACTION to 9.0), true))
    }

    @Test
    fun segmentsSplitSentencesAndJoinedRequestsButNotPlainAnd() {
        assertEquals(
            listOf("we landed and it's raining", "get me to the hotel."),
            segments("Hey computer, we landed and it's raining. Get me to the hotel.")
        )
        assertEquals(listOf("set a timer", "then save a note"), segments("set a timer, then save a note"))
        assertEquals(listOf("note the code", "remind me in ten"), segments("note the code and then remind me in ten"))
        assertEquals(
            listOf("set a timer", "write down that the tea is green"),
            segments("set a timer and write down that the tea is green")
        )
        assertEquals(emptyList(), segments("add eggs and bread to the list"))
        assertEquals(emptyList(), segments("Hi. Save the code"))  // "hi" is too short to be a piece
    }

    @Test
    fun aRequestAtTheEndOfAStoryStillFires() {
        val router = EmbeddingIntentRouter(
            RouterConfig("fake", 0, 10.0, config.thresholds, config.weights, config.bias, split = true), encoder
        )
        val decision = router.decide("long day at work today. remind me in ten minutes")
        assertEquals("ACTION", decision.label)
        assertEquals(listOf("long day at work today. remind me in ten minutes", "long day at work today",
            "remind me in ten minutes"), seen)
        val off = EmbeddingIntentRouter(config, encoder).decide("long day at work today. remind me in ten minutes")
        assertEquals("NONE", off.label)
    }

    @Test
    fun twoPiecesWinningDifferentToolsAreTwoRequests() {
        val whole = mapOf(ToolIntent.SAVE to 0.0, ToolIntent.ACTION to 0.0)
        val save = mapOf(ToolIntent.SAVE to 2.0, ToolIntent.ACTION to -2.0)
        val act = mapOf(ToolIntent.SAVE to -2.0, ToolIntent.ACTION to 3.0)
        assertEquals("SAVE+ACTION", combine(whole, listOf(save, act), "note this. timer ten", config.thresholds).label)
        assertEquals(3.0, combine(whole, listOf(save, act), "x", config.thresholds).margins.getValue(ToolIntent.ACTION))
        val both = mapOf(ToolIntent.SAVE to 2.0, ToolIntent.ACTION to 3.0)
        assertEquals("ACTION", combine(whole, listOf(both), "x", config.thresholds).label)
    }

    @Test
    fun labelListsIntentsInSaveActionOrder() {
        assertEquals("SAVE+ACTION", RouteDecision(listOf(ToolIntent.ACTION, ToolIntent.SAVE), emptyMap()).label)
        assertEquals("NONE", RouteDecision(emptyList(), emptyMap()).label)
    }

    @Test
    fun parsesTheFittedConfig() {
        val parsed = parseRouterConfig(
            """{"model":"m","scorer":"linear","thresholds":{"SAVE":0.3,"ACTION":0.95},"head_words":4,"l2":0.001,
            "scale":10.0,"labels":["SAVE","ACTION","NONE"],"weights":[[1,2,3],[4,5,6]],"bias":[0.1,0.2,0.3]}"""
        )
        assertEquals(4, parsed.headWords)
        assertEquals(0.95, parsed.thresholds.getValue(ToolIntent.ACTION))
        assertEquals(6.0, parsed.weights[1][2])
        assertEquals(0.3, parsed.bias[2])
        assertEquals(false, parsed.split)
        val split = parseRouterConfig(
            """{"model":"m","scorer":"linear","thresholds":{"SAVE":0,"ACTION":0},"head_words":3,"scale":10,
            "labels":["SAVE","ACTION","NONE"],"weights":[[1,2,3]],"bias":[0,0,0],"split":true}"""
        )
        assertEquals(true, split.split)
    }

    @Test
    fun theMlpScorerRunsItsHiddenLayerWithReluFirst() {
        // Hidden unit 0 copies dim 0, unit 1 is dim 0 negated (ReLU zeroes it for a positive input).
        val parsed = parseRouterConfig(
            """{"model":"m","scorer":"mlp","thresholds":{"SAVE":0,"ACTION":0},"head_words":0,"scale":10,
            "labels":["SAVE","ACTION","NONE"],"hidden_weights":[[1,-1],[0,0]],"hidden_bias":[0,0],
            "weights":[[1,0,0],[0,1,0]],"bias":[0,0,2],"split":false}"""
        )
        val margins = EmbeddingIntentRouter(parsed, encoder).margins(doubleArrayOf(0.5, 0.0))
        assertEquals(3.0, margins.getValue(ToolIntent.SAVE), 1e-9)  // h = (5, 0): SAVE 5 - NONE 2
        assertEquals(-2.0, margins.getValue(ToolIntent.ACTION), 1e-9)
        val negative = EmbeddingIntentRouter(parsed, encoder).margins(doubleArrayOf(-0.5, 0.0))
        assertEquals(-2.0, negative.getValue(ToolIntent.SAVE), 1e-9)  // h = (0, 5): SAVE 0 - NONE 2
        assertEquals(3.0, negative.getValue(ToolIntent.ACTION), 1e-9)
    }

    @Test
    fun rejectsAnotherScorerOrLabelOrder() {
        assertFailsWith<IllegalArgumentException> {
            parseRouterConfig("""{"model":"m","scorer":"knn","labels":["SAVE","ACTION","NONE"]}""")
        }
        val error = assertFailsWith<IllegalArgumentException> {
            parseRouterConfig(
                """{"model":"m","scorer":"linear","thresholds":{"SAVE":0,"ACTION":0},"head_words":4,"scale":10,
                "labels":["NONE","SAVE","ACTION"],"weights":[],"bias":[]}"""
            )
        }
        assertTrue(error.message.orEmpty().contains("labels"))
    }
}
