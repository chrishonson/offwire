package com.chrishonson.gemmavoice

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun AppForegroundEffect(onForeground: () -> Unit)
