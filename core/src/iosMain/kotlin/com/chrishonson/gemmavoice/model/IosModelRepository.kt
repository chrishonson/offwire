@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.model

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionDownloadTaskResumeData
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDomainMask
import platform.darwin.NSObject

private const val MODELS_DIR = "models"
private const val SESSION_IDENTIFIER = "app.offwire.classic.models"
private const val HTTP_OK_START = 200L
private const val HTTP_OK_END = 299L

private sealed interface Progress {
    data class Running(val bytes: Long) : Progress
    data object Done : Progress
    data class Failed(val message: String) : Progress
}

/**
 * Downloads models with a background [NSURLSession] so a transfer continues while the app is off screen.
 * The session delegate runs on the main queue, so [download] must be collected on the main dispatcher, which is
 * where `ChatStore` runs it. Archive specs are not unpacked on iOS yet.
 */
class IosModelRepository(
    baseDir: String = applicationSupportPath()
) : ModelRepository {
    private val modelsDir = "$baseDir/$MODELS_DIR"
    private val fileManager = NSFileManager.defaultManager
    private val delegate = DownloadDelegate(::modelPath)
    private val session: NSURLSession by lazy {
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(SESSION_IDENTIFIER),
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue
        )
    }

    init {
        fileManager.createDirectoryAtPath(
            modelsDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        NSURL.fileURLWithPath(modelsDir).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    override fun modelPath(spec: ModelSpec): String = "$modelsDir/${spec.fileName}"

    override fun isDownloaded(spec: ModelSpec): Boolean =
        !spec.isArchive && fileSize(modelPath(spec)) == spec.sizeBytes

    override fun download(spec: ModelSpec): Flow<Long> = flow {
        if (spec.isArchive) {
            throw UnsupportedOperationException("archive models are not unpacked on iOS yet")
        }
        val progress = delegate.states[spec.id] ?: start(spec)
        progress.transformWhile { update ->
            when (update) {
                is Progress.Running -> emit(update.bytes)
                Progress.Done -> emit(spec.sizeBytes)
                is Progress.Failed -> throw IllegalStateException(update.message)
            }
            update is Progress.Running
        }.collect { emit(it) }
    }

    private fun start(spec: ModelSpec): MutableStateFlow<Progress> {
        fileManager.removeItemAtPath(modelPath(spec), error = null)
        val progress = MutableStateFlow<Progress>(Progress.Running(0L))
        delegate.states[spec.id] = progress
        delegate.specs[spec.id] = spec
        val resumeData = delegate.resumeData.remove(spec.id)
        val task = if (resumeData != null) {
            session.downloadTaskWithResumeData(resumeData)
        } else {
            session.downloadTaskWithURL(requireNotNull(NSURL.URLWithString(spec.url)) { "Invalid URL ${spec.url}" })
        }
        task.taskDescription = spec.id
        task.resume()
        return progress
    }

    private fun fileSize(path: String): Long? {
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
        return (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
    }
}

private fun applicationSupportPath(): String =
    NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: ""

// Every callback arrives on the main queue, so these maps need no locking.
private class DownloadDelegate(
    private val destination: (ModelSpec) -> String
) : NSObject(), NSURLSessionDownloadDelegateProtocol {
    val states = mutableMapOf<String, MutableStateFlow<Progress>>()
    val specs = mutableMapOf<String, ModelSpec>()
    val resumeData = mutableMapOf<String, NSData>()

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        states[downloadTask.taskDescription]?.value = Progress.Running(totalBytesWritten)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        val id = downloadTask.taskDescription ?: return
        val spec = specs[id] ?: return
        val status = (downloadTask.response as? NSHTTPURLResponse)?.statusCode
        val result = if (status != null && status !in HTTP_OK_START..HTTP_OK_END) {
            Progress.Failed("Download failed with HTTP $status")
        } else {
            moveIntoPlace(didFinishDownloadingToURL, spec)
        }
        finish(id, result)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        val id = task.taskDescription ?: return
        if (didCompleteWithError == null) return
        (didCompleteWithError.userInfo[NSURLSessionDownloadTaskResumeData] as? NSData)?.let { resumeData[id] = it }
        finish(id, Progress.Failed(didCompleteWithError.localizedDescription))
    }

    private fun moveIntoPlace(temporary: NSURL, spec: ModelSpec): Progress {
        val fileManager = NSFileManager.defaultManager
        val target = destination(spec)
        fileManager.removeItemAtPath(target, error = null)
        val moved = fileManager.moveItemAtURL(temporary, toURL = NSURL.fileURLWithPath(target), error = null)
        val size = (fileManager.attributesOfItemAtPath(target, error = null)?.get(NSFileSize) as? NSNumber)
            ?.longLongValue
        return when {
            !moved -> Progress.Failed("Could not store ${spec.fileName}")
            size != spec.sizeBytes -> {
                fileManager.removeItemAtPath(target, error = null)
                Progress.Failed("Downloaded $size bytes of ${spec.sizeBytes} for ${spec.fileName}")
            }
            else -> Progress.Done
        }
    }

    private fun finish(id: String, result: Progress) {
        states.remove(id)?.value = result
        specs.remove(id)
    }
}
