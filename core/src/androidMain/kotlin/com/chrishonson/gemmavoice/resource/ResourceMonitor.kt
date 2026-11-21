@file:JvmName("AndroidResourceMonitorActual")

package com.chrishonson.gemmavoice.resource

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.jvm.JvmName

private const val LOG_TAG = "GemmaVoice"
private const val BYTES_PER_MIB = 1024.0 * 1024.0
private const val KIB_PER_MIB = 1024.0
private const val SYSTEM_RESERVED_RAM_MB = 3 * 1024.0
private const val UTIME_INDEX = 11
private const val STIME_INDEX = 12

actual class ResourceMonitor actual constructor(platformContext: Any?) : ResourceSampler {
    private val activityManager =
        (platformContext as? Context)?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val clockTicksPerSecond = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L)
    private var previousProcessTicks: Long? = null
    private var previousWallMillis: Long? = null

    override fun sample(): ResourceSample {
        val now = System.currentTimeMillis()
        val processTicks = readProcessTicks()
        val cpu = if (processTicks != null) {
            val previousTicks = previousProcessTicks
            val previousWall = previousWallMillis
            previousProcessTicks = processTicks
            previousWallMillis = now
            if (previousTicks != null && previousWall != null) {
                calculateCpuPercent(previousTicks, processTicks, previousWall, now, clockTicksPerSecond)
            } else {
                null
            }
        } else {
            null
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val pssMb = Debug.getPss().toDouble() / KIB_PER_MIB
        return ResourceSample(
            cpuPercent = cpu,
            pssMb = pssMb,
            // The meter is full where the app would run into the system's own share of RAM. The system's free
            // memory would shrink as other apps and the file cache grow, and fill the meter with any model.
            availMb = activityManager?.let {
                ramHeadroomMb(memoryInfo.totalMem / BYTES_PER_MIB, SYSTEM_RESERVED_RAM_MB, pssMb)
            },
            at = now
        )
    }

    override fun logTurnPeaks(cpuPercent: Double?, pssMb: Double?) {
        val cpu = cpuPercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
        val pss = pssMb?.let { String.format(Locale.US, "%.1fMB", it) } ?: "--"
        Log.i(LOG_TAG, "Resource peaks: CPU $cpu PSS $pss")
        previousProcessTicks = null
        previousWallMillis = null
    }

    private fun readProcessTicks(): Long? = runCatching {
        val stat = File("/proc/self/stat").readText()
        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        fields[UTIME_INDEX].toLong() + fields[STIME_INDEX].toLong()
    }.getOrNull()
}
