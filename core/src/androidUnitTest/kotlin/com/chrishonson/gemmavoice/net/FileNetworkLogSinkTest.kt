package com.chrishonson.gemmavoice.net

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileNetworkLogSinkTest {
    private lateinit var tempFile: File

    @BeforeTest
    fun setUp() {
        tempFile = File.createTempFile("network-log-test", ".jsonl")
        tempFile.delete()
    }

    @AfterTest
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun writesParseableJsonLineWithEscaping() {
        val sink = FileNetworkLogSink(tempFile)
        val entry = NetworkRequestEntry(
            timeMs = 123456789L,
            host = "api.example.com",
            purpose = "model download: test \"quoted\" and \\escaped\\",
            bytes = 4096L
        )

        sink.record(entry)

        assertTrue(tempFile.exists())
        val lines = tempFile.readLines()
        assertEquals(1, lines.size)
        val line = lines.first()

        val expected = """{"t":123456789,"host":"api.example.com","purpose":""" +
            """"model download: test \"quoted\" and \\escaped\\","bytes":4096}"""
        assertEquals(expected, line)
    }

    @Test
    fun appendsMultipleEntries() {
        val sink = FileNetworkLogSink(tempFile)
        val entry1 = NetworkRequestEntry(100L, "host1", "p1", 10L)
        val entry2 = NetworkRequestEntry(200L, "host2", "p2", 20L)

        sink.record(entry1)
        sink.record(entry2)

        val lines = tempFile.readLines()
        assertEquals(2, lines.size)
        assertEquals("""{"t":100,"host":"host1","purpose":"p1","bytes":10}""", lines[0])
        assertEquals("""{"t":200,"host":"host2","purpose":"p2","bytes":20}""", lines[1])
    }
}
