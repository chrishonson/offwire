package com.chrishonson.gemmavoice.bench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WerCalculatorTest {

    @Test
    fun testNormalizeLowercaseAndPunctuation() {
        val input = "Computer, what time is it?"
        val expected = "computer what time is it"
        assertEquals(expected, WerCalculator.normalize(input))
    }

    @Test
    fun testNormalizePreservesApostrophesAndDigits() {
        val input = "Doctor Patel’s prescription: 2 pills a day, don't miss!"
        val expected = "doctor patel's prescription 2 pills a day don't miss"
        assertEquals(expected, WerCalculator.normalize(input))
    }

    @Test
    fun testNormalizeCollapsesWhitespace() {
        val input = "   Hello \t\n world!   Multiple   spaces.  "
        val expected = "hello world multiple spaces"
        assertEquals(expected, WerCalculator.normalize(input))
    }

    @Test
    fun testNormalizeEmpty() {
        assertEquals("", WerCalculator.normalize(""))
        assertEquals("", WerCalculator.normalize("   !?,.;:   "))
    }

    @Test
    fun testTokenize() {
        val tokens = WerCalculator.tokenize("Computer, what time is it?")
        assertEquals(listOf("computer", "what", "time", "is", "it"), tokens)
        assertTrue(WerCalculator.tokenize("").isEmpty())
    }

    @Test
    fun testWordLevenshteinIdentical() {
        val words = listOf("hello", "world")
        assertEquals(0, WerCalculator.wordLevenshteinDistance(words, words))
    }

    @Test
    fun testWordLevenshteinSubstitution() {
        val ref = listOf("the", "quick", "brown", "fox")
        val hyp = listOf("the", "slow", "brown", "fox")
        assertEquals(1, WerCalculator.wordLevenshteinDistance(ref, hyp))
    }

    @Test
    fun testWordLevenshteinInsertion() {
        val ref = listOf("the", "brown", "fox")
        val hyp = listOf("the", "quick", "brown", "fox")
        assertEquals(1, WerCalculator.wordLevenshteinDistance(ref, hyp))
    }

    @Test
    fun testWordLevenshteinDeletion() {
        val ref = listOf("the", "quick", "brown", "fox")
        val hyp = listOf("the", "brown", "fox")
        assertEquals(1, WerCalculator.wordLevenshteinDistance(ref, hyp))
    }

    @Test
    fun testWordLevenshteinEmpty() {
        val words = listOf("hello", "world")
        assertEquals(2, WerCalculator.wordLevenshteinDistance(emptyList(), words))
        assertEquals(2, WerCalculator.wordLevenshteinDistance(words, emptyList()))
        assertEquals(0, WerCalculator.wordLevenshteinDistance(emptyList(), emptyList()))
    }

    @Test
    fun testSentenceWerCaseAndPunctuationIgnored() {
        val ref = "Computer, what time is it?"
        val hyp = "COMPUTER what time is it"
        assertEquals(0.0, WerCalculator.sentenceWer(ref, hyp))
    }

    @Test
    fun testSentenceWerEdits() {
        val ref = "Computer, what time is it?" // 5 words
        val hyp = "Computer, what date is it now?" // time->date (sub), now (ins) -> 2 edits
        val wer = WerCalculator.sentenceWer(ref, hyp)
        assertEquals(2.0 / 5.0, wer, 0.0001)
    }

    @Test
    fun testOverallWer() {
        val pairs = listOf(
            "Computer, what time is it?" to "Computer what time is it", // 5 words, 0 edits
            "Turn off the lights" to "Turn on lights" // 4 words, off->on (sub), the deleted -> 2 edits
        )
        // total ref words = 5 + 4 = 9, total edits = 0 + 2 = 2
        val overall = WerCalculator.overallWer(pairs)
        assertEquals(2.0 / 9.0, overall, 0.0001)
    }

    @Test
    fun testStatsCalculations() {
        val values = listOf(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L)
        assertEquals(550.0, WerCalculator.calculateMean(values))
        assertEquals(1000L, WerCalculator.calculateMax(values))
        // 10 items, 90th percentile is index ceil(0.9 * 10) - 1 = 9 - 1 = 8 -> 900L
        assertEquals(900.0, WerCalculator.calculateP90(values))

        assertTrue(WerCalculator.calculateMean(emptyList()) == 0.0)
        assertEquals(0L, WerCalculator.calculateMax(emptyList()))
        assertTrue(WerCalculator.calculateP90(emptyList()) == 0.0)
    }
}
