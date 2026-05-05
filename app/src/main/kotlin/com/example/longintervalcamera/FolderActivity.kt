package com.example.longintervalcamera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.app.AlertDialog
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class FolderActivity : android.app.Activity() {
    private lateinit var directory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        directory = File(intent.getStringExtra(EXTRA_DIRECTORY).orEmpty())
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        root.addView(TextView(this).apply {
            text = "保存フォルダ"
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = directory.absolutePath
            textSize = 12f
            setPadding(0, dp(6), 0, dp(8))
        })

        root.addView(compactButton("パスをコピー") {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("LongIntervalCamera folder", directory.absolutePath))
            Toast.makeText(this@FolderActivity, "パスをコピーしました。", Toast.LENGTH_SHORT).show()
        })

        root.addView(compactButton("更新") {
            setContentView(buildContent())
        })

        val files = directory.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()

        root.addView(TextView(this).apply {
            text = "${files.size} ファイル"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        })

        if (!directory.exists()) {
            root.addView(message("フォルダはまだ作成されていません。"))
        } else if (files.isEmpty()) {
            root.addView(message("ファイルはまだありません。"))
        } else {
            files.forEach { file ->
                root.addView(fileRow(file))
            }
        }

        return ScrollView(this).apply {
            clipToPadding = true
            addView(root)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, systemBars.top, 0, systemBars.bottom)
                insets
            }
        }
    }

    private fun compactButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 15f
            includeFontPadding = false
            minHeight = dp(40)
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
            ).apply {
                topMargin = dp(4)
            }
            setAllCaps(false)
            setOnClickListener { onClick() }
        }
    }

    private fun fileRow(file: File): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { openFile(file) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(this).apply {
            text = "${file.name}\n${formatBytes(file.length())}"
            textSize = 13f
            setPadding(0, 0, 0, dp(4))
        })

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(fileActionButton("開く") { openFile(file) })
            addView(fileActionButton("共有") { shareFile(file) })
            addView(fileActionButton("削除") { confirmDelete(file) })
        })

        return root
    }

    private fun message(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, dp(12), 0, dp(12))
        }
    }

    private fun openFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val directIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType(file))
            if (file.extension.lowercase() in IMAGE_EXTENSIONS) {
                setPackage(GOOGLE_PHOTOS_PACKAGE)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (file.extension.lowercase() in IMAGE_EXTENSIONS) {
            grantUriPermission(GOOGLE_PHOTOS_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(directIntent)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(fallback)
            }.onFailure {
                Toast.makeText(this, "このファイルを開けるアプリが見つかりません。", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, file.name))
        }.onFailure {
            Toast.makeText(this, "共有できるアプリが見つかりません。", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("ファイルを削除しますか？")
            .setMessage(file.name)
            .setPositiveButton("削除") { _, _ ->
                if (file.delete()) {
                    Toast.makeText(this, "削除しました。", Toast.LENGTH_SHORT).show()
                    setContentView(buildContent())
                } else {
                    Toast.makeText(this, "削除できませんでした。", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun fileActionButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            includeFontPadding = false
            minHeight = dp(34)
            minimumHeight = 0
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                setMargins(0, 0, dp(6), 0)
            }
            setAllCaps(false)
            setOnClickListener { onClick() }
        }
    }

    private fun mimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "csv" -> "text/csv"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "%.2f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_DIRECTORY = "directory"
        private const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg")
    }
}
