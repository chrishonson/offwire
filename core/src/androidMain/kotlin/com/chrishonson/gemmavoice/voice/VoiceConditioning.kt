package com.chrishonson.gemmavoice.voice

/** The voice every build ships: Caro Davy, a LibriVox reader, from Kyutai's tts-voices (voice-zero, CC0). */
const val DEFAULT_CONDITIONING_ASSET = "voices/caro-davy-conditioning.onnx"

/** Gitignored asset directory where a personal build puts its own voice, so it never reaches the repository. */
const val LOCAL_CONDITIONING_DIR = "voices/local"
const val LOCAL_CONDITIONING_FILE = "conditioning.onnx"

/** The asset to speak with: the local voice when [localFiles], the listing of [LOCAL_CONDITIONING_DIR], has one. */
fun conditioningAsset(localFiles: Array<String>?): String =
    if (localFiles?.contains(LOCAL_CONDITIONING_FILE) == true) {
        "$LOCAL_CONDITIONING_DIR/$LOCAL_CONDITIONING_FILE"
    } else {
        DEFAULT_CONDITIONING_ASSET
    }
