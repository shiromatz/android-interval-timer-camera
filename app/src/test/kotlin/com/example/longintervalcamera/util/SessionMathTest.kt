package com.example.longintervalcamera.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMathTest {
    @Test
    fun estimateCaptureCountIncludesStartAndEndWhenAligned() {
        val oneHour = 60L * 60L * 1000L
        assertEquals(25L, SessionMath.estimateCaptureCount(0L, 24L * oneHour, oneHour))
    }

    @Test
    fun nextScheduledAfterDoesNotAccumulateDelay() {
        val oneHour = 60L * 60L * 1000L
        val previousScheduled = 13L * oneHour
        val now = 13L * oneHour + 7L * 60L * 1000L

        assertEquals(14L * oneHour, SessionMath.nextScheduledAfter(previousScheduled, oneHour, now))
    }

    @Test
    fun firstSlotAfterSkipsMissedIntervals() {
        val oneHour = 60L * 60L * 1000L
        val base = 13L * oneHour
        val now = 16L * oneHour + 30L * 60L * 1000L

        assertEquals(17L * oneHour, SessionMath.firstSlotAfter(base, oneHour, now))
    }
}
