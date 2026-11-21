package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplySpeedTest {
    private var nowMs = 0L
    private val meter = ReplySpeedMeter { nowMs }

    @Test
    fun firstTextReportsTimeToFirstToken() {
        nowMs = 1_000L
        meter.start()
        nowMs = 1_260L

        assertEquals(ReplySpeed(ttftMs = 260L), meter.onText("Hi"))
    }

    @Test
    fun laterTextReportsNothingNew() {
        meter.start()
        nowMs = 100L
        meter.onText("Hi")
        nowMs = 160L

        assertNull(meter.onText("Hi there"))
    }

    @Test
    fun blankOrRepeatedTextIsNotCounted() {
        meter.start()
        nowMs = 50L
        assertNull(meter.onText(""))
        assertNull(meter.onText("   "))
        nowMs = 300L
        meter.onText("One")
        nowMs = 400L
        meter.onText("One")
        nowMs = 500L
        meter.onText("One two")

        // Two counted messages, 200 ms apart: one interval, five per second.
        assertEquals(ReplySpeed(ttftMs = 300L, tokensPerSecond = 5.0), meter.finish())
    }

    @Test
    fun finishCountsMessagesBetweenFirstAndLast() {
        meter.start()
        val texts = listOf("A", "A b", "A b c", "A b c d", "A b c d e")
        texts.forEachIndexed { i, text ->
            nowMs = 250L + i * 50L
            meter.onText(text)
        }

        // Four intervals over 200 ms is 20 tokens per second.
        assertEquals(ReplySpeed(ttftMs = 250L, tokensPerSecond = 20.0), meter.finish())
    }

    @Test
    fun singleMessageHasNoRate() {
        meter.start()
        nowMs = 90L
        meter.onText("Done.")

        assertEquals(ReplySpeed(ttftMs = 90L, tokensPerSecond = null), meter.finish())
    }

    @Test
    fun noTextMeansNoSpeed() {
        meter.start()

        assertNull(meter.finish())
    }

    @Test
    fun startResetsThePreviousReply() {
        meter.start()
        nowMs = 100L
        meter.onText("First reply")
        nowMs = 1_000L
        meter.start()
        nowMs = 1_400L

        assertEquals(ReplySpeed(ttftMs = 400L), meter.onText("Second"))
    }

    @Test
    fun formatShowsSecondsAndRate() {
        assertEquals(
            "0.26 s to first token · 16.4 tok/s",
            formatReplySpeed(ReplySpeed(ttftMs = 260L, tokensPerSecond = 16.43))
        )
    }

    @Test
    fun formatWithoutRateShowsOnlyTimeToFirstToken() {
        assertEquals("2.2 s to first token", formatReplySpeed(ReplySpeed(ttftMs = 2_204L)))
    }
}
