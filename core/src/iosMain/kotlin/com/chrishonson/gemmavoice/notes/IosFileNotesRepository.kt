package com.chrishonson.gemmavoice.notes

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

private val NOTES_SERIALIZER = ListSerializer(Note.serializer())
private const val MILLIS_PER_SECOND = 1000.0

/**
 * Keeps the notes as one JSON file, rewritten on every change. iOS encrypts app files at rest with the device
 * passcode, which stands in for the Keystore cipher Android uses.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileNotesRepository(
    private val path: String = defaultNotesPath(),
    now: () -> Long = { (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong() }
) : InMemoryNotesRepository(read(path), now) {
    override fun onChanged() {
        val json = Json.encodeToString(NOTES_SERIALIZER, notes.value)
        val written = NSString.create(string = json)
            .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        check(written) { "Could not write $path" }
    }

    private companion object {
        fun read(path: String): List<Note> =
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
                ?.let { Json.decodeFromString(NOTES_SERIALIZER, it) }
                .orEmpty()
    }
}

/** A directory of its own, kept out of iCloud backups so notes never leave the device. */
@OptIn(ExperimentalForeignApi::class)
private fun defaultNotesPath(): String {
    val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .first() as String
    val dir = "$support/Notes"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    // On the directory, so the atomic rewrite of the file inside cannot drop it.
    NSURL.fileURLWithPath(dir, isDirectory = true)
        .setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return "$dir/notes.json"
}
