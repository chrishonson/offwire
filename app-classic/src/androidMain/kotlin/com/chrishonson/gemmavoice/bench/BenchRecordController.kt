package com.chrishonson.gemmavoice.bench

import com.chrishonson.gemmavoice.voice.AudioStream
import com.chrishonson.gemmavoice.voice.WavEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val SAMPLE_RATE_HZ = 16000

class BenchRecordController(
    private val audioStream: AudioStream,
    private val benchDir: File,
    private val recDir: File,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {
    private var recordingJob: Job? = null
    private val pcmBuffer = mutableListOf<ByteArray>()

    private val _currentIndex = MutableStateFlow(1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedCount = MutableStateFlow(0)
    val recordedCount: StateFlow<Int> = _recordedCount.asStateFlow()

    private val _allRecorded = MutableStateFlow(false)
    val allRecorded: StateFlow<Boolean> = _allRecorded.asStateFlow()

    init {
        refreshRecordedState()
    }

    fun refreshRecordedState() {
        var count = 0
        for (i in 1..REFERENCE_SENTENCES.size) {
            val file = File(recDir, formatRecordingFileName(i))
            if (file.exists() && file.length() > WavEncoder.HEADER_SIZE) {
                count++
            }
        }
        _recordedCount.value = count
        _allRecorded.value = count == REFERENCE_SENTENCES.size
    }

    fun writeSentencesFile() {
        benchDir.mkdirs()
        val sentencesFile = File(benchDir, "sentences.txt")
        sentencesFile.writeText(REFERENCE_SENTENCES.joinToString("\n") + "\n")
    }

    fun startRecording(scope: CoroutineScope) {
        writeSentencesFile()
        pcmBuffer.clear()
        _isRecording.value = true
        recordingJob = scope.launch(ioDispatcher) {
            audioStream.frames().collect { frame ->
                pcmBuffer.add(frame)
            }
        }
    }

    suspend fun stopRecording(): File = withContext(ioDispatcher) {
        recordingJob?.cancelAndJoin()
        recordingJob = null
        _isRecording.value = false

        val totalBytes = pcmBuffer.sumOf { it.size }
        val pcm = ByteArray(totalBytes)
        var offset = 0
        for (chunk in pcmBuffer) {
            chunk.copyInto(pcm, offset)
            offset += chunk.size
        }
        pcmBuffer.clear()

        val wavBytes = WavEncoder.encodePcm16Mono(pcm, SAMPLE_RATE_HZ)
        recDir.mkdirs()
        val sentenceIdx = _currentIndex.value
        val file = File(recDir, formatRecordingFileName(sentenceIdx))
        file.writeBytes(wavBytes)

        refreshRecordedState()

        if (sentenceIdx < REFERENCE_SENTENCES.size) {
            _currentIndex.value = sentenceIdx + 1
        }
        file
    }

    fun redo() {
        val file = File(recDir, formatRecordingFileName(_currentIndex.value))
        if (file.exists()) {
            file.delete()
        }
        refreshRecordedState()
    }

    fun next() {
        if (_currentIndex.value < REFERENCE_SENTENCES.size) {
            _currentIndex.value += 1
        }
    }

    fun back() {
        if (_currentIndex.value > 1) {
            _currentIndex.value -= 1
        }
    }

    fun isCurrentSentenceRecorded(): Boolean {
        val file = File(recDir, formatRecordingFileName(_currentIndex.value))
        return file.exists() && file.length() > WavEncoder.HEADER_SIZE
    }
}
