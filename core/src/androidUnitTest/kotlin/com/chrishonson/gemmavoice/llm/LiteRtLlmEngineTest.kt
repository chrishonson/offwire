package com.chrishonson.gemmavoice.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class LiteRtLlmEngineTest {

    @Test
    fun defaultBackendIsCpu() {
        val engine = LiteRtLlmEngine(cacheDir = "/tmp/cache", assistantName = "TestAssistant")
        assertEquals(LlmBackend.CPU, engine.backend)
    }

    @Test
    fun customBackendIsConfigured() {
        val engine = LiteRtLlmEngine(
            cacheDir = "/tmp/cache",
            assistantName = "TestAssistant",
            backend = LlmBackend.GPU
        )
        assertEquals(LlmBackend.GPU, engine.backend)
    }
}
