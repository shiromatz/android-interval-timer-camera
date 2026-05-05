package com.example.longintervalcamera.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class CaptureAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(scheduledTimeMillis: Long) {
        val triggerAtMillis = scheduledTimeMillis.coerceAtLeast(System.currentTimeMillis() + MIN_DELAY_MILLIS)
        val pendingIntent = capturePendingIntent(context, scheduledTimeMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel() {
        alarmManager.cancel(capturePendingIntent(context, 0L))
    }

    companion object {
        private const val REQUEST_CAPTURE = 10_001
        private const val MIN_DELAY_MILLIS = 1_000L

        fun capturePendingIntent(context: Context, scheduledTimeMillis: Long): PendingIntent {
            val intent = Intent(context, CaptureAlarmReceiver::class.java).apply {
                action = CaptureAlarmReceiver.ACTION_CAPTURE_ALARM
                putExtra(CaptureAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTimeMillis)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, REQUEST_CAPTURE, intent, flags)
        }
    }
}
