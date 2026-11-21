package com.chrishonson.gemmavoice.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SilenceTrimmerTest {
    private val rate = 1000 // pad is 50 samples

    private fun quiet(n: Int) = FloatArray(n)
    private fun loud(n: Int) = FloatArray(n) { 0.5f }

    @Test
    fun leadingSilenceIsDroppedExceptForThePad() {
        val trimmer = SilenceTrimmer(rate)
        assertTrue(trimmer.feed(quiet(300)).isEmpty())
        val out = trimmer.feed(quiet(200) + loud(100))
        assertEquals(1, out.size)
        assertEquals(150, out[0].size)
    }

    @Test
    fun innerSilenceIsReleasedWhenSpeechResumes() {
        val trimmer = SilenceTrimmer(rate)
        trimmer.feed(loud(100))
        assertTrue(trimmer.feed(quiet(80)).isEmpty())
        val out = trimmer.feed(loud(100))
        assertEquals(listOf(80, 100), out.map { it.size })
    }

    @Test
    fun trailingSilenceIsCutToThePad() {
        val trimmer = SilenceTrimmer(rate)
        trimmer.feed(loud(100))
        trimmer.feed(quiet(300))
        trimmer.feed(quiet(300))
        assertEquals(50, trimmer.flush()?.size)
        assertNull(trimmer.flush())
    }

    @Test
    fun speakableTextDropsMarkdownMarks() {
        assertEquals("Shields are up.", speakableText(" **Shields** are `up`. #"))
    }
}
