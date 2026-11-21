package com.chrishonson.gemmavoice.security

import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VaultCipherTest {
    private val key = AesGcmVaultCipher.generateKey()
    private val cipher = AesGcmVaultCipher(key)

    @Test
    fun roundTripEncryptDecrypt() {
        val plaintext = "Sensitive health record data: BP 120/80".encodeToByteArray()
        val ciphertext = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(ciphertext)
        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
    }

    @Test
    fun roundTripTextExtensions() {
        val text = "Patient allergies: Penicillin"
        val encryptedBase64 = cipher.encryptText(text)
        assertFalse(encryptedBase64.contains("Penicillin"))
        val decrypted = cipher.decryptText(encryptedBase64)
        assertEquals(text, decrypted)
    }

    @Test
    fun ivUniquenessAcrossCalls() {
        val plaintext = "Identical message for IV test".encodeToByteArray()
        val ciphertexts = (1..50).map { cipher.encrypt(plaintext) }
        val ivs = ciphertexts.map { it.copyOfRange(0, AesGcmVaultCipher.GCM_IV_LENGTH_BYTES).toList() }
        assertEquals(50, ivs.toSet().size, "Every encryption call must produce a unique 12-byte IV")
    }

    @Test
    fun tamperedCiphertextFailsToDecrypt() {
        val plaintext = "Tamper check payload".encodeToByteArray()
        val ciphertext = cipher.encrypt(plaintext)

        // Modify one byte in the ciphertext body
        val tampered = ciphertext.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        assertFailsWith<GeneralSecurityException> {
            cipher.decrypt(tampered)
        }
    }

    @Test
    fun ciphertextTooShortFails() {
        assertFailsWith<IllegalArgumentException> {
            cipher.decrypt(byteArrayOf(1, 2, 3))
        }
    }
}
