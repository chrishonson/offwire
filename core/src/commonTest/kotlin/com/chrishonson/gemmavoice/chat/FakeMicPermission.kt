package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.voice.MicPermission

class FakeMicPermission(
    var granted: Boolean = true
) : MicPermission {
    var ensureGrantedCount: Int = 0

    override suspend fun ensureGranted(): Boolean {
        ensureGrantedCount++
        return granted
    }
}
