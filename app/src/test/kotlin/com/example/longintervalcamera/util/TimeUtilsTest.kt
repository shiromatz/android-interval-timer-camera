package com.example.longintervalcamera.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun defaultStartRoundsUpToNextFiveMinuteBoundary() {
        val base = (10L * 60L * 60L + 2L * 60L + 13L) * 1000L

        assertEquals((10L * 60L + 5L) * 60L * 1000L, TimeUtils.defaultStartMillis(base))
    }

    @Test
    fun defaultStartKeepsExactFiveMinuteBoundary() {
        val base = (10L * 60L + 5L) * 60L * 1000L

        assertEquals(base, TimeUtils.defaultStartMillis(base))
    }

    @Test
    fun defaultEndIsOneHourAfterStart() {
        val start = 10L * 60L * 60L * 1000L

        assertEquals(11L * 60L * 60L * 1000L, TimeUtils.defaultEndMillis(start))
    }
}
