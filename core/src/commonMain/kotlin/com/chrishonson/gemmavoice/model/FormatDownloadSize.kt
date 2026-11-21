package com.chrishonson.gemmavoice.model

private const val ONE_GB_BYTES = 1_000_000_000L
private const val ONE_MB_BYTES = 1_000_000L
private const val TENTHS_OF_GB = 100_000_000L
private const val HALF_TENTH_OF_GB = 50_000_000L
private const val HALF_MB = 500_000L
private const val TENTHS_DIVISOR = 10

fun formatDownloadSize(bytes: Long): String =
    if (bytes >= ONE_GB_BYTES) {
        val tenths = (bytes + HALF_TENTH_OF_GB) / TENTHS_OF_GB
        val whole = tenths / TENTHS_DIVISOR
        val remainder = tenths % TENTHS_DIVISOR
        "$whole.$remainder GB"
    } else {
        val mb = (bytes + HALF_MB) / ONE_MB_BYTES
        "$mb MB"
    }
