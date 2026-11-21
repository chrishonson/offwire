package com.chrishonson.gemmavoice.resource

data class ResourceSample(
    val cpuPercent: Double?,
    val pssMb: Double?,
    /** RAM this app could still use, so [pssMb] plus this is where the memory meter is full. */
    val availMb: Double?,
    val at: Long
)

interface ResourceSampler {
    fun sample(): ResourceSample

    fun logTurnPeaks(cpuPercent: Double?, pssMb: Double?)
}

expect class ResourceMonitor(platformContext: Any? = null) : ResourceSampler

fun calculateCpuPercent(
    previousProcessTicks: Long,
    currentProcessTicks: Long,
    previousWallMillis: Long,
    currentWallMillis: Long,
    clockTicksPerSecond: Long
): Double? {
    val processTicks = currentProcessTicks - previousProcessTicks
    val wallMillis = currentWallMillis - previousWallMillis
    if (processTicks < 0L || wallMillis <= 0L || clockTicksPerSecond <= 0L) return null
    val wallTicks = wallMillis.toDouble() * clockTicksPerSecond.toDouble() / MILLIS_PER_SECOND
    return processTicks.toDouble() / wallTicks * PERCENT_MULTIPLIER
}

private const val MILLIS_PER_SECOND = 1000.0
private const val PERCENT_MULTIPLIER = 100.0
