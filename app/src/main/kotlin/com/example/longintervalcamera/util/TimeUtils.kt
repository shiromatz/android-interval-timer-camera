package com.example.longintervalcamera.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtils {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val displayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val fileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(zone)
    private val sessionFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(zone)
    private val csvFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zone)

    fun nowMillis(): Long = System.currentTimeMillis()

    fun formatDisplay(millis: Long?): String {
        return millis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(displayFormatter)
        } ?: "-"
    }

    fun formatCsv(millis: Long?): String {
        return millis?.let { csvFormatter.format(Instant.ofEpochMilli(it)) } ?: ""
    }

    fun parseDisplay(value: String): Long? {
        return try {
            LocalDateTime.parse(value.trim(), displayFormatter).atZone(zone).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun fileNameFor(millis: Long): String = "${fileFormatter.format(Instant.ofEpochMilli(millis))}.jpg"

    fun sessionIdFor(millis: Long): String = "session_${sessionFormatter.format(Instant.ofEpochMilli(millis))}"

    fun defaultStartMillis(): Long = defaultStartMillis(nowMillis())

    fun defaultStartMillis(baseMillis: Long): Long = roundUpToInterval(baseMillis, FIVE_MINUTES_MILLIS)

    fun defaultEndMillis(startMillis: Long): Long = startMillis + ONE_HOUR_MILLIS

    private fun roundUpToInterval(millis: Long, intervalMillis: Long): Long {
        val remainder = millis % intervalMillis
        return if (remainder == 0L) millis else millis + (intervalMillis - remainder)
    }

    private const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L
    private const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
}
