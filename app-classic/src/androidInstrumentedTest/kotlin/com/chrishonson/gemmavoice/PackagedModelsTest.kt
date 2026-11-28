package com.chrishonson.gemmavoice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrishonson.gemmavoice.router.AndroidIntentRouter
import com.chrishonson.gemmavoice.router.AndroidMobileBertRouter
import com.chrishonson.gemmavoice.router.ToolIntent
import com.chrishonson.gemmavoice.voice.ParakeetSpeechToText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

private const val SAMPLE_RATE = 16_000
private const val BYTES_PER_SAMPLE = 2
private const val LONGEST_SILENCE_TRANSCRIPT = 100

/**
 * The installed app finds the models it ships with. In an APK they are in the app's own assets. In an app bundle
 * they are in the install-time pack :model-pack, so run this against a bundle install to check that route too
 * (docs/RELEASING.md, "Google Play bundle").
 */
@RunWith(AndroidJUnit4::class)
class PackagedModelsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun routerLoadsItsModelAndRoutesASaveRequest() {
        AndroidIntentRouter(context).use { router ->
            assertEquals(listOf(ToolIntent.SAVE), router.decide("save a note that the garage door is open").intents)
        }
    }

    @Test
    fun mobileBertRouterLoadsItsModelAndRoutesASaveRequest() {
        AndroidMobileBertRouter(context).use { router ->
            assertEquals(listOf(ToolIntent.SAVE), router.decide("save a note that the garage door is open").intents)
        }
    }

    @Test
    fun speechToTextLoadsItsModelAndTranscribesSilence() {
        val silence = ByteArray(SAMPLE_RATE * BYTES_PER_SAMPLE)
        val text = runBlocking { ParakeetSpeechToText(context).transcribe(silence, SAMPLE_RATE) }
        assertTrue(text.length < LONGEST_SILENCE_TRANSCRIPT, "one second of silence came back as: $text")
    }
}
