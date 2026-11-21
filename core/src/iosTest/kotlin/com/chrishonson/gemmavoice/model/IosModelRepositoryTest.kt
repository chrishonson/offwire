@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.model

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.Foundation.NSUTF8StringEncoding
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosModelRepositoryTest {
    private val baseDir = NSTemporaryDirectory() + NSUUID().UUIDString
    private val repo = IosModelRepository(baseDir)
    private val spec = ModelSpec(
        id = "tiny",
        displayName = "Tiny",
        // An unregistered scheme fails at once, so no test touches the network.
        url = "unsupported://localhost/tiny.bin",
        fileName = "tiny.bin",
        sizeBytes = 5L
    )

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(baseDir, error = null)
    }

    private fun write(contents: String) {
        NSString.create(string = contents)
            .writeToFile(repo.modelPath(spec), atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun exists() = NSFileManager.defaultManager.fileExistsAtPath(repo.modelPath(spec))

    @Test
    fun fileOfTheRightSizeIsDownloaded() {
        write("12345")
        assertTrue(repo.isDownloaded(spec))
        assertEquals("$baseDir/models/tiny.bin", repo.modelPath(spec))
    }

    @Test
    fun missingFileIsNotDownloaded() {
        assertFalse(repo.isDownloaded(spec))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun partialFileIsNotDownloadedAndIsRemovedWhenADownloadStarts() = runTest {
        write("123")
        assertFalse(repo.isDownloaded(spec))
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { repo.download(spec).collect { } }
        assertFalse(exists())
        job.cancel()
    }

    @Test
    fun archiveSpecIsNotDownloadedAndCannotBeDownloaded() = runTest {
        val archive = spec.copy(unpacked = listOf(ModelFile("a.bin", 5L)))
        write("12345")
        assertFalse(repo.isDownloaded(archive))
        assertFailsWith<UnsupportedOperationException> { repo.download(archive).toList() }
    }
}
