package com.chrishonson.gemmavoice.model

data class ModelFile(
    val name: String,
    val sizeBytes: Long
)

/**
 * A downloadable model. [sizeBytes] is the download size. When [unpacked] is set the download is a tar.bz2
 * archive and the model is the directory holding those files, so a download is complete only once they all exist.
 * A device with less total RAM than [minRamBytes] is not offered the model.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val unpacked: List<ModelFile> = emptyList(),
    val minRamBytes: Long = 0L
) {
    val isArchive: Boolean
        get() = unpacked.isNotEmpty()
}

object Models {
    val GEMMA_4_E2B = ModelSpec(
        id = "gemma-4-E2B-it",
        displayName = "Gemma 4 E2B",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L
    )

    val GEMMA_4_E4B = ModelSpec(
        id = "gemma-4-E4B-it",
        displayName = "Gemma 4 E4B",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L,
        // iOS kills the app at 1.2 GB while loading it on a 3 GB iPad. A 16 GB Pixel 9 Pro runs it.
        minRamBytes = 6_000_000_000L
    )

    /** The language models the user can pick from; the first is the default. */
    val GEMMA = listOf(GEMMA_4_E2B, GEMMA_4_E4B)

    /** The language models a device with [totalRamBytes] can run. */
    fun languageModelsFor(totalRamBytes: Long): List<ModelSpec> = GEMMA.filter { totalRamBytes >= it.minRamBytes }

    // Kyutai Pocket TTS as exported for sherpa-onnx. The archive's reference encoder is not used (see
    // PocketSpeechSynthesizer), so it is left in the archive and never unpacked.
    val POCKET_TTS = ModelSpec(
        id = "pocket-tts",
        displayName = "Computer voice",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
            "sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2",
        fileName = "sherpa-onnx-pocket-tts-int8-2026-01-26.tar.bz2",
        sizeBytes = 98_336_520L,
        unpacked = listOf(
            ModelFile(name = "lm_flow.int8.onnx", sizeBytes = 9_962_530L),
            ModelFile(name = "lm_main.int8.onnx", sizeBytes = 76_341_079L),
            ModelFile(name = "decoder.int8.onnx", sizeBytes = 22_693_618L),
            ModelFile(name = "text_conditioner.onnx", sizeBytes = 16_388_343L),
            ModelFile(name = "vocab.json", sizeBytes = 69_478L),
            ModelFile(name = "token_scores.json", sizeBytes = 123_616L)
        )
    )
}
