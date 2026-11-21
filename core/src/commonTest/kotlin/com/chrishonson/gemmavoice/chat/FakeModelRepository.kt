package com.chrishonson.gemmavoice.chat

import com.chrishonson.gemmavoice.model.ModelRepository
import com.chrishonson.gemmavoice.model.ModelSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield

class FakeModelRepository(
    var downloaded: Boolean = false,
    var path: String = "/fake/models/gemma-4-E2B-it.litertlm"
) : ModelRepository {
    var progressEmissions: List<Long> = emptyList()
    var downloadError: Throwable? = null
    var downloadCalls: Int = 0

    override fun modelPath(spec: ModelSpec): String = path

    override fun isDownloaded(spec: ModelSpec): Boolean = downloaded

    override fun download(spec: ModelSpec): Flow<Long> = flow {
        downloadCalls++
        for (bytes in progressEmissions) {
            emit(bytes)
            yield()
        }
        downloadError?.let { throw it }
        downloaded = true
    }
}
