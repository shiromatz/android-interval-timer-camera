package com.example.longintervalcamera.logging

import com.example.longintervalcamera.data.CaptureResult
import com.example.longintervalcamera.data.SessionConfig
import com.example.longintervalcamera.storage.ImageStorageManager
import com.example.longintervalcamera.util.TimeUtils
import java.io.File

class CaptureLogger(private val storageManager: ImageStorageManager) {
    fun append(
        config: SessionConfig,
        scheduledTimeMillis: Long?,
        actualTimeMillis: Long?,
        result: CaptureResult,
        file: File?,
        batteryPercent: Int?,
        freeSpaceBytes: Long?,
        errorType: String? = null,
        errorMessage: String? = null
    ) {
        val row = listOf(
            config.sessionId,
            TimeUtils.formatCsv(scheduledTimeMillis),
            TimeUtils.formatCsv(actualTimeMillis),
            result.name,
            file?.absolutePath.orEmpty(),
            batteryPercent?.toString().orEmpty(),
            freeSpaceBytes?.toString().orEmpty(),
            errorType.orEmpty(),
            errorMessage.orEmpty()
        ).joinToString(",") { csvEscape(it) }

        val text = buildString {
            if (!storageManager.logExists(config)) append(HEADER)
            append(row)
            append('\n')
        }
        runCatching { storageManager.appendLog(config, text) }
    }

    fun readTail(config: SessionConfig, maxLines: Int = 200): String? {
        val lines = storageManager.readLogText(config)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()
        if (lines.isEmpty()) return null
        return lines.takeLast(maxLines).joinToString("\n")
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    companion object {
        private const val HEADER =
            "session_id,scheduled_time,actual_time,result,file_path,battery_percent,free_space_bytes,error_type,error_message\n"
    }
}
