package com.chrishonson.gemmavoice.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkActivityTest {

    @Test
    fun beginAndFinishRecordsEntryAndTogglesActive() {
        var currentTime = 1000L
        val recorded = mutableListOf<NetworkRequestEntry>()
        val sink = NetworkLogSink { recorded.add(it) }
        val activity = NetworkActivity(now = { currentTime }, sink = sink)

        assertFalse(activity.isActive.value)
        assertEquals(0, activity.inFlight.value)
        assertTrue(activity.entries.value.isEmpty())

        val request = activity.begin(host = "example.com", purpose = "download")
        assertTrue(activity.isActive.value)
        assertEquals(1, activity.inFlight.value)

        currentTime = 2000L
        request.addBytes(512L)
        request.addBytes(256L)

        request.finish()
        assertFalse(activity.isActive.value)
        assertEquals(0, activity.inFlight.value)

        val expected = NetworkRequestEntry(
            timeMs = 1000L,
            host = "example.com",
            purpose = "download",
            bytes = 768L
        )
        assertEquals(listOf(expected), activity.entries.value)
        assertEquals(listOf(expected), recorded)
    }

    @Test
    fun overlappingRequestsMaintainActiveUntilBothFinish() {
        val activity = NetworkActivity(now = { 100L })

        val req1 = activity.begin("host1.org", "task1")
        assertTrue(activity.isActive.value)
        assertEquals(1, activity.inFlight.value)

        val req2 = activity.begin("host2.org", "task2")
        assertTrue(activity.isActive.value)
        assertEquals(2, activity.inFlight.value)

        req1.finish()
        assertTrue(activity.isActive.value)
        assertEquals(1, activity.inFlight.value)

        req2.finish()
        assertFalse(activity.isActive.value)
        assertEquals(0, activity.inFlight.value)
    }

    @Test
    fun doubleFinishIsIdempotent() {
        val recorded = mutableListOf<NetworkRequestEntry>()
        val sink = NetworkLogSink { recorded.add(it) }
        val activity = NetworkActivity(now = { 500L }, sink = sink)

        val req = activity.begin("host.com", "sync")
        req.addBytes(100L)

        req.finish()
        assertEquals(1, recorded.size)
        assertEquals(1, activity.entries.value.size)
        assertEquals(0, activity.inFlight.value)
        assertFalse(activity.isActive.value)

        req.finish()
        assertEquals(1, recorded.size)
        assertEquals(1, activity.entries.value.size)
        assertEquals(0, activity.inFlight.value)
        assertFalse(activity.isActive.value)
    }
}
