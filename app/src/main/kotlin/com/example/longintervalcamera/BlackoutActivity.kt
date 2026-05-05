package com.example.longintervalcamera

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.example.longintervalcamera.data.SessionRepository
import com.example.longintervalcamera.data.SessionStatus
import com.example.longintervalcamera.util.TimeUtils

class BlackoutActivity : android.app.Activity() {
    private lateinit var repository: SessionRepository
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var touchStartMillis = 0L
    private var statusVisibleUntilMillis = 0L

    private val hideStatusRunnable = Runnable {
        statusVisibleUntilMillis = 0L
        refresh()
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SessionRepository(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(DIM_TEXT_COLOR)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(
                statusText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            setOnTouchListener { _, event -> handleTouch(event) }
            systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        setContentView(root)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(hideStatusRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    private fun refresh() {
        val session = repository.getSession()
        if (session?.status?.isTerminal == true) {
            returnToMain()
            return
        }

        val isVisible = System.currentTimeMillis() < statusVisibleUntilMillis
        statusText.setTextColor(if (isVisible) VISIBLE_TEXT_COLOR else DIM_TEXT_COLOR)
        statusText.textSize = if (isVisible) 18f else 11f
        statusText.text = if (session == null) {
            if (isVisible) "未設定" else ""
        } else if (isVisible) {
            "状態: ${statusLabel(session.status)}\n" +
                "次回: ${TimeUtils.formatDisplay(session.nextCaptureTimeMillis)}\n" +
                "撮影済み: ${session.capturedCount}枚\n" +
                "最後: ${TimeUtils.formatDisplay(session.lastCaptureTimeMillis)}\n" +
                "結果: ${session.lastResult ?: "-"}\n\n" +
                "長押しで黒画面を閉じる"
        } else {
            "撮影中"
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> touchStartMillis = System.currentTimeMillis()
            MotionEvent.ACTION_UP -> {
                val heldMillis = System.currentTimeMillis() - touchStartMillis
                if (heldMillis >= EXIT_HOLD_MILLIS) {
                    confirmExit()
                } else {
                    revealStatus()
                }
            }
            MotionEvent.ACTION_CANCEL -> touchStartMillis = 0L
        }
        return true
    }

    private fun revealStatus() {
        statusVisibleUntilMillis = System.currentTimeMillis() + STATUS_REVEAL_MILLIS
        handler.removeCallbacks(hideStatusRunnable)
        refresh()
        handler.postDelayed(hideStatusRunnable, STATUS_REVEAL_MILLIS)
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("黒画面を閉じますか？")
            .setMessage("撮影セッションは継続します。")
            .setPositiveButton("閉じる") { _, _ -> finish() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun returnToMain() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    private fun statusLabel(status: SessionStatus): String {
        return when (status) {
            SessionStatus.NOT_CONFIGURED -> "未設定"
            SessionStatus.WAITING -> "待機中"
            SessionStatus.RUNNING -> "撮影中"
            SessionStatus.PAUSED -> "一時停止中"
            SessionStatus.COMPLETED -> "完了"
            SessionStatus.ERROR -> "エラー停止"
            SessionStatus.STOPPED -> "停止"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXIT_HOLD_MILLIS = 1_500L
        private const val STATUS_REVEAL_MILLIS = 8_000L
        private const val DIM_TEXT_COLOR = 0xFF111111.toInt()
        private const val VISIBLE_TEXT_COLOR = 0xFFE5E7EB.toInt()
    }
}

private val SessionStatus.isTerminal: Boolean
    get() = this == SessionStatus.COMPLETED || this == SessionStatus.STOPPED || this == SessionStatus.ERROR
