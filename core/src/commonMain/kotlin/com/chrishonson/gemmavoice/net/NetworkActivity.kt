package com.chrishonson.gemmavoice.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_ENTRIES = 500

data class NetworkRequestEntry(
    val timeMs: Long,
    val host: String,
    val purpose: String,
    val bytes: Long
)

fun interface NetworkLogSink {
    fun record(entry: NetworkRequestEntry)

    object None : NetworkLogSink {
        override fun record(entry: NetworkRequestEntry) {}
    }
}

interface NetworkRequest {
    fun addBytes(n: Long)
    fun finish()
}

class NetworkActivity(
    private val now: () -> Long,
    private val sink: NetworkLogSink = NetworkLogSink.None
) {
    private val _inFlight = MutableStateFlow(0)
    val inFlight: StateFlow<Int> = _inFlight.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _entries = MutableStateFlow<List<NetworkRequestEntry>>(emptyList())
    val entries: StateFlow<List<NetworkRequestEntry>> = _entries.asStateFlow()

    fun begin(host: String, purpose: String): NetworkRequest {
        val timeMs = now()
        _inFlight.update { count ->
            val next = count + 1
            _isActive.value = true
            next
        }
        return ActiveRequest(timeMs, host, purpose)
    }

    private inner class ActiveRequest(
        private val timeMs: Long,
        private val host: String,
        private val purpose: String
    ) : NetworkRequest {
        private val byteCount = MutableStateFlow(0L)
        private val finished = MutableStateFlow(false)

        override fun addBytes(n: Long) {
            if (n <= 0L) return
            byteCount.update { it + n }
        }

        override fun finish() {
            if (!finished.compareAndSet(expect = false, update = true)) return
            _inFlight.update { count ->
                val next = (count - 1).coerceAtLeast(0)
                _isActive.value = next > 0
                next
            }
            val entry = NetworkRequestEntry(
                timeMs = timeMs,
                host = host,
                purpose = purpose,
                bytes = byteCount.value
            )
            _entries.update { list -> (list + entry).takeLast(MAX_ENTRIES) }
            sink.record(entry)
        }
    }
}
