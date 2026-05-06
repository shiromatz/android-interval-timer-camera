package com.example.longintervalcamera.util

import android.os.StatFs
import java.io.File

object StorageUtils {
    fun freeSpaceBytes(directory: File): Long {
        val target = existingAncestor(directory)
        return runCatching { StatFs(target.absolutePath).availableBytes }.getOrDefault(target.freeSpace)
    }

    private fun existingAncestor(directory: File): File {
        var current: File? = directory
        while (current != null && !current.exists()) {
            current = current.parentFile
        }
        return current ?: directory
    }
}
