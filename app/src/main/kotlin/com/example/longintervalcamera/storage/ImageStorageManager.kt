package com.example.longintervalcamera.storage

import android.content.Context
import android.os.Environment
import com.example.longintervalcamera.data.SessionConfig
import com.example.longintervalcamera.util.TimeUtils
import java.io.File

class ImageStorageManager(private val context: Context) {
    fun baseDirectory(): File {
        val externalPictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val root = externalPictures ?: File(context.filesDir, "Pictures")
        return File(root, APP_FOLDER)
    }

    fun sessionDirectory(config: SessionConfig): File {
        return File(baseDirectory(), config.sessionId).also { it.mkdirs() }
    }

    fun imageFile(config: SessionConfig, captureTimeMillis: Long): File {
        return File(sessionDirectory(config), TimeUtils.fileNameFor(captureTimeMillis))
    }

    fun logFile(config: SessionConfig): File {
        return File(sessionDirectory(config), "capture_log.csv")
    }

    companion object {
        const val APP_FOLDER = "LongIntervalCamera"
    }
}
