package com.chrishonson.gemmavoice

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LandscapeTest {

    @Test
    fun widerDimensionsReturnTrue() {
        assertTrue(isLandscape(width = 800.dp, height = 360.dp))
    }

    @Test
    fun tallerDimensionsReturnFalse() {
        assertFalse(isLandscape(width = 360.dp, height = 800.dp))
    }

    @Test
    fun squareDimensionsReturnFalse() {
        assertFalse(isLandscape(width = 500.dp, height = 500.dp))
    }
}
