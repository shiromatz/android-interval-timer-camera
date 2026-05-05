package com.example.longintervalcamera.util

object SessionMath {
    fun estimateCaptureCount(startMillis: Long, endMillis: Long, intervalMillis: Long): Long {
        if (intervalMillis <= 0L || endMillis < startMillis) return 0L
        return ((endMillis - startMillis) / intervalMillis) + 1L
    }

    fun nextScheduledAfter(
        previousScheduledMillis: Long,
        intervalMillis: Long,
        nowMillis: Long
    ): Long {
        return firstSlotAfter(previousScheduledMillis + intervalMillis, intervalMillis, nowMillis)
    }

    fun firstSlotAfter(baseMillis: Long, intervalMillis: Long, nowMillis: Long): Long {
        if (intervalMillis <= 0L) return baseMillis
        if (baseMillis > nowMillis) return baseMillis
        val skippedSlots = ((nowMillis - baseMillis) / intervalMillis) + 1L
        return baseMillis + skippedSlots * intervalMillis
    }
}
