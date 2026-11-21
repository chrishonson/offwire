package com.chrishonson.gemmavoice.resource

import kotlin.math.roundToInt

const val RESOURCE_BLOCK_COUNT = 10
private const val PERCENT_DIVISOR = 100.0
private const val MIB_PER_GIB = 1024.0
private const val ONE_DECIMAL = 10.0

expect fun cpuCoreCount(): Int

fun cpuDevicePercent(cpuPercent: Double?, cores: Int): Double? {
    if (cpuPercent == null || cores <= 0) return null
    return (cpuPercent / cores.toDouble()).coerceIn(0.0, PERCENT_DIVISOR)
}

fun cpuFraction(cpuPercent: Double?, cores: Int): Float {
    val devicePercent = cpuDevicePercent(cpuPercent, cores) ?: return 0f
    return (devicePercent / PERCENT_DIVISOR).toFloat().coerceIn(0f, 1f)
}

fun memoryFraction(pssMb: Double?, availMb: Double?): Float {
    val total = if (pssMb != null && availMb != null) pssMb + availMb else 0.0
    if (pssMb == null || total <= 0.0) return 0f
    return (pssMb / total).toFloat().coerceIn(0f, 1f)
}

/** RAM still free for this app: the device's total, less what the system keeps for itself, less what it uses. */
fun ramHeadroomMb(totalMb: Double, reservedMb: Double, usedMb: Double): Double =
    (totalMb - reservedMb - usedMb).coerceAtLeast(0.0)

fun resourceBlockCount(fraction: Float, blocks: Int = RESOURCE_BLOCK_COUNT): Int =
    (fraction.coerceIn(0f, 1f) * blocks).roundToInt()

fun formatCpuValue(cpuPercent: Double?, cores: Int): String =
    cpuDevicePercent(cpuPercent, cores)?.roundToInt()?.let { "$it%" } ?: "--"

fun formatMemoryValue(pssMb: Double?): String =
    pssMb?.let { mb ->
        if (mb >= MIB_PER_GIB) {
            "${(mb / MIB_PER_GIB * ONE_DECIMAL).roundToInt() / ONE_DECIMAL}G"
        } else {
            "${mb.roundToInt()}M"
        }
    } ?: "--"
