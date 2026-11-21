package com.chrishonson.gemmavoice.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

class AndroidAudioStream(
    private val config: VoiceConfig = VoiceConfig(),
    private val callAudio: CommunicationAudio? = null,
    // Runs once capture has started, when call audio is routed to its live device.
    private val onStarted: () -> Unit = {}
) : AudioStream {

    @SuppressLint("MissingPermission")
    override fun frames(): Flow<ByteArray> = flow {
        val frameBytes = config.frameBytes
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameBytes)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            config.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer
        )
        val echoCanceler = createEchoCanceler(record.audioSessionId)
        val noiseSuppressor = createNoiseSuppressor(record.audioSessionId)

        try {
            callAudio?.enter()
            record.startRecording()
            onStarted()
            val buffer = ByteArray(frameBytes)
            var offset = 0
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, offset, frameBytes - offset)
                if (read > 0) {
                    offset += read
                    if (offset == frameBytes) {
                        emit(buffer.copyOf())
                        offset = 0
                    }
                } else if (read < 0) {
                    break
                }
            }
        } finally {
            callAudio?.exit()
            echoCanceler?.release()
            noiseSuppressor?.release()
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun createEchoCanceler(sessionId: Int): AcousticEchoCanceler? =
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        } else {
            null
        }

    private fun createNoiseSuppressor(sessionId: Int): NoiseSuppressor? =
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        } else {
            null
        }
}
