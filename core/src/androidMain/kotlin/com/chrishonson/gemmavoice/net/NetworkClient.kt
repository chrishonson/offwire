package com.chrishonson.gemmavoice.net

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_REDIRECTS = 10
private const val CONNECT_TIMEOUT_MS = 30_000
private const val READ_TIMEOUT_MS = 30_000
private val REDIRECT_CODES = setOf(
    HttpURLConnection.HTTP_MOVED_PERM,
    HttpURLConnection.HTTP_MOVED_TEMP,
    HttpURLConnection.HTTP_SEE_OTHER,
    307,
    308
)

class NetworkResponse(
    private val connection: HttpURLConnection,
    private val request: NetworkRequest
) : Closeable {
    val code: Int get() = connection.responseCode
    val isPartial: Boolean get() = code == HttpURLConnection.HTTP_PARTIAL

    val inputStream: InputStream by lazy {
        CountingInputStream(connection.inputStream, request)
    }

    override fun close() {
        try {
            connection.disconnect()
        } finally {
            request.finish()
        }
    }
}

class NetworkClient(private val activity: NetworkActivity) {

    fun open(url: String, purpose: String, resumeOffset: Long = 0L): NetworkResponse {
        // Begun before the first connect so the indicator covers redirect hops too.
        val request = activity.begin(URL(url).host, purpose)
        return try {
            NetworkResponse(openConnectionFollowingRedirects(url, resumeOffset), request)
        } catch (expected: IOException) {
            request.finish()
            throw expected
        }
    }

    private fun openConnectionFollowingRedirects(
        urlString: String,
        resumeOffset: Long
    ): HttpURLConnection {
        var currentUrl = URL(urlString)
        var redirects = 0
        while (redirects < MAX_REDIRECTS) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                if (resumeOffset > 0L) {
                    setRequestProperty("Range", "bytes=$resumeOffset-")
                }
            }
            val code = conn.responseCode
            if (code !in REDIRECT_CODES) {
                return conn
            }
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank()) {
                throw IOException("Redirect ($code) missing Location header")
            }
            currentUrl = URL(currentUrl, location)
            redirects++
        }
        throw IOException("Too many redirects ($MAX_REDIRECTS)")
    }
}

private class CountingInputStream(
    private val delegate: InputStream,
    private val request: NetworkRequest
) : InputStream() {

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) {
            request.addBytes(1L)
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        val count = delegate.read(b)
        if (count > 0) {
            request.addBytes(count.toLong())
        }
        return count
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = delegate.read(b, off, len)
        if (count > 0) {
            request.addBytes(count.toLong())
        }
        return count
    }

    override fun skip(n: Long): Long {
        val skipped = delegate.skip(n)
        if (skipped > 0L) {
            request.addBytes(skipped)
        }
        return skipped
    }

    override fun available(): Int = delegate.available()

    override fun markSupported(): Boolean = delegate.markSupported()

    override fun mark(readlimit: Int) = delegate.mark(readlimit)

    override fun reset() = delegate.reset()

    override fun close() = delegate.close()
}
