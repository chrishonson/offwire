package com.chrishonson.gemmavoice.resource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourceMonitorTest {
    @Test
    fun processTicksConvertToPercentOfWallClockTicks() {
        val percent = calculateCpuPercent(
            previousProcessTicks = 100L,
            currentProcessTicks = 175L,
            previousWallMillis = 1_000L,
            currentWallMillis = 2_500L,
            clockTicksPerSecond = 100L
        )

        assertEquals(50.0, percent)
    }

    @Test
    fun invalidOrBackwardIntervalsHaveNoPercent() {
        assertNull(calculateCpuPercent(10L, 9L, 100L, 200L, 100L))
        assertNull(calculateCpuPercent(10L, 11L, 200L, 200L, 100L))
        assertNull(calculateCpuPercent(10L, 11L, 100L, 200L, 0L))
    }
}
