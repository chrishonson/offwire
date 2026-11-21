package com.chrishonson.gemmavoice.net

import android.util.Log
import java.io.File
import java.io.IOException

private const val LOG_TAG = "FileNetworkLogSink"

class FileNetworkLogSink(private val file: File) : NetworkLogSink {

    override fun record(entry: NetworkRequestEntry) {
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val line = buildJsonLine(entry)
            synchronized(this) {
                file.appendText(line, Charsets.UTF_8)
            }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Failed to write network log entry: ${e.message}")
        }
    }

    private fun buildJsonLine(entry: NetworkRequestEntry): String {
        val escapedHost = escape(entry.host)
        val escapedPurpose = escape(entry.purpose)
        return """{"t":${entry.timeMs},"host":"$escapedHost","purpose":"$escapedPurpose","bytes":${entry.bytes}}""" +
            "\n"
    }

    private fun escape(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
