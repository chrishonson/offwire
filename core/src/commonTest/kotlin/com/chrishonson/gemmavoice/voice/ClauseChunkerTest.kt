package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClauseChunkerTest {
    @Test
    fun emitsFirstChunkEarlyAt12CharsOnComma() {
        val chunker = ClauseChunker()

        // Comma before 12 chars ("Well, " is 5 chars) does not emit
        assertTrue(chunker.feed("Well, ").isEmpty())

        // Reaching a comma at >= 12 chars ("Well, hello there," is 18 chars) emits first chunk
        assertEquals(
            listOf("Well, hello there,"),
            chunker.feed("Well, hello there, how are you")
        )
        assertEquals("how are you", chunker.flush())
        assertNull(chunker.flush())
    }

    @Test
    fun hardCutsFirstChunkAt100CharsOnWordBreakWhenNoClauseBoundary() {
        val chunker = ClauseChunker()
        val longText = "This opening sentence deliberately avoids all punctuation marks " +
            "so that the chunker must cut cleanly at a word boundary"
        assertTrue(longText.length > 100)

        val emitted = chunker.feed(longText)
        assertEquals(1, emitted.size)
        val firstChunk = emitted.single()
        assertTrue(firstChunk.length in 12..100, "Expected cut between 12 and 100 chars, got ${firstChunk.length}")
        assertEquals(
            "This opening sentence deliberately avoids all punctuation marks so that the chunker must cut cleanly",
            firstChunk
        )
        assertEquals("at a word boundary", chunker.flush())
    }

    @Test
    fun splitsLaterChunksAtCommasOnlyAfter40CharsAndAlwaysAtSentences() {
        val chunker = ClauseChunker()

        // Emit first chunk at >= 12 chars on comma
        assertEquals(
            listOf("Hello there,"),
            chunker.feed("Hello there, ")
        )

        // Later chunk with comma at < 40 chars ("short clause," is 13 chars) stays buffered
        assertTrue(chunker.feed("Hello there, short clause, ").isEmpty())

        // Later chunk reaching a comma at >= 40 chars splits on that comma
        val afterLongComma =
            "Hello there, short clause, followed by a much longer continuation clause, trailing words"
        assertEquals(
            listOf("short clause, followed by a much longer continuation clause,"),
            chunker.feed(afterLongComma)
        )

        // Sentence boundary in later chunk splits even when < 40 chars
        assertEquals(
            listOf("trailing words."),
            chunker.feed("$afterLongComma. Final tail")
        )
        assertEquals("Final tail", chunker.flush())
    }

    @Test
    fun splitsMultipleSentencesAndNewlinesAndPreservesAbbreviations() {
        val chunker = ClauseChunker()

        val clauses = chunker.feed("First line here\nSecond sentence! Third question? Trailing")
        assertEquals(
            listOf("First line here", "Second sentence!", "Third question?"),
            clauses
        )
        assertEquals("Trailing", chunker.flush())

        val abbrevChunker = ClauseChunker()
        assertTrue(abbrevChunker.feed("Talk to Dr.").isEmpty())
        assertEquals(
            listOf("Talk to Dr. Smith today."),
            abbrevChunker.feed("Talk to Dr. Smith today.")
        )

        val shortChunker = ClauseChunker()
        assertTrue(shortChunker.feed("Yes.").isEmpty())
        assertEquals("Yes.", shortChunker.flush())
    }

    @Test
    fun stripsMarkdownSymbolsAndEmojisBeforeReturning() {
        val chunker = ClauseChunker()

        val clauses = chunker.feed(
            "**Hello** *world*! 😀✨\n### `Quick` _summary_ ~now~ 🎉\n> Trailing **note** 🚀"
        )
        assertEquals(
            listOf("Hello world!", "Quick summary now"),
            clauses
        )
        assertEquals("Trailing note", chunker.flush())
        assertNull(chunker.flush())
    }
}
