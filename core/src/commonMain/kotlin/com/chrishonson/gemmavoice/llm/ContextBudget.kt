package com.chrishonson.gemmavoice.llm

/**
 * Gemma's context on every device. A bigger one slows every new conversation's first word: on a Pixel 9 Pro it took
 * 1.4 s at 4,096 tokens, 2.4 s at 8,192 and 4.8 s at 32,000 (LlmBenchmarkActivity with harness=app, 2026-11-09).
 */
const val CONTEXT_TOKENS = 4_096
