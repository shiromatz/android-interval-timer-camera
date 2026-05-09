package com.example.longintervalcamera.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.longintervalcamera.data.CaptureResult
import com.example.longintervalcamera.data.SessionRepository
import com.example.longintervalcamera.data.SessionStatus

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = SessionRepository(context)
        repository.updateSession { config ->
            if (config.status.isUnfinished) {
                config.copy(
                    status = SessionStatus.PAUSED,
                    runningStartedTimeMillis = null,
                    lastResult = CaptureResult.DEVICE_REBOOTED_OPEN_APP_TO_RESUME.name
                )
            } else {
                config
            }
        }
    }
}
