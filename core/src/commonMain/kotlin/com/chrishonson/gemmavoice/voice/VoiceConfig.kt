package com.chrishonson.gemmavoice.voice

import com.chrishonson.gemmavoice.llm.LlmBackend

data class VoiceConfig(
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    val frameSamples: Int = DEFAULT_FRAME_SAMPLES,
    val rmsFloor: Double = DEFAULT_RMS_FLOOR,
    val vadThreshold: Float = DEFAULT_VAD_THRESHOLD,
    val bargeInThreshold: Float = DEFAULT_BARGE_IN_THRESHOLD,
    val endpointHangMs: Int = DEFAULT_ENDPOINT_HANG_MS,
    val minSpeechMs: Int = DEFAULT_MIN_SPEECH_MS,
    val maxSegmentMs: Int = DEFAULT_MAX_SEGMENT_MS,
    val leadInMs: Int = DEFAULT_LEAD_IN_MS,
    val bargeInEnabled: Boolean = true,
    val bargeInMinSpeechMs: Int = DEFAULT_BARGE_IN_MIN_SPEECH_MS,
    val mergeMs: Int = DEFAULT_MERGE_MS,
    val maxMergeMs: Int = DEFAULT_MAX_MERGE_MS,
    val halfDuplex: Boolean = false,
    // For echo cancellation that leaves reply speech audible to the VAD: the mic is ignored while a reply plays and
    // for echoTailMs after, so the reply is never heard as a user turn. A tap still interrupts.
    val ignoreMicWhileSpeaking: Boolean = false,
    val echoTailMs: Int = DEFAULT_ECHO_TAIL_MS,
    val firstMinChars: Int = DEFAULT_FIRST_MIN_CHARS,
    val firstMaxChars: Int = DEFAULT_FIRST_MAX_CHARS,
    val laterMinChars: Int = DEFAULT_LATER_MIN_CHARS,
    val assistantName: String = DEFAULT_ASSISTANT_NAME,
    val wakeWords: List<String> = listOf(assistantName.lowercase()),
    val overheardLines: Int = DEFAULT_OVERHEARD_LINES,
    val followUpMs: Int = DEFAULT_FOLLOW_UP_MS,
    val sendAudioToModel: Boolean = false,
    // GPU cut time to first token from 0.69 s to 0.26 s on the Pixel 9 Pro (docs/spikes/x0-gpu-image.md).
    val llmBackend: LlmBackend = LlmBackend.GPU,
    val explanationSpeechRate: Float = DEFAULT_EXPLANATION_SPEECH_RATE
) {
    val frameMs: Int
        get() = (frameSamples * MS_PER_SECOND) / sampleRateHz

    val bytesPerMs: Int
        get() = (sampleRateHz * BYTES_PER_SAMPLE) / MS_PER_SECOND

    val frameBytes: Int
        get() = frameSamples * BYTES_PER_SAMPLE

    companion object {
        const val BYTES_PER_SAMPLE = 2
        const val MS_PER_SECOND = 1000
        const val DEFAULT_SAMPLE_RATE_HZ = 16000
        const val DEFAULT_FRAME_SAMPLES = 512
        const val DEFAULT_RMS_FLOOR = 0.01
        const val DEFAULT_VAD_THRESHOLD = 0.7f
        const val DEFAULT_BARGE_IN_THRESHOLD = 0.8f
        const val DEFAULT_ENDPOINT_HANG_MS = 700
        const val DEFAULT_MIN_SPEECH_MS = 400
        const val DEFAULT_MAX_SEGMENT_MS = 25_000
        const val DEFAULT_LEAD_IN_MS = 300
        const val DEFAULT_BARGE_IN_MIN_SPEECH_MS = 200
        const val DEFAULT_MERGE_MS = 600
        const val DEFAULT_ECHO_TAIL_MS = 300
        const val DEFAULT_MAX_MERGE_MS = 10_000
        const val DEFAULT_FIRST_MIN_CHARS = 12
        const val DEFAULT_FIRST_MAX_CHARS = 100
        const val DEFAULT_LATER_MIN_CHARS = 40
        const val DEFAULT_ASSISTANT_NAME = "Computer"
        const val DEFAULT_OVERHEARD_LINES = 20
        const val DEFAULT_FOLLOW_UP_MS = 6_000
        const val DEFAULT_EXPLANATION_SPEECH_RATE = 0.8f
    }
}
