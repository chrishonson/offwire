package com.chrishonson.gemmavoice.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

open class AesGcmVaultCipher(
    private val keyProvider: () -> SecretKey
) : VaultCipher {

    constructor(key: SecretKey) : this({ key })
    constructor(keyBytes: ByteArray) : this(SecretKeySpec(keyBytes, "AES"))

    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = keyProvider()
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        } catch (_: Exception) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        }
        val actualIv = cipher.iv ?: iv
        val ciphertext = cipher.doFinal(plaintext)
        val combined = ByteArray(actualIv.size + ciphertext.size)
        System.arraycopy(actualIv, 0, combined, 0, actualIv.size)
        System.arraycopy(ciphertext, 0, combined, actualIv.size, ciphertext.size)
        return combined
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= GCM_IV_LENGTH_BYTES + GCM_MIN_TAG_LENGTH_BYTES) {
            "Ciphertext too short"
        }
        val iv = ciphertext.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = ciphertext.copyOfRange(GCM_IV_LENGTH_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_MIN_TAG_LENGTH_BYTES = 16
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        fun generateKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE_BITS)
            return keyGen.generateKey()
        }
    }
}
