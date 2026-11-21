package com.chrishonson.gemmavoice.model

import com.chrishonson.gemmavoice.net.NetworkActivity
import com.chrishonson.gemmavoice.net.NetworkClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidModelRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var tempDir: File
    private var receivedRangeHeader: String? = null

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("models-test").toFile()
        receivedRangeHeader = null
    }

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) {
            server.stop(0)
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun downloadResumesFromPartFileWithRangeHeader() {
        val fullData = "Complete model file contents for resume testing.".toByteArray(Charsets.UTF_8)
        val initialBytesCount = 16
        val initialPartData = fullData.copyOfRange(0, initialBytesCount)
        val remainingData = fullData.copyOfRange(initialBytesCount, fullData.size)

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/test-model.bin") { exchange ->
            receivedRangeHeader = exchange.requestHeaders.getFirst("Range")
            if (receivedRangeHeader != null) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_PARTIAL, remainingData.size.toLong())
                exchange.responseBody.use { it.write(remainingData) }
            } else {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, fullData.size.toLong())
                exchange.responseBody.use { it.write(fullData) }
            }
        }
        server.start()

        val port = server.address.port
        val fileName = "test-model.bin"
        val partFile = File(tempDir, "$fileName.part")
        partFile.writeBytes(initialPartData)

        val spec = ModelSpec(
            id = "test-model-id",
            displayName = "Test Model",
            url = "http://127.0.0.1:$port/test-model.bin",
            fileName = fileName,
            sizeBytes = fullData.size.toLong()
        )

        val repo = AndroidModelRepository(tempDir, NetworkClient(NetworkActivity(now = { 0L })))
        runBlocking {
            repo.download(spec).collect()
        }

        assertEquals("bytes=$initialBytesCount-", receivedRangeHeader)
        val finalFile = File(tempDir, fileName)
        assertTrue(finalFile.exists())
        assertFalse(partFile.exists())
        assertEquals(fullData.size.toLong(), finalFile.length())
        assertContentEquals(fullData, finalFile.readBytes())
    }
}
