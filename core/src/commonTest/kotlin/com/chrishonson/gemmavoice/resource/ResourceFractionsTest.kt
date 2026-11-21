package com.chrishonson.gemmavoice.resource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceFractionsTest {
    @Test
    fun cpuDevicePercentNormalizesByCoreCountAndClamps() {
        assertNull(cpuDevicePercent(null, 8))
        assertNull(cpuDevicePercent(63.0, 0))
        assertNull(cpuDevicePercent(63.0, -2))
        assertEquals(0.0, cpuDevicePercent(0.0, 8))
        assertEquals(31.25, cpuDevicePercent(250.0, 8) ?: -1.0, 0.0001)
        assertEquals(100.0, cpuDevicePercent(900.0, 8))
        assertEquals(0.0, cpuDevicePercent(-10.0, 4))
    }

    @Test
    fun cpuFractionNormalizesByCoreCountAndClamps() {
        assertEquals(0f, cpuFraction(null, 8))
        assertEquals(0f, cpuFraction(0.0, 8))
        assertEquals(0.63f, cpuFraction(63.0, 1), 0.0001f)
        assertEquals(0.3125f, cpuFraction(250.0, 8), 0.0001f)
        assertEquals(1f, cpuFraction(900.0, 8))
        assertEquals(0f, cpuFraction(63.0, 0))
        assertEquals(0f, cpuFraction(63.0, -2))
        assertEquals(0f, cpuFraction(-10.0, 4))
    }

    @Test
    fun memoryFractionDividesPssByTotalAndHandlesInvalidInputs() {
        assertEquals(0f, memoryFraction(null, 4096.0))
        assertEquals(0f, memoryFraction(1945.6, null))
        assertEquals(0.3220339f, memoryFraction(1945.6, 4096.0), 0.0001f)
        assertEquals(0f, memoryFraction(0.0, 0.0))
        assertEquals(0f, memoryFraction(100.0, -100.0))
    }

    @Test
    fun resourceBlockCountRoundsAndClamps() {
        assertEquals(0, resourceBlockCount(0f))
        assertEquals(0, resourceBlockCount(0.04f))
        assertEquals(5, resourceBlockCount(0.5f))
        assertEquals(10, resourceBlockCount(1f))
        assertEquals(0, resourceBlockCount(-0.5f))
        assertEquals(10, resourceBlockCount(1.5f))
    }

    @Test
    fun formatCpuAndMemoryValuesMatchExpectedUnits() {
        assertEquals("--", formatCpuValue(null, 1))
        assertEquals("--", formatCpuValue(null, 8))
        assertEquals("--", formatCpuValue(63.0, 0))
        assertEquals("63%", formatCpuValue(63.0, 1))
        assertEquals("31%", formatCpuValue(250.0, 8))
        assertEquals("100%", formatCpuValue(900.0, 8))
        assertEquals("--", formatMemoryValue(null))
        assertEquals("512M", formatMemoryValue(512.0))
        assertEquals("1.9G", formatMemoryValue(1945.6))
    }

    @Test
    fun theMemoryMeterIsFullWhereTheSystemsReserveBegins() {
        // A 16 GB Pixel with 3 GB kept by the system: E2B at 3 GB is about a quarter, E4B at 5.2 GB about 40%.
        val total = 15_583.0
        val reserved = 3_072.0
        assertEquals(9_439.0, ramHeadroomMb(total, reserved, usedMb = 3_072.0), 0.0001)
        assertEquals(0.25f, memoryFraction(3_072.0, ramHeadroomMb(total, reserved, 3_072.0)), 0.01f)
        assertEquals(0.42f, memoryFraction(5_325.0, ramHeadroomMb(total, reserved, 5_325.0)), 0.01f)
    }

    @Test
    fun anAppPastTheReserveHasNoHeadroomAndAFullMeter() {
        assertEquals(0.0, ramHeadroomMb(totalMb = 4_000.0, reservedMb = 3_072.0, usedMb = 2_000.0))
        assertEquals(1f, memoryFraction(2_000.0, 0.0))
    }

    @Test
    fun cpuCoreCountIsAtLeastOne() {
        assertTrue(cpuCoreCount() >= 1)
    }
}
