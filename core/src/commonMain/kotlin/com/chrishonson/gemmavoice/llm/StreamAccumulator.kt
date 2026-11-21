package com.chrishonson.gemmavoice.llm

class StreamAccumulator {
    private var current: String = ""

    fun append(chunk: String): String {
        current = if (chunk.startsWith(current)) {
            chunk
        } else {
            current + chunk
        }
        return current
    }
}
