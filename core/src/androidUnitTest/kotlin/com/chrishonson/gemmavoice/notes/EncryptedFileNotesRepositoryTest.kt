package com.chrishonson.gemmavoice.notes

import com.chrishonson.gemmavoice.security.VaultCipher
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Flips every byte, so the test can tell the file is not stored as plain text. */
private object FlipCipher : VaultCipher {
    override fun encrypt(plaintext: ByteArray) = ByteArray(plaintext.size) { plaintext[it].toInt().inv().toByte() }
    override fun decrypt(ciphertext: ByteArray) = encrypt(ciphertext)
}

class EncryptedFileNotesRepositoryTest {
    private val file = File(Files.createTempDirectory("notes").toFile(), "notes.bin")

    @Test
    fun notesSurviveARestartAndAreNotPlainText() {
        val first = EncryptedFileNotesRepository(file, FlipCipher, now = { 7L })
        first.add("brake pads have 3 mm left")
        assertFalse(file.readBytes().decodeToString().contains("brake"))
        val reopened = EncryptedFileNotesRepository(file, FlipCipher)
        assertEquals(first.notes.value, reopened.notes.value)
    }

    @Test
    fun aDeleteIsWrittenToo() {
        val repo = EncryptedFileNotesRepository(file, FlipCipher)
        val note = repo.add("lease by Friday")
        repo.delete(note.id)
        assertEquals(emptyList(), EncryptedFileNotesRepository(file, FlipCipher).notes.value)
    }

    @Test
    fun noFileMeansNoNotes() {
        assertEquals(emptyList(), EncryptedFileNotesRepository(file, FlipCipher).notes.value)
    }
}
