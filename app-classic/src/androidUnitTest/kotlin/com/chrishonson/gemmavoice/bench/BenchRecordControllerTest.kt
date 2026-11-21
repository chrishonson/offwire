package com.chrishonson.gemmavoice.bench

import com.chrishonson.gemmavoice.chat.FakeAudioStream
import com.chrishonson.gemmavoice.voice.WavEncoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BenchRecordControllerTest {

    private lateinit var tempDir: File
    private lateinit var benchDir: File
    private lateinit var recDir: File
    private lateinit var fakeStream: FakeAudioStream

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("bench_test").toFile()
        benchDir = File(tempDir, "bench")
        recDir = File(benchDir, "rec")
        fakeStream = FakeAudioStream()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testInitialState() {
        val controller = BenchRecordController(fakeStream, benchDir, recDir)
        assertEquals(1, controller.currentIndex.value)
        assertFalse(controller.isRecording.value)
        assertEquals(0, controller.recordedCount.value)
        assertFalse(controller.allRecorded.value)
        assertFalse(controller.isCurrentSentenceRecorded())
    }

    @Test
    fun testNavigation() {
        val controller = BenchRecordController(fakeStream, benchDir, recDir)
        controller.next()
        assertEquals(2, controller.currentIndex.value)
        controller.back()
        assertEquals(1, controller.currentIndex.value)
        // Cannot go below 1
        controller.back()
        assertEquals(1, controller.currentIndex.value)
    }

    @Test
    fun testRecordAndStopFlow() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val controller = BenchRecordController(fakeStream, benchDir, recDir, ioDispatcher = dispatcher)
        controller.startRecording(this)
        assertTrue(controller.isRecording.value)

        // sentences.txt should be written on recording start
        val sentencesFile = File(benchDir, "sentences.txt")
        assertTrue(sentencesFile.exists())
        val lines = sentencesFile.readLines()
        assertEquals(15, lines.size)
        assertEquals("Computer, what time is it?", lines[0])

        // Push some PCM audio into the stream
        val pcm = fakeStream.silence(100)
        fakeStream.push(pcm)

        val savedFile = controller.stopRecording()
        assertFalse(controller.isRecording.value)
        assertEquals("01.wav", savedFile.name)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.length() > WavEncoder.HEADER_SIZE)

        // Screen should advance after stop
        assertEquals(2, controller.currentIndex.value)
        assertEquals(1, controller.recordedCount.value)
        assertFalse(controller.allRecorded.value)
    }

    @Test
    fun testRedoDeletesRecording() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val controller = BenchRecordController(fakeStream, benchDir, recDir, ioDispatcher = dispatcher)
        controller.startRecording(this)
        fakeStream.push(fakeStream.silence(50))
        controller.stopRecording()
        // Index is now 2, go back to 1
        controller.back()
        assertEquals(1, controller.currentIndex.value)
        assertTrue(controller.isCurrentSentenceRecorded())

        controller.redo()
        assertFalse(controller.isCurrentSentenceRecorded())
        assertEquals(0, controller.recordedCount.value)
    }

    @Test
    fun testAllRecordedBecomesTrue() {
        val controller = BenchRecordController(fakeStream, benchDir, recDir)
        // Pre-create all 15 files
        recDir.mkdirs()
        val dummyWav = WavEncoder.encodePcm16Mono(ByteArray(100), 16000)
        for (i in 1..15) {
            File(recDir, formatRecordingFileName(i)).writeBytes(dummyWav)
        }
        controller.refreshRecordedState()
        assertEquals(15, controller.recordedCount.value)
        assertTrue(controller.allRecorded.value)
    }
}
