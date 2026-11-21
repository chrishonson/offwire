package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StreamAccumulatorTest {
    @Test
    fun appendsDeltaChunks() {
        val accumulator = StreamAccumulator()
        assertEquals("Hello", accumulator.append("Hello"))
        assertEquals("Hello, ", accumulator.append(", "))
        assertEquals("Hello, world!", accumulator.append("world!"))
    }

    @Test
    fun replacesCumulativeChunks() {
        val accumulator = StreamAccumulator()
        assertEquals("Hello", accumulator.append("Hello"))
        assertEquals("Hello, ", accumulator.append("Hello, "))
        assertEquals("Hello, world!", accumulator.append("Hello, world!"))
    }

    @Test
    fun handlesEmptyChunksInBetween() {
        val accumulator = StreamAccumulator()
        assertEquals("Hi", accumulator.append("Hi"))
        assertEquals("Hi", accumulator.append(""))
        assertEquals("Hi there", accumulator.append(" there"))
    }

    @Test
    fun audioPromptEqualsAndHashCode() {
        val first = LlmPrompt.Audio(byteArrayOf(1, 2, 3), "transcribe")
        val second = LlmPrompt.Audio(byteArrayOf(1, 2, 3), "transcribe")
        val differentBytes = LlmPrompt.Audio(byteArrayOf(1, 2, 4), "transcribe")
        val differentInstruction = LlmPrompt.Audio(byteArrayOf(1, 2, 3), "other")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, differentBytes)
        assertNotEquals(first, differentInstruction)
    }
}
