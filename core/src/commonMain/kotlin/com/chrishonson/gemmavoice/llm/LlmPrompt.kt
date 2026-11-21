package com.chrishonson.gemmavoice.llm

sealed interface LlmPrompt {
    data class Text(val text: String) : LlmPrompt

    data class Image(val imagePath: String, val instruction: String) : LlmPrompt

    data class Audio(val wavBytes: ByteArray, val instruction: String) : LlmPrompt {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Audio
            return wavBytes.contentEquals(other.wavBytes) && instruction == other.instruction
        }

        override fun hashCode(): Int {
            var result = wavBytes.contentHashCode()
            result = 31 * result + instruction.hashCode()
            return result
        }
    }
}
