package com.example.longintervalcamera.data

import android.annotation.SuppressLint
import android.content.Context

class SessionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSession(): SessionConfig? {
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        return SessionConfig(
            sessionId = sessionId,
            startTimeMillis = prefs.getLong(KEY_START_TIME, 0L),
            endTimeMillis = prefs.getLong(KEY_END_TIME, 0L),
            intervalMillis = prefs.getLong(KEY_INTERVAL, 0L),
            saveDirectory = prefs.getString(KEY_SAVE_DIRECTORY, "") ?: "",
            jpegQuality = prefs.getInt(KEY_JPEG_QUALITY, 90),
            minBatteryPercent = prefs.getInt(KEY_MIN_BATTERY, 10),
            minFreeSpaceBytes = prefs.getLong(KEY_MIN_FREE_SPACE, DEFAULT_MIN_FREE_SPACE_BYTES),
            blackoutModeEnabled = prefs.getBoolean(KEY_BLACKOUT, true),
            status = runCatching {
                SessionStatus.valueOf(prefs.getString(KEY_STATUS, SessionStatus.NOT_CONFIGURED.name)!!)
            }.getOrDefault(SessionStatus.NOT_CONFIGURED),
            nextCaptureTimeMillis = prefs.getLongOrNull(KEY_NEXT_CAPTURE),
            capturedCount = prefs.getInt(KEY_CAPTURED_COUNT, 0),
            lastCaptureTimeMillis = prefs.getLongOrNull(KEY_LAST_CAPTURE),
            lastResult = prefs.getString(KEY_LAST_RESULT, null),
            runningStartedTimeMillis = prefs.getLongOrNull(KEY_RUNNING_STARTED),
            consecutiveCameraFailures = prefs.getInt(KEY_CAMERA_FAILURES, 0)
        )
    }

    @SuppressLint("ApplySharedPref")
    fun saveSession(config: SessionConfig) {
        prefs.edit()
            .putString(KEY_SESSION_ID, config.sessionId)
            .putLong(KEY_START_TIME, config.startTimeMillis)
            .putLong(KEY_END_TIME, config.endTimeMillis)
            .putLong(KEY_INTERVAL, config.intervalMillis)
            .putString(KEY_SAVE_DIRECTORY, config.saveDirectory)
            .putInt(KEY_JPEG_QUALITY, config.jpegQuality)
            .putInt(KEY_MIN_BATTERY, config.minBatteryPercent)
            .putLong(KEY_MIN_FREE_SPACE, config.minFreeSpaceBytes)
            .putBoolean(KEY_BLACKOUT, config.blackoutModeEnabled)
            .putString(KEY_STATUS, config.status.name)
            .putNullableLong(KEY_NEXT_CAPTURE, config.nextCaptureTimeMillis)
            .putInt(KEY_CAPTURED_COUNT, config.capturedCount)
            .putNullableLong(KEY_LAST_CAPTURE, config.lastCaptureTimeMillis)
            .putString(KEY_LAST_RESULT, config.lastResult)
            .putNullableLong(KEY_RUNNING_STARTED, config.runningStartedTimeMillis)
            .putInt(KEY_CAMERA_FAILURES, config.consecutiveCameraFailures)
            .commit()
    }

    fun updateSession(transform: (SessionConfig) -> SessionConfig): SessionConfig? {
        val current = getSession() ?: return null
        val updated = transform(current)
        saveSession(updated)
        return updated
    }

    fun hasUnfinishedSession(): Boolean = getSession()?.status?.isUnfinished == true

    private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? {
        return if (contains(key)) getLong(key, 0L) else null
    }

    private fun android.content.SharedPreferences.Editor.putNullableLong(
        key: String,
        value: Long?
    ): android.content.SharedPreferences.Editor {
        return if (value == null) remove(key) else putLong(key, value)
    }

    companion object {
        const val PREFS_NAME = "long_interval_camera_session"
        const val DEFAULT_MIN_FREE_SPACE_BYTES = 1_000_000_000L

        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_SAVE_DIRECTORY = "save_directory"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_MIN_BATTERY = "min_battery"
        private const val KEY_MIN_FREE_SPACE = "min_free_space"
        private const val KEY_BLACKOUT = "blackout"
        private const val KEY_STATUS = "status"
        private const val KEY_NEXT_CAPTURE = "next_capture"
        private const val KEY_CAPTURED_COUNT = "captured_count"
        private const val KEY_LAST_CAPTURE = "last_capture"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_RUNNING_STARTED = "running_started"
        private const val KEY_CAMERA_FAILURES = "camera_failures"
    }
}
