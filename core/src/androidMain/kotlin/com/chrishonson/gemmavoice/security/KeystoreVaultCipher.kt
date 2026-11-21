package com.chrishonson.gemmavoice.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreVaultCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : AesGcmVaultCipher(
    keyProvider = { getOrCreateKey(keyAlias) }
) {
    companion object {
        const val DEFAULT_KEY_ALIAS = "health_vault_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        @Synchronized
        private fun getOrCreateKey(alias: String): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                return keyStore.getKey(alias, null) as SecretKey
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AesGcmVaultCipher.AES_KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(false)
                .build()
            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
    }
}
