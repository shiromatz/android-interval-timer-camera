package com.example.longintervalcamera.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.longintervalcamera.service.CaptureForegroundService

class CaptureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CAPTURE_ALARM) return
        val scheduledTime = intent.getLongExtra(EXTRA_SCHEDULED_TIME, 0L)
        val serviceIntent = Intent(context, CaptureForegroundService::class.java).apply {
            action = CaptureForegroundService.ACTION_CAPTURE
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_CAPTURE_ALARM = "com.example.longintervalcamera.action.CAPTURE_ALARM"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    }
}
