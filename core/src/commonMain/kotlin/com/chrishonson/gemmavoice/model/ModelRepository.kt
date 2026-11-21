package com.chrishonson.gemmavoice.model

import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun modelPath(spec: ModelSpec): String
    fun isDownloaded(spec: ModelSpec): Boolean
    fun download(spec: ModelSpec): Flow<Long>
}
