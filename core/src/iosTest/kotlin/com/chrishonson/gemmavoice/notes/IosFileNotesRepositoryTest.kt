package com.chrishonson.gemmavoice.notes

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosFileNotesRepositoryTest {
    private val path = "${NSTemporaryDirectory()}notes-${NSUUID().UUIDString}.json"

    @AfterTest
    fun removeFile() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    @Test
    fun startsEmptyWithoutAFile() {
        assertTrue(IosFileNotesRepository(path).notes.value.isEmpty())
    }

    @Test
    fun savedAndDeletedNotesSurviveARestart() {
        val first = IosFileNotesRepository(path, now = { 5L })
        val kept = first.add("parking is level 3")
        val gone = first.add("buy milk")
        first.delete(gone.id)

        assertEquals(listOf(kept), IosFileNotesRepository(path).notes.value)
    }
}
