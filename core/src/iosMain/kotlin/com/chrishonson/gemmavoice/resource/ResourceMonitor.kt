@file:OptIn(ExperimentalForeignApi::class)

package com.chrishonson.gemmavoice.resource

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970
import platform.darwin.TASK_VM_INFO
import platform.darwin.mach_msg_type_number_tVar
import platform.darwin.mach_task_self_
import platform.darwin.task_info
import platform.darwin.task_vm_info
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.rusage

private const val MILLIS_PER_SECOND = 1000.0
private const val MICROS_PER_SECOND = 1_000_000L
private const val BYTES_PER_MIB = 1024.0 * 1024.0
private const val BYTES_PER_INT = 4

actual class ResourceMonitor actual constructor(platformContext: Any?) : ResourceSampler {
    private var previousCpuMicros: Long? = null
    private var previousWallMillis: Long? = null

    override fun sample(): ResourceSample {
        val now = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
        val footprint = footprintBytes()
        val physical = NSProcessInfo.processInfo.physicalMemory.toDouble()
        return ResourceSample(
            cpuPercent = cpuPercent(now),
            pssMb = footprint?.let { it / BYTES_PER_MIB },
            availMb = footprint?.let { (physical - it).coerceAtLeast(0.0) / BYTES_PER_MIB },
            at = now
        )
    }

    override fun logTurnPeaks(cpuPercent: Double?, pssMb: Double?) {
        previousCpuMicros = null
        previousWallMillis = null
    }

    // Process CPU time over wall time, where 100 is one fully busy core, the scale the shared code divides by cores.
    private fun cpuPercent(nowMillis: Long): Double? {
        val micros = processCpuMicros() ?: return null
        val previousMicros = previousCpuMicros
        val previousWall = previousWallMillis
        previousCpuMicros = micros
        previousWallMillis = nowMillis
        if (previousMicros == null || previousWall == null) return null
        return calculateCpuPercent(previousMicros, micros, previousWall, nowMillis, MICROS_PER_SECOND)
    }

    private fun processCpuMicros(): Long? = memScoped {
        val usage = alloc<rusage>()
        if (getrusage(RUSAGE_SELF, usage.ptr) != 0) return@memScoped null
        val user = usage.ru_utime.tv_sec * MICROS_PER_SECOND + usage.ru_utime.tv_usec
        val system = usage.ru_stime.tv_sec * MICROS_PER_SECOND + usage.ru_stime.tv_usec
        user + system
    }

    private fun footprintBytes(): Double? = memScoped {
        val info = alloc<task_vm_info>()
        val count = alloc<mach_msg_type_number_tVar>()
        count.value = (sizeOf<task_vm_info>() / BYTES_PER_INT).convert()
        val status = task_info(mach_task_self_, TASK_VM_INFO.convert(), info.ptr.reinterpret(), count.ptr)
        if (status == 0) info.phys_footprint.toDouble() else null
    }
}
