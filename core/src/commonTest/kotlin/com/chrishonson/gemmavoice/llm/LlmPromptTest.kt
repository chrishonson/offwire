package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LlmPromptTest {

    @Test
    fun imagePromptEqualityAndFields() {
        val prompt1 = LlmPrompt.Image("/path/to/image1.jpg", "instruction A")
        val prompt2 = LlmPrompt.Image("/path/to/image1.jpg", "instruction A")
        val promptDifferentPath = LlmPrompt.Image("/path/to/image2.jpg", "instruction A")
        val promptDifferentInstruction = LlmPrompt.Image("/path/to/image1.jpg", "instruction B")

        assertEquals("/path/to/image1.jpg", prompt1.imagePath)
        assertEquals("instruction A", prompt1.instruction)

        assertEquals(prompt1, prompt2)
        assertEquals(prompt1.hashCode(), prompt2.hashCode())

        assertNotEquals(prompt1, promptDifferentPath)
        assertNotEquals(prompt1, promptDifferentInstruction)
    }

}
