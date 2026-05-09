package com.example.longintervalcamera.data

data class SessionConfig(
    val sessionId: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val intervalMillis: Long,
    val saveDirectory: String,
    val jpegQuality: Int,
    val minBatteryPercent: Int,
    val minFreeSpaceBytes: Long,
    val blackoutModeEnabled: Boolean,
    val status: SessionStatus,
    val nextCaptureTimeMillis: Long?,
    val capturedCount: Int,
    val lastCaptureTimeMillis: Long?,
    val lastResult: String?,
    val runningStartedTimeMillis: Long? = null,
    val consecutiveCameraFailures: Int = 0
)
