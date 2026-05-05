package com.example.longintervalcamera.util

import android.os.StatFs
import java.io.File

object StorageUtils {
    fun freeSpaceBytes(directory: File): Long {
        val target = if (directory.exists()) directory else directory.parentFile ?: directory
        return runCatching { StatFs(target.absolutePath).availableBytes }.getOrDefault(target.freeSpace)
    }
}
