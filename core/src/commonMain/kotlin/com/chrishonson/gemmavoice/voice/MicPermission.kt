package com.chrishonson.gemmavoice.voice

interface MicPermission {
    suspend fun ensureGranted(): Boolean
}
