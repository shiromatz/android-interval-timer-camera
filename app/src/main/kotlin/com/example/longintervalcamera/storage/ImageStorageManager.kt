package com.example.longintervalcamera.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.longintervalcamera.data.SessionConfig
import com.example.longintervalcamera.util.TimeUtils
import java.io.File
import java.io.IOException

class ImageStorageManager(private val context: Context) {
    fun baseDirectory(): File {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File(root, APP_FOLDER)
    }

    fun sessionDirectory(config: SessionConfig): File {
        return File(baseDirectory(), config.sessionId)
    }

    fun imageFile(config: SessionConfig, captureTimeMillis: Long): File {
        return File(sessionDirectory(config), TimeUtils.fileNameFor(captureTimeMillis))
    }

    fun imageTarget(config: SessionConfig, captureTimeMillis: Long): ImageCaptureTarget {
        val fileName = TimeUtils.fileNameFor(captureTimeMillis)
        val file = File(sessionDirectory(config), fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ImageCaptureTarget.MediaStoreTarget(
                file = file,
                collectionUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues = imageContentValues(config, fileName, captureTimeMillis)
            )
        } else {
            ImageCaptureTarget.FileTarget(file)
        }
    }

    fun logFile(config: SessionConfig): File {
        return File(sessionDirectory(config), LOG_FILE_NAME)
    }

    fun logExists(config: SessionConfig): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreFileUri(sessionDirectory(config), LOG_FILE_NAME) != null || fallbackLogFile(config).exists()
        } else {
            logFile(config).exists()
        }
    }

    fun appendLog(config: SessionConfig, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findMediaStoreFileUri(sessionDirectory(config), LOG_FILE_NAME)
                ?: runCatching { createLogUri(config) }.getOrNull()
            if (uri != null && runCatching { appendText(uri, text) }.isSuccess) {
                return
            }
            appendFallbackLog(config, text)
            return
        }

        val file = logFile(config)
        file.parentFile?.mkdirs()
        file.appendText(text)
    }

    fun readLogText(config: SessionConfig): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = findMediaStoreFileUri(sessionDirectory(config), LOG_FILE_NAME)
            val publicLog = uri?.let {
                runCatching {
                    context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                }.getOrNull()
            }
            if (!publicLog.isNullOrBlank()) return publicLog

            return fallbackLogFile(config).takeIf { it.exists() }?.readText()
        }

        return logFile(config).takeIf { it.exists() }?.readText()
    }

    fun latestImageFile(directory: File): StoredFile? {
        return listFiles(directory)
            .filter { it.file.extension.lowercase() in IMAGE_EXTENSIONS }
            .maxByOrNull { it.name }
    }

    fun listFiles(directory: File): List<StoredFile> {
        val filesByName = linkedMapOf<String, StoredFile>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStoreFiles(directory).forEach { filesByName[it.name] = it }
        }

        directory.listFiles()
            ?.filter { it.isFile }
            ?.forEach { file ->
                filesByName.putIfAbsent(
                    file.name,
                    StoredFile(
                        name = file.name,
                        file = file,
                        uri = null,
                        sizeBytes = file.length(),
                        mimeType = null
                    )
                )
            }

        return filesByName.values.sortedBy { it.name }
    }

    private fun imageContentValues(
        config: SessionConfig,
        fileName: String,
        captureTimeMillis: Long
    ): ContentValues {
        return ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, IMAGE_MIME_TYPE)
            put(MediaStore.Images.Media.DATE_TAKEN, captureTimeMillis)
            put(MediaStore.Images.Media.DATE_ADDED, captureTimeMillis / 1000L)
            put(MediaStore.Images.Media.DATE_MODIFIED, captureTimeMillis / 1000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativeSessionPath(config))
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    }

    private fun createLogUri(config: SessionConfig): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, LOG_MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSessionPath(config))
            }
        }
        return context.contentResolver.insert(filesCollectionUri(), values)
            ?: throw IOException("Failed to create $LOG_FILE_NAME")
    }

    private fun appendText(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wa")
            ?: throw IOException("Failed to open log stream")
        stream.bufferedWriter().use { writer -> writer.write(text) }
    }

    private fun appendFallbackLog(config: SessionConfig, text: String) {
        val file = fallbackLogFile(config)
        file.parentFile?.mkdirs()
        file.appendText(text)
    }

    private fun fallbackLogFile(config: SessionConfig): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOCUMENTS)
        return File(File(root, APP_FOLDER), "${config.sessionId}/$LOG_FILE_NAME")
    }

    private fun queryMediaStoreFiles(directory: File): List<StoredFile> {
        val relativePath = relativePathFor(directory) ?: return emptyList()
        val collectionUri = filesCollectionUri()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val files = mutableListOf<StoredFile>()

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            arrayOf(relativePath),
            "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val id = cursor.getLong(idColumn)
                files.add(
                    StoredFile(
                        name = name,
                        file = File(directory, name),
                        uri = ContentUris.withAppendedId(collectionUri, id),
                        sizeBytes = cursor.getLong(sizeColumn),
                        mimeType = cursor.getString(mimeTypeColumn)
                    )
                )
            }
        }

        return files
    }

    private fun findMediaStoreFileUri(directory: File, fileName: String): Uri? {
        val relativePath = relativePathFor(directory) ?: return null
        val collectionUri = filesCollectionUri()
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            arrayOf(relativePath, fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    collectionUri,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                )
            }
        }
        return null
    }

    private fun relativeSessionPath(config: SessionConfig): String {
        return "${Environment.DIRECTORY_PICTURES}/$APP_FOLDER/${config.sessionId}/"
    }

    private fun relativePathFor(directory: File): String? {
        val picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .absolutePath
            .replace('\\', '/')
            .trimEnd('/')
        val path = directory.absolutePath.replace('\\', '/').trimEnd('/')
        if (path == picturesRoot) return "${Environment.DIRECTORY_PICTURES}/"
        if (!path.startsWith("$picturesRoot/")) return null

        val child = path.removePrefix("$picturesRoot/").trim('/')
        return "${Environment.DIRECTORY_PICTURES}/$child/"
    }

    private fun filesCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }

    companion object {
        const val APP_FOLDER = "LongIntervalCamera"
        private const val LOG_FILE_NAME = "capture_log.csv"
        private const val LOG_MIME_TYPE = "text/csv"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg")
    }
}

sealed class ImageCaptureTarget(open val file: File) {
    data class FileTarget(override val file: File) : ImageCaptureTarget(file)
    data class MediaStoreTarget(
        override val file: File,
        val collectionUri: Uri,
        val contentValues: ContentValues
    ) : ImageCaptureTarget(file)
}

data class StoredFile(
    val name: String,
    val file: File,
    val uri: Uri?,
    val sizeBytes: Long,
    val mimeType: String?
)
