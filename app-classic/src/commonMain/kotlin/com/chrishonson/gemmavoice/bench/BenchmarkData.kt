package com.chrishonson.gemmavoice.bench

val REFERENCE_SENTENCES: List<String> = listOf(
    "Computer, what time is it?",
    "Computer, remind me to call my daughter tomorrow morning.",
    "My doctor prescribed metformin twice a day with meals.",
    "What was my last cholesterol result?",
    "The cardiologist said my blood pressure is a little high.",
    "I take lisinopril and atorvastatin every evening.",
    "Can you read me the instructions on this prescription?",
    "My appointment with Doctor Patel is next Thursday afternoon.",
    "How many steps did I walk yesterday?",
    "Turn off the kitchen lights and lock the front door.",
    "Computer, what does my hemoglobin result mean?",
    "I had a colonoscopy in March and everything looked normal.",
    "Please tell me a short story about a lighthouse keeper.",
    "What is the weather going to be like this weekend?",
    "Thank you, that is all for now."
)

@Suppress("MagicNumber")
val AUDIO_VS_TEXT_SENTENCE_INDICES: List<Int> = listOf(3, 7, 11, 13)

private const val TWO_DIGIT_THRESHOLD = 10

fun formatRecordingFileName(index: Int): String {
    val prefix = if (index < TWO_DIGIT_THRESHOLD) "0$index" else "$index"
    return "$prefix.wav"
}

enum class CandidateType {
    MOONSHINE,
    NEMO_TRANSDUCER
}

data class CandidateSpec(
    val id: String,
    val type: CandidateType,
    val requiredFiles: List<String>
)

val CANDIDATE_SPECS: List<CandidateSpec> = listOf(
    CandidateSpec(
        id = "moonshine-tiny",
        type = CandidateType.MOONSHINE,
        requiredFiles = listOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt")
    ),
    CandidateSpec(
        id = "moonshine-base",
        type = CandidateType.MOONSHINE,
        requiredFiles = listOf("encoder_model.ort", "decoder_model_merged.ort", "tokens.txt")
    ),
    CandidateSpec(
        id = "parakeet-110m",
        type = CandidateType.NEMO_TRANSDUCER,
        requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
    ),
    CandidateSpec(
        id = "parakeet-0.6b",
        type = CandidateType.NEMO_TRANSDUCER,
        requiredFiles = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
    )
)

data class SentenceResult(
    val index: Int,
    val ms: Long,
    val text: String,
    val wer: Double
)

data class CandidateResult(
    val candidate: String,
    val loadMs: Long,
    val pssDeltaKb: Long,
    val meanMs: Double,
    val p90Ms: Double,
    val maxMs: Long,
    val overallWer: Double,
    val perSentence: List<SentenceResult>
)

data class AudioVsTextTiming(
    val index: Int,
    val audioFirstEmissionMs: Long,
    val audioTotalMs: Long,
    val textFirstEmissionMs: Long,
    val textTotalMs: Long
)

data class BenchmarkReport(
    val deviceModel: String,
    val gemmaPssKb: Long?,
    val candidates: List<CandidateResult>,
    val audioVsText: List<AudioVsTextTiming>
) {
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"deviceModel\": \"").append(escapeJson(deviceModel)).append("\",\n")
        append("  \"gemmaPssKb\": ").append(gemmaPssKb?.toString() ?: "null").append(",\n")
        if (candidates.isEmpty()) {
            append("  \"candidates\": [],\n")
        } else {
            append("  \"candidates\": [\n")
            candidates.forEachIndexed { cIndex, cand ->
                append("    {\n")
                append("      \"candidate\": \"").append(escapeJson(cand.candidate)).append("\",\n")
                append("      \"loadMs\": ").append(cand.loadMs).append(",\n")
                append("      \"pssDeltaKb\": ").append(cand.pssDeltaKb).append(",\n")
                append("      \"meanMs\": ").append(cand.meanMs).append(",\n")
                append("      \"p90Ms\": ").append(cand.p90Ms).append(",\n")
                append("      \"maxMs\": ").append(cand.maxMs).append(",\n")
                append("      \"overallWer\": ").append(cand.overallWer).append(",\n")
                append("      \"perSentence\": [\n")
                cand.perSentence.forEachIndexed { sIndex, s ->
                    append("        {\n")
                    append("          \"index\": ").append(s.index).append(",\n")
                    append("          \"ms\": ").append(s.ms).append(",\n")
                    append("          \"text\": \"").append(escapeJson(s.text)).append("\",\n")
                    append("          \"wer\": ").append(s.wer).append("\n")
                    append("        }")
                    if (sIndex < cand.perSentence.size - 1) append(",")
                    append("\n")
                }
                append("      ]\n")
                append("    }")
                if (cIndex < candidates.size - 1) append(",")
                append("\n")
            }
            append("  ],\n")
        }
        if (audioVsText.isEmpty()) {
            append("  \"audioVsText\": []\n")
        } else {
            append("  \"audioVsText\": [\n")
            audioVsText.forEachIndexed { aIndex, avt ->
                append("    {\n")
                append("      \"index\": ").append(avt.index).append(",\n")
                append("      \"audioFirstEmissionMs\": ").append(avt.audioFirstEmissionMs).append(",\n")
                append("      \"audioTotalMs\": ").append(avt.audioTotalMs).append(",\n")
                append("      \"textFirstEmissionMs\": ").append(avt.textFirstEmissionMs).append(",\n")
                append("      \"textTotalMs\": ").append(avt.textTotalMs).append("\n")
                append("    }")
                if (aIndex < audioVsText.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
        }
        append("}\n")
    }
}

private fun escapeJson(value: String): String = buildString {
    for (char in value) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
