package com.chrishonson.gemmavoice.security

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface VaultCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

@OptIn(ExperimentalEncodingApi::class)
fun VaultCipher.encryptText(text: String): String =
    Base64.Default.encode(encrypt(text.encodeToByteArray()))

@OptIn(ExperimentalEncodingApi::class)
fun VaultCipher.decryptText(base64Ciphertext: String): String =
    decrypt(Base64.Default.decode(base64Ciphertext)).decodeToString()
