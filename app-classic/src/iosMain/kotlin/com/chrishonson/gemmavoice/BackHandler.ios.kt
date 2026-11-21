package com.chrishonson.gemmavoice

import androidx.compose.runtime.Composable

@Suppress("UnusedParameter")
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // System back is an Android platform concept; no-op on iOS.
}
