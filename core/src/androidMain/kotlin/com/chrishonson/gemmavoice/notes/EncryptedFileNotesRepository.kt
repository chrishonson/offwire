package com.chrishonson.gemmavoice.notes

import com.chrishonson.gemmavoice.security.VaultCipher
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val NOTES_SERIALIZER = ListSerializer(Note.serializer())

/** Keeps the notes as one encrypted JSON file, rewritten on every change. */
class EncryptedFileNotesRepository(
    private val file: File,
    private val cipher: VaultCipher,
    now: () -> Long = System::currentTimeMillis
) : InMemoryNotesRepository(read(file, cipher), now) {
    @Synchronized
    override fun onChanged() {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(cipher.encrypt(Json.encodeToString(NOTES_SERIALIZER, notes.value).encodeToByteArray()))
        check(temp.renameTo(file)) { "Could not replace ${file.name}" }
    }

    private companion object {
        fun read(file: File, cipher: VaultCipher): List<Note> =
            if (file.exists()) {
                Json.decodeFromString(NOTES_SERIALIZER, cipher.decrypt(file.readBytes()).decodeToString())
            } else {
                emptyList()
            }
    }
}
