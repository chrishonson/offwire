package com.chrishonson.gemmavoice.model

import android.content.Context
import com.chrishonson.gemmavoice.net.NetworkClient
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val MODELS_DIR = "models"
private const val BUFFER_SIZE = 64 * 1024
private const val EMIT_INTERVAL_BYTES = 1_048_576L
private const val HTTP_OK_START = 200
private const val HTTP_OK_END = 299

class AndroidModelRepository internal constructor(
    private val modelsDir: File,
    private val client: NetworkClient
) : ModelRepository {
    constructor(context: Context, client: NetworkClient) : this(
        File(
            (context.applicationContext ?: context).let { ctx ->
                ctx.getExternalFilesDir(null) ?: ctx.filesDir
            },
            MODELS_DIR
        ).apply { mkdirs() },
        client
    )

    override fun modelPath(spec: ModelSpec): String =
        if (spec.isArchive) unpackDir(spec).absolutePath else modelFile(spec).absolutePath

    override fun isDownloaded(spec: ModelSpec): Boolean {
        if (spec.isArchive) {
            val dir = unpackDir(spec)
            return spec.unpacked.all { File(dir, it.name).let { f -> f.exists() && f.length() == it.sizeBytes } }
        }
        val file = modelFile(spec)
        return file.exists() && file.length() == spec.sizeBytes
    }

    override fun download(spec: ModelSpec): Flow<Long> = flow {
        val targetFile = modelFile(spec)
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val partFile = File(parentDir, "${spec.fileName}.part")
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val response = client.open(spec.url, "model download: ${spec.id}", existingBytes)
        response.use { resp ->
            val code = resp.code
            if (code !in HTTP_OK_START..HTTP_OK_END) {
                throw IOException("Download failed with HTTP $code")
            }
            val append = existingBytes > 0L && resp.isPartial
            val startingBytes = if (append) existingBytes else 0L
            streamResponseToPartFile(resp.inputStream, partFile, append, startingBytes)
        }
        finalizePartFile(partFile, targetFile)
        if (spec.isArchive) {
            unpack(targetFile, spec)
        }
    }.flowOn(Dispatchers.IO)

    // Keeps only the files the spec names, flattened, and drops the archive once they are out.
    private fun unpack(archive: File, spec: ModelSpec) {
        val dir = unpackDir(spec).apply { mkdirs() }
        val wanted = spec.unpacked.map { it.name }.toSet()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            generateSequence { tar.nextEntry }
                .filter { !it.isDirectory && it.name.substringAfterLast('/') in wanted }
                .forEach { entry ->
                    FileOutputStream(File(dir, entry.name.substringAfterLast('/'))).use { out ->
                        tar.copyTo(out, BUFFER_SIZE)
                    }
                }
        }
        archive.delete()
        if (!isDownloaded(spec)) {
            throw IOException("Archive ${archive.name} did not contain the expected files")
        }
    }


    private fun modelFile(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    private fun unpackDir(spec: ModelSpec): File = File(modelsDir, spec.id)

    private suspend fun FlowCollector<Long>.streamResponseToPartFile(
        input: InputStream,
        partFile: File,
        append: Boolean,
        startingBytes: Long
    ) {
        FileOutputStream(partFile, append).use { output ->
            copyWithProgress(input, output, startingBytes)
        }
    }

    private suspend fun FlowCollector<Long>.copyWithProgress(
        input: InputStream,
        output: OutputStream,
        startingBytes: Long
    ) {
        var totalBytes = startingBytes
        var lastEmittedBytes = startingBytes
        emit(totalBytes)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead = input.read(buffer)
        while (bytesRead >= 0) {
            output.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
            if (totalBytes - lastEmittedBytes >= EMIT_INTERVAL_BYTES) {
                emit(totalBytes)
                lastEmittedBytes = totalBytes
            }
            bytesRead = input.read(buffer)
        }
        output.flush()
        if (totalBytes != lastEmittedBytes) {
            emit(totalBytes)
        }
    }

    private fun finalizePartFile(partFile: File, targetFile: File) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!partFile.renameTo(targetFile)) {
            throw IOException("Failed to rename ${partFile.name} to ${targetFile.name}")
        }
    }
}
