package com.chrishonson.gemmavoice.voice

import kotlinx.coroutines.flow.Flow

interface AudioStream {
    fun frames(): Flow<ByteArray>
}
