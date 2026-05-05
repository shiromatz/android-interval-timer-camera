package com.example.longintervalcamera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.longintervalcamera.MainActivity
import com.example.longintervalcamera.R
import com.example.longintervalcamera.camera.CameraCaptureOutcome
import com.example.longintervalcamera.camera.CameraCaptureManager
import com.example.longintervalcamera.data.CaptureResult
import com.example.longintervalcamera.data.SessionConfig
import com.example.longintervalcamera.data.SessionRepository
import com.example.longintervalcamera.data.SessionStatus
import com.example.longintervalcamera.logging.CaptureLogger
import com.example.longintervalcamera.scheduler.CaptureAlarmReceiver
import com.example.longintervalcamera.scheduler.CaptureAlarmScheduler
import com.example.longintervalcamera.storage.ImageStorageManager
import com.example.longintervalcamera.util.BatteryUtils
import com.example.longintervalcamera.util.SessionMath
import com.example.longintervalcamera.util.StorageUtils
import com.example.longintervalcamera.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class CaptureForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: SessionRepository
    private lateinit var scheduler: CaptureAlarmScheduler
    private lateinit var storageManager: ImageStorageManager
    private lateinit var logger: CaptureLogger
    private lateinit var cameraCaptureManager: CameraCaptureManager
    private var captureJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = SessionRepository(this)
        scheduler = CaptureAlarmScheduler(this)
        storageManager = ImageStorageManager(this)
        logger = CaptureLogger(storageManager)
        cameraCaptureManager = CameraCaptureManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        startForegroundSafely(repository.getSession())

        when (action) {
            ACTION_START -> handleStart()
            ACTION_CAPTURE -> {
                val scheduled = intent?.getLongExtra(
                    CaptureAlarmReceiver.EXTRA_SCHEDULED_TIME,
                    repository.getSession()?.nextCaptureTimeMillis ?: TimeUtils.nowMillis()
                ) ?: TimeUtils.nowMillis()
                launchCapture(scheduled)
            }
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> handleStopByUser()
            else -> handleStart()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cameraCaptureManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart() {
        val config = repository.getSession() ?: run {
            stopSelf()
            return
        }
        if (!config.status.isUnfinished) {
            updateNotification(config)
            return
        }

        val next = config.nextCaptureTimeMillis ?: config.startTimeMillis
        val now = TimeUtils.nowMillis()
        if (next <= now) {
            launchCapture(next)
        } else {
            scheduler.schedule(next)
            repository.saveSession(config.copy(status = SessionStatus.WAITING, nextCaptureTimeMillis = next))
            updateNotification(repository.getSession())
        }
    }

    private fun handleResume() {
        val config = repository.getSession() ?: return
        val now = TimeUtils.nowMillis()
        if (now > config.endTimeMillis) {
            completeSession(config, now)
            return
        }
        val base = config.nextCaptureTimeMillis ?: config.startTimeMillis
        val next = SessionMath.firstSlotAfter(base, config.intervalMillis, now)
        if (next > config.endTimeMillis) {
            completeSession(config, now)
            return
        }
        val updated = config.copy(
            status = SessionStatus.WAITING,
            nextCaptureTimeMillis = next,
            lastResult = "RESUMED"
        )
        repository.saveSession(updated)
        scheduler.schedule(next)
        updateNotification(updated)
    }

    private fun handlePause() {
        val config = repository.getSession() ?: return
        scheduler.cancel()
        logger.append(
            config = config,
            scheduledTimeMillis = config.nextCaptureTimeMillis,
            actualTimeMillis = TimeUtils.nowMillis(),
            result = CaptureResult.SESSION_PAUSED,
            file = null,
            batteryPercent = BatteryUtils.batteryPercent(this),
            freeSpaceBytes = StorageUtils.freeSpaceBytes(storageManager.sessionDirectory(config))
        )
        val updated = config.copy(status = SessionStatus.PAUSED, lastResult = CaptureResult.SESSION_PAUSED.name)
        repository.saveSession(updated)
        updateNotification(updated)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun handleStopByUser() {
        val config = repository.getSession() ?: return
        scheduler.cancel()
        logger.append(
            config = config,
            scheduledTimeMillis = config.nextCaptureTimeMillis,
            actualTimeMillis = TimeUtils.nowMillis(),
            result = CaptureResult.SESSION_STOPPED_BY_USER,
            file = null,
            batteryPercent = BatteryUtils.batteryPercent(this),
            freeSpaceBytes = StorageUtils.freeSpaceBytes(storageManager.sessionDirectory(config))
        )
        val updated = config.copy(
            status = SessionStatus.STOPPED,
            nextCaptureTimeMillis = null,
            lastResult = CaptureResult.SESSION_STOPPED_BY_USER.name
        )
        repository.saveSession(updated)
        updateNotification(updated)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun launchCapture(scheduledTimeMillis: Long) {
        if (captureJob?.isActive == true) return
        captureJob = serviceScope.launch {
            performCapture(scheduledTimeMillis)
        }
    }

    private suspend fun performCapture(scheduledTimeMillis: Long) {
        val wakeLock = acquireCaptureWakeLock()
        try {
            val config = repository.getSession() ?: return
            if (config.status == SessionStatus.PAUSED || config.status == SessionStatus.STOPPED) return

            val now = TimeUtils.nowMillis()
            if (scheduledTimeMillis > config.endTimeMillis || now > config.endTimeMillis + config.intervalMillis) {
                completeSession(config, now)
                return
            }

            val runningConfig = config.copy(status = SessionStatus.RUNNING, nextCaptureTimeMillis = scheduledTimeMillis)
            repository.saveSession(runningConfig)
            updateNotification(runningConfig)

            val sessionDirectory = storageManager.sessionDirectory(runningConfig)
            val batteryPercent = BatteryUtils.batteryPercent(this)
            val freeSpaceBytes = StorageUtils.freeSpaceBytes(sessionDirectory)
            val actualTime = TimeUtils.nowMillis()

            if (batteryPercent < runningConfig.minBatteryPercent) {
                logger.append(
                    config = runningConfig,
                    scheduledTimeMillis = scheduledTimeMillis,
                    actualTimeMillis = actualTime,
                    result = CaptureResult.SKIPPED_LOW_BATTERY,
                    file = null,
                    batteryPercent = batteryPercent,
                    freeSpaceBytes = freeSpaceBytes
                )
                val afterSkip = runningConfig.copy(lastResult = CaptureResult.SKIPPED_LOW_BATTERY.name)
                scheduleAfterAttempt(afterSkip, scheduledTimeMillis, CaptureResult.SKIPPED_LOW_BATTERY)
                return
            }

            if (freeSpaceBytes < runningConfig.minFreeSpaceBytes) {
                logger.append(
                    config = runningConfig,
                    scheduledTimeMillis = scheduledTimeMillis,
                    actualTimeMillis = actualTime,
                    result = CaptureResult.SKIPPED_LOW_STORAGE,
                    file = null,
                    batteryPercent = batteryPercent,
                    freeSpaceBytes = freeSpaceBytes
                )
                val updated = runningConfig.copy(
                    status = SessionStatus.ERROR,
                    nextCaptureTimeMillis = null,
                    lastResult = CaptureResult.SKIPPED_LOW_STORAGE.name
                )
                repository.saveSession(updated)
                scheduler.cancel()
                updateNotification(updated)
                stopSelf()
                return
            }

            val targetFile = storageManager.imageFile(runningConfig, actualTime)
            val outcome = withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) {
                cameraCaptureManager.capture(targetFile, runningConfig.jpegQuality)
            } ?: CameraCaptureOutcome(
                result = CaptureResult.FAILED_CAPTURE,
                file = targetFile,
                errorType = "CaptureTimeout",
                errorMessage = "Capture did not finish within ${CAPTURE_TIMEOUT_MILLIS / 1000} seconds"
            )
            val freeSpaceAfterCapture = StorageUtils.freeSpaceBytes(sessionDirectory)
            logger.append(
                config = runningConfig,
                scheduledTimeMillis = scheduledTimeMillis,
                actualTimeMillis = actualTime,
                result = outcome.result,
                file = if (outcome.result == CaptureResult.SUCCESS) outcome.file else null,
                batteryPercent = batteryPercent,
                freeSpaceBytes = freeSpaceAfterCapture,
                errorType = outcome.errorType,
                errorMessage = outcome.errorMessage
            )

            val cameraFailures = if (outcome.result == CaptureResult.FAILED_CAMERA_INIT) {
                runningConfig.consecutiveCameraFailures + 1
            } else {
                0
            }

            val capturedCount = if (outcome.result == CaptureResult.SUCCESS) {
                runningConfig.capturedCount + 1
            } else {
                runningConfig.capturedCount
            }

            val shouldStopForSaveFailure =
                outcome.result == CaptureResult.FAILED_SAVE && freeSpaceAfterCapture < runningConfig.minFreeSpaceBytes
            val shouldStopForCameraFailure = cameraFailures >= MAX_CAMERA_FAILURES

            val updated = runningConfig.copy(
                capturedCount = capturedCount,
                lastCaptureTimeMillis = actualTime,
                lastResult = outcome.result.name,
                consecutiveCameraFailures = cameraFailures
            )

            if (shouldStopForSaveFailure || shouldStopForCameraFailure) {
                val stopped = updated.copy(
                    status = SessionStatus.ERROR,
                    nextCaptureTimeMillis = null
                )
                repository.saveSession(stopped)
                scheduler.cancel()
                updateNotification(stopped)
                stopSelf()
                return
            }

            scheduleAfterAttempt(updated, scheduledTimeMillis, outcome.result)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun scheduleAfterAttempt(
        config: SessionConfig,
        previousScheduledTimeMillis: Long,
        result: CaptureResult
    ) {
        val now = TimeUtils.nowMillis()
        val next = SessionMath.nextScheduledAfter(previousScheduledTimeMillis, config.intervalMillis, now)
        if (next > config.endTimeMillis) {
            completeSession(config.copy(lastResult = result.name), now)
            return
        }

        val updated = config.copy(
            status = SessionStatus.WAITING,
            nextCaptureTimeMillis = next,
            lastResult = result.name
        )
        repository.saveSession(updated)
        scheduler.schedule(next)
        updateNotification(updated)
    }

    private fun completeSession(config: SessionConfig, actualTimeMillis: Long) {
        logger.append(
            config = config,
            scheduledTimeMillis = config.nextCaptureTimeMillis,
            actualTimeMillis = actualTimeMillis,
            result = CaptureResult.SESSION_COMPLETED,
            file = null,
            batteryPercent = BatteryUtils.batteryPercent(this),
            freeSpaceBytes = StorageUtils.freeSpaceBytes(storageManager.sessionDirectory(config))
        )
        val updated = config.copy(
            status = SessionStatus.COMPLETED,
            nextCaptureTimeMillis = null,
            lastResult = CaptureResult.SESSION_COMPLETED.name
        )
        repository.saveSession(updated)
        scheduler.cancel()
        updateNotification(updated)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LongIntervalCamera",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "LongIntervalCamera capture session status"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun startForegroundSafely(config: SessionConfig?) {
        val notification = buildNotification(config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(config: SessionConfig?) {
        notificationManager().notify(NOTIFICATION_ID, buildNotification(config))
    }

    private fun buildNotification(config: SessionConfig?): Notification {
        val status = config?.status ?: SessionStatus.NOT_CONFIGURED
        val text = if (config == null) {
            "セッション未設定"
        } else {
            "次回撮影: ${TimeUtils.formatDisplay(config.nextCaptureTimeMillis)} / 撮影済み: ${config.capturedCount}枚"
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_camera)
            .setContentTitle("LongIntervalCamera ${statusLabel(status)}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(status == SessionStatus.WAITING || status == SessionStatus.RUNNING)
            .setContentIntent(activityPendingIntent())
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_pause,
                    "一時停止",
                    servicePendingIntent(ACTION_PAUSE, REQUEST_PAUSE)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_stop,
                    "停止",
                    servicePendingIntent(ACTION_STOP, REQUEST_STOP)
                ).build()
            )
            .build()
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

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, CaptureForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun acquireCaptureWakeLock(): PowerManager.WakeLock {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:capture").apply {
            acquire(CAPTURE_WAKE_LOCK_TIMEOUT_MILLIS)
        }
    }

    companion object {
        const val ACTION_START = "com.example.longintervalcamera.action.START"
        const val ACTION_CAPTURE = "com.example.longintervalcamera.action.CAPTURE"
        const val ACTION_PAUSE = "com.example.longintervalcamera.action.PAUSE"
        const val ACTION_RESUME = "com.example.longintervalcamera.action.RESUME"
        const val ACTION_STOP = "com.example.longintervalcamera.action.STOP"

        private const val CHANNEL_ID = "capture_session"
        private const val NOTIFICATION_ID = 40_001
        private const val REQUEST_OPEN_APP = 40_010
        private const val REQUEST_PAUSE = 40_011
        private const val REQUEST_STOP = 40_012
        private const val MAX_CAMERA_FAILURES = 3
        private const val CAPTURE_WAKE_LOCK_TIMEOUT_MILLIS = 2L * 60L * 1000L
        private const val CAPTURE_TIMEOUT_MILLIS = 45L * 1000L

        fun start(context: Context) {
            context.startServiceIntent(ACTION_START)
        }

        fun resume(context: Context) {
            context.startServiceIntent(ACTION_RESUME)
        }

        fun stop(context: Context) {
            context.startServiceIntent(ACTION_STOP)
        }

        fun pause(context: Context) {
            context.startServiceIntent(ACTION_PAUSE)
        }

        private fun Context.startServiceIntent(action: String) {
            val intent = Intent(this, CaptureForegroundService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
