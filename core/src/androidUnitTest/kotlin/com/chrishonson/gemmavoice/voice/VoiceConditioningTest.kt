package com.chrishonson.gemmavoice.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceConditioningTest {

    @Test
    fun defaultVoiceWhenNoLocalDirectory() {
        assertEquals(DEFAULT_CONDITIONING_ASSET, conditioningAsset(null))
    }

    @Test
    fun defaultVoiceWhenLocalDirectoryIsEmpty() {
        assertEquals(DEFAULT_CONDITIONING_ASSET, conditioningAsset(emptyArray()))
    }

    @Test
    fun defaultVoiceWhenLocalDirectoryHoldsOtherFiles() {
        assertEquals(DEFAULT_CONDITIONING_ASSET, conditioningAsset(arrayOf("other-conditioning.onnx", "README")))
    }

    @Test
    fun localVoiceOverridesDefault() {
        assertEquals("voices/local/conditioning.onnx", conditioningAsset(arrayOf("conditioning.onnx")))
    }

    @Test
    fun defaultVoiceAssetIsBundled() {
        val asset = File("src/androidMain/assets", DEFAULT_CONDITIONING_ASSET)
        assertTrue(asset.isFile, "Missing ${asset.canonicalPath}")
    }
}
