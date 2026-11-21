package com.chrishonson.gemmavoice.router

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WordPieceTokenizerTest {
    private val vocab = parseVocab(
        listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "remember", "the", "gate", "code", "un", "##aff", "##able", ",",
            "!", "x", "##x", "東").joinToString("\n")
    )
    private val tokenizer = WordPieceTokenizer(vocab, decompose = { it }, maxTokens = 8)

    @Test
    fun vocabIdsAreLineNumbers() {
        assertEquals(4, vocab["remember"])
        assertEquals(15, vocab["東"])
    }

    @Test
    fun wrapsInClsAndSepAndLowercases() {
        assertContentEquals(intArrayOf(2, 4, 5, 6, 7, 3), tokenizer.encode("Remember THE gate code"))
    }

    @Test
    fun punctuationIsItsOwnWordAndPiecesContinue() {
        assertContentEquals(intArrayOf(2, 8, 9, 10, 11, 12, 3), tokenizer.encode("unaffable,!"))
    }

    @Test
    fun aWordWithNoPieceIsOneUnknown() {
        assertContentEquals(intArrayOf(2, 1, 4, 3), tokenizer.encode("zebra remember"))
    }

    @Test
    fun chineseCharactersAreSpacedOut() {
        assertContentEquals(intArrayOf(2, 4, 15, 4, 3), tokenizer.encode("remember東remember"))
    }

    @Test
    fun controlCharactersAreDroppedAndWhitespaceSplits() {
        assertEquals(listOf("the", "gate"), tokenizer.preTokenize(tokenizer.normalize("the\u0000\tgate​")))
    }

    @Test
    fun cutsToMaxTokensKeepingSep() {
        val ids = tokenizer.encode("x x x x x x x x x x")
        assertEquals(8, ids.size)
        assertEquals(2, ids.first())
        assertEquals(3, ids.last())
    }

    @Test
    fun aWordOverAHundredCharactersIsUnknown() {
        assertContentEquals(intArrayOf(2, 1, 3), tokenizer.encode("x".repeat(101)))
        assertEquals(100, tokenizer.wordPiece("x".repeat(100)).size)
    }
}
