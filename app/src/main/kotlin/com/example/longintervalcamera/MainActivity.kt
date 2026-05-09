package com.example.longintervalcamera

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.longintervalcamera.data.CaptureResult
import com.example.longintervalcamera.data.SessionConfig
import com.example.longintervalcamera.data.SessionRepository
import com.example.longintervalcamera.data.SessionStatus
import com.example.longintervalcamera.logging.CaptureLogger
import com.example.longintervalcamera.scheduler.CaptureAlarmScheduler
import com.example.longintervalcamera.service.CaptureForegroundService
import com.example.longintervalcamera.storage.ImageStorageManager
import com.example.longintervalcamera.storage.StoredFile
import com.example.longintervalcamera.util.BatteryUtils
import com.example.longintervalcamera.util.SessionMath
import com.example.longintervalcamera.util.StorageUtils
import com.example.longintervalcamera.util.TimeUtils
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : android.app.Activity() {
    private lateinit var repository: SessionRepository
    private lateinit var storageManager: ImageStorageManager
    private lateinit var logger: CaptureLogger
    private lateinit var statusText: TextView
    private lateinit var nextCaptureText: TextView
    private lateinit var countText: TextView
    private lateinit var lastCaptureText: TextView
    private lateinit var lastResultText: TextView
    private lateinit var pathText: TextView
    private lateinit var batteryText: TextView
    private lateinit var storageText: TextView
    private lateinit var startTimeEdit: EditText
    private lateinit var endTimeEdit: EditText
    private lateinit var intervalEdit: EditText
    private lateinit var intervalUnitSpinner: Spinner
    private lateinit var folderEdit: EditText
    private lateinit var jpegQualityEdit: EditText
    private lateinit var minBatteryEdit: EditText
    private lateinit var minFreeSpaceEdit: EditText
    private lateinit var blackoutCheck: CheckBox
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var stopButton: Button
    private lateinit var blackoutButton: Button
    private lateinit var logButton: Button
    private lateinit var latestImageButton: Button
    private lateinit var openFolderButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStartAfterPermission = false
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SessionRepository(this)
        storageManager = ImageStorageManager(this)
        logger = CaptureLogger(storageManager)
        setContentView(buildContent())
        initializeDefaults()
        maybeShowBatteryGuidance()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            if (hasRequiredPermissions()) {
                validateAndConfirmStart()
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 13f
            setPadding(0, dp(1), 0, dp(8))
        })

        root.addView(sectionTitle(getString(R.string.section_settings)))
        val dateRow = horizontalRow()
        startTimeEdit = labeledEditColumn(dateRow, getString(R.string.label_start_datetime), inputType = InputType.TYPE_NULL, marginEnd = true)
        endTimeEdit = labeledEditColumn(dateRow, getString(R.string.label_end_datetime), inputType = InputType.TYPE_NULL)
        root.addView(dateRow)
        startTimeEdit.setOnClickListener { pickDateTime(startTimeEdit) }
        endTimeEdit.setOnClickListener { pickDateTime(endTimeEdit) }

        val intervalQualityRow = horizontalRow()
        val intervalColumn = fieldColumn(marginEnd = true)
        val intervalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        intervalEdit = EditText(this).apply {
            hint = getString(R.string.hint_interval)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            applyCompactEditStyle()
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(40))
        }
        intervalUnitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                resources.getStringArray(R.array.interval_units).toList()
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            minimumHeight = 0
            dropDownWidth = dp(148)
            layoutParams = LinearLayout.LayoutParams(dp(126), dp(40)).apply {
                setMargins(dp(6), 0, 0, 0)
            }
        }
        intervalRow.addView(intervalEdit)
        intervalRow.addView(intervalUnitSpinner)
        intervalColumn.addView(label(getString(R.string.label_capture_interval)))
        intervalColumn.addView(intervalRow)
        intervalQualityRow.addView(intervalColumn)
        jpegQualityEdit = labeledEditColumn(intervalQualityRow, getString(R.string.label_jpeg_quality), InputType.TYPE_CLASS_NUMBER)
        root.addView(intervalQualityRow)

        folderEdit = labeledEdit(root, getString(R.string.label_save_folder_name))
        val thresholdRow = horizontalRow()
        minBatteryEdit = labeledEditColumn(thresholdRow, getString(R.string.label_min_battery_percent), InputType.TYPE_CLASS_NUMBER, marginEnd = true)
        minFreeSpaceEdit = labeledEditColumn(
            thresholdRow,
            getString(R.string.label_min_free_space_gb),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        root.addView(thresholdRow)
        blackoutCheck = CheckBox(this).apply {
            text = getString(R.string.label_blackout_mode)
            textSize = 15f
            minHeight = dp(36)
            minimumHeight = 0
            includeFontPadding = false
        }
        root.addView(blackoutCheck)

        root.addView(sectionTitle(getString(R.string.section_status)))
        statusRow(root).also { row ->
            statusText = statusCell(row, getString(R.string.label_current_status), marginEnd = true)
            nextCaptureText = statusCell(row, getString(R.string.label_next_capture))
        }
        statusRow(root).also { row ->
            countText = statusCell(row, getString(R.string.label_captured_count), marginEnd = true)
            lastCaptureText = statusCell(row, getString(R.string.label_last_capture))
        }
        statusRow(root).also { row ->
            lastResultText = statusCell(row, getString(R.string.label_last_result), marginEnd = true)
            batteryText = statusCell(row, getString(R.string.label_battery))
        }
        statusRow(root).also { row ->
            storageText = statusCell(row, getString(R.string.label_free_space), marginEnd = true)
            pathText = statusCell(row, getString(R.string.label_save_location), singleLine = true)
        }

        root.addView(sectionTitle(getString(R.string.section_actions)))
        startButton = button(getString(R.string.button_start_capture)) { startSessionClicked() }
        pauseButton = button(getString(R.string.button_pause)) { CaptureForegroundService.pause(this); refreshStatus() }
        resumeButton = button(getString(R.string.button_resume)) { resumeSessionClicked() }
        stopButton = button(getString(R.string.button_stop)) { confirmStop() }
        blackoutButton = button(getString(R.string.button_blackout_display)) { startActivity(Intent(this, BlackoutActivity::class.java)) }
        logButton = button(getString(R.string.button_show_log)) { showLogDialog() }
        latestImageButton = button(getString(R.string.button_open_latest_image)) { openLatestImage() }
        openFolderButton = button(getString(R.string.button_open_save_folder)) { openSaveFolder() }

        root.addView(buttonRow(startButton, blackoutButton))
        root.addView(buttonRow(pauseButton, resumeButton, stopButton))
        root.addView(buttonRow(logButton, latestImageButton))
        root.addView(buttonRow(openFolderButton))

        setupInputChangeListeners()

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

    private fun initializeDefaults() {
        val existing = repository.getSession()
        if (existing != null && existing.status.isUnfinished) {
            startTimeEdit.setText(TimeUtils.formatDisplay(existing.startTimeMillis))
            endTimeEdit.setText(TimeUtils.formatDisplay(existing.endTimeMillis))
            intervalEdit.setText(defaultIntervalValue(existing.intervalMillis).first)
            intervalUnitSpinner.setSelection(defaultIntervalValue(existing.intervalMillis).second)
            folderEdit.setText(existing.sessionId)
            jpegQualityEdit.setText(existing.jpegQuality.toString())
            minBatteryEdit.setText(existing.minBatteryPercent.toString())
            minFreeSpaceEdit.setText(bytesToGbText(existing.minFreeSpaceBytes))
            blackoutCheck.isChecked = existing.blackoutModeEnabled
            return
        }

        val start = TimeUtils.defaultStartMillis()
        val end = TimeUtils.defaultEndMillis(start)
        startTimeEdit.setText(TimeUtils.formatDisplay(start))
        endTimeEdit.setText(TimeUtils.formatDisplay(end))
        intervalEdit.setText("1")
        intervalUnitSpinner.setSelection(0)
        folderEdit.setText(TimeUtils.sessionIdFor(start))
        jpegQualityEdit.setText("90")
        minBatteryEdit.setText("10")
        minFreeSpaceEdit.setText("1")
        blackoutCheck.isChecked = true
    }

    private fun startSessionClicked() {
        if (!hasRequiredPermissions()) {
            pendingStartAfterPermission = true
            requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS)
            return
        }
        validateAndConfirmStart()
    }

    private fun validateAndConfirmStart() {
        val config = buildSessionConfigFromForm() ?: return
        val estimate = SessionMath.estimateCaptureCount(
            config.startTimeMillis,
            config.endTimeMillis,
            config.intervalMillis
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_session_title))
            .setMessage(
                getString(
                    R.string.start_session_message,
                    TimeUtils.formatDisplay(config.startTimeMillis),
                    TimeUtils.formatDisplay(config.endTimeMillis),
                    formatInterval(config.intervalMillis),
                    estimate
                )
            )
            .setPositiveButton(getString(R.string.button_start)) { _, _ ->
                repository.saveSession(config)
                CaptureForegroundService.start(this)
                if (config.blackoutModeEnabled) {
                    startActivity(Intent(this, BlackoutActivity::class.java))
                }
                refreshStatus()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun resumeSessionClicked() {
        val session = repository.getSession()
        if (session == null || !session.status.isUnfinished) {
            Toast.makeText(this, getString(R.string.no_resumable_session), Toast.LENGTH_LONG).show()
            return
        }
        if (!hasRequiredPermissions()) {
            pendingStartAfterPermission = false
            requestPermissions(requiredPermissions(), REQUEST_PERMISSIONS)
            return
        }
        CaptureForegroundService.resume(this)
        if (session.blackoutModeEnabled) {
            startActivity(Intent(this, BlackoutActivity::class.java))
        }
        refreshStatus()
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.stop_session_title))
            .setMessage(getString(R.string.stop_session_message))
            .setPositiveButton(getString(R.string.button_stop)) { _, _ ->
                CaptureForegroundService.stop(this)
                refreshStatus()
            }
            .setNegativeButton(getString(R.string.button_cancel), null)
            .show()
    }

    private fun buildSessionConfigFromForm(): SessionConfig? {
        val start = TimeUtils.parseDisplay(startTimeEdit.text.toString())
        val end = TimeUtils.parseDisplay(endTimeEdit.text.toString())
        val intervalValue = intervalEdit.text.toString().toLongOrNull()
        val jpegQuality = jpegQualityEdit.text.toString().toIntOrNull()
        val minBattery = minBatteryEdit.text.toString().toIntOrNull()
        val minFreeSpaceGb = minFreeSpaceEdit.text.toString().toDoubleOrNull()

        if (start == null || end == null) {
            showValidationError(getString(R.string.validation_datetime_format))
            return null
        }
        if (end < start) {
            showValidationError(getString(R.string.validation_end_after_start))
            return null
        }
        if (end < System.currentTimeMillis()) {
            showValidationError(getString(R.string.validation_end_future))
            return null
        }
        if (intervalValue == null || intervalValue <= 0L) {
            showValidationError(getString(R.string.validation_interval_positive))
            return null
        }
        if (jpegQuality == null || jpegQuality !in 1..100) {
            showValidationError(getString(R.string.validation_jpeg_quality))
            return null
        }
        if (minBattery == null || minBattery !in 0..100) {
            showValidationError(getString(R.string.validation_battery))
            return null
        }
        if (minFreeSpaceGb == null || minFreeSpaceGb < 0.0) {
            showValidationError(getString(R.string.validation_free_space))
            return null
        }

        val intervalMillis = intervalValue * if (intervalUnitSpinner.selectedItemPosition == 0) {
            60L * 60L * 1000L
        } else {
            60L * 1000L
        }
        val sessionId = sanitizeFolderName(folderEdit.text.toString()).ifBlank { TimeUtils.sessionIdFor(start) }
        val saveDirectory = File(storageManager.baseDirectory(), sessionId).absolutePath

        return SessionConfig(
            sessionId = sessionId,
            startTimeMillis = start,
            endTimeMillis = end,
            intervalMillis = intervalMillis,
            saveDirectory = saveDirectory,
            jpegQuality = jpegQuality,
            minBatteryPercent = minBattery,
            minFreeSpaceBytes = (minFreeSpaceGb * 1_000_000_000L).toLong(),
            blackoutModeEnabled = blackoutCheck.isChecked,
            status = SessionStatus.WAITING,
            nextCaptureTimeMillis = start,
            capturedCount = 0,
            lastCaptureTimeMillis = null,
            runningStartedTimeMillis = null,
            lastResult = null
        )
    }

    private fun refreshStatus() {
        val config = recoverStaleRunningSession(repository.getSession())
        val status = config?.status ?: SessionStatus.NOT_CONFIGURED
        statusText.text = statusLineText(R.string.label_current_status, statusLabel(status))
        nextCaptureText.text = statusLineText(R.string.label_next_capture, TimeUtils.formatDisplay(config?.nextCaptureTimeMillis))
        countText.text = statusLineText(R.string.label_captured_count, (config?.capturedCount ?: 0).toString())
        lastCaptureText.text = statusLineText(R.string.label_last_capture, TimeUtils.formatDisplay(config?.lastCaptureTimeMillis))
        lastResultText.text = statusLineText(R.string.label_last_result, config?.lastResult ?: "-")
        pathText.text = statusLineText(R.string.label_save_location, config?.saveDirectory ?: "-")
        batteryText.text = statusLineText(R.string.label_battery, "${BatteryUtils.batteryPercent(this)}%")
        val base = config?.saveDirectory?.let { File(it) } ?: storageManager.baseDirectory()
        storageText.text = statusLineText(R.string.label_free_space, formatBytes(StorageUtils.freeSpaceBytes(base)))
        updateButtonStates(config)
    }

    private fun recoverStaleRunningSession(config: SessionConfig?): SessionConfig? {
        if (config?.status != SessionStatus.RUNNING) return config
        val scheduled = config.nextCaptureTimeMillis ?: return config
        val runningStarted = config.runningStartedTimeMillis ?: scheduled
        val now = TimeUtils.nowMillis()
        if (now - runningStarted <= STALE_RUNNING_TIMEOUT_MILLIS) return config

        val sessionDirectory = storageManager.sessionDirectory(config)
        val battery = BatteryUtils.batteryPercent(this)
        val freeSpace = StorageUtils.freeSpaceBytes(sessionDirectory)

        logger.append(
            config = config,
            scheduledTimeMillis = scheduled,
            actualTimeMillis = now,
            result = CaptureResult.FAILED_CAPTURE,
            file = null,
            batteryPercent = battery,
            freeSpaceBytes = freeSpace,
            errorType = "StaleRunningRecovery",
            errorMessage = "Recovered a capture that stayed RUNNING past its timeout"
        )

        val next = SessionMath.nextScheduledAfter(scheduled, config.intervalMillis, now)
        val recovered = if (now > config.endTimeMillis || next > config.endTimeMillis) {
            logger.append(
                config = config,
                scheduledTimeMillis = config.nextCaptureTimeMillis,
                actualTimeMillis = now,
                result = CaptureResult.SESSION_COMPLETED,
                file = null,
                batteryPercent = battery,
                freeSpaceBytes = freeSpace,
                errorType = "StaleRunningRecovery",
                errorMessage = "Session end time had passed during recovery"
            )
            config.copy(
                status = SessionStatus.COMPLETED,
                nextCaptureTimeMillis = null,
                runningStartedTimeMillis = null,
                lastResult = CaptureResult.SESSION_COMPLETED.name
            ).also {
                CaptureAlarmScheduler(this).cancel()
            }
        } else {
            val waiting = config.copy(
                status = SessionStatus.WAITING,
                nextCaptureTimeMillis = next,
                runningStartedTimeMillis = null,
                lastResult = CaptureResult.FAILED_CAPTURE.name
            )
            CaptureAlarmScheduler(this).schedule(next)
            waiting
        }

        repository.saveSession(recovered)
        return recovered
    }

    private fun showLogDialog() {
        val config = repository.getSession()
        val logText = config?.let { logger.readTail(it, LOG_DIALOG_MAX_ROWS + 1) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        if (logText == null) {
            container.addView(TextView(this).apply {
                text = getString(R.string.no_log)
                textSize = 15f
            })
        } else {
            val rows = logText.lines()
                .filter { it.isNotBlank() && !it.startsWith("session_id,") }
                .takeLast(LOG_DIALOG_MAX_ROWS)
                .mapNotNull { parseCsvLine(it) }

            if (rows.isEmpty()) {
                container.addView(TextView(this).apply {
                    text = getString(R.string.no_log)
                    textSize = 15f
                })
            } else {
                rows.forEachIndexed { index, columns ->
                    if (columns.size >= 9) {
                        container.addView(logRowView(index + 1, columns))
                    }
                }
            }
        }

        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("capture_log.csv")
            .setView(scroll)
            .setPositiveButton(getString(R.string.button_close), null)
            .show()
    }

    private fun openSaveFolder() {
        val directory = currentSaveDirectory()
        if (directory == null) {
            Toast.makeText(this, getString(R.string.no_save_location), Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, FolderActivity::class.java).putExtra(FolderActivity.EXTRA_DIRECTORY, directory.absolutePath))
    }

    private fun openLatestImage() {
        val storedFile = latestImageFile()
        if (storedFile == null) {
            Toast.makeText(this, getString(R.string.no_openable_image), Toast.LENGTH_LONG).show()
            return
        }
        openImageFile(storedFile)
    }

    private fun openImageFile(storedFile: StoredFile) {
        val uri: Uri = storedFile.uri
            ?: FileProvider.getUriForFile(this, "$packageName.fileprovider", storedFile.file)
        val photosIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/jpeg")
            setPackage(GOOGLE_PHOTOS_PACKAGE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantUriPermission(GOOGLE_PHOTOS_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(photosIntent)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                startActivity(fallback)
            }.onFailure {
                Toast.makeText(this, getString(R.string.no_image_app), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun currentSaveDirectory(): File? {
        val typedFolder = sanitizeFolderName(folderEdit.text.toString())
        if (typedFolder.isNotBlank()) {
            return File(storageManager.baseDirectory(), typedFolder)
        }

        val savedPath = repository.getSession()?.saveDirectory?.takeIf { it.isNotBlank() }
        if (savedPath != null) {
            return File(savedPath)
        }

        val start = TimeUtils.parseDisplay(startTimeEdit.text.toString()) ?: return null
        return File(storageManager.baseDirectory(), TimeUtils.sessionIdFor(start))
    }

    private fun latestImageFile(): StoredFile? {
        return currentSaveDirectory()?.let { storageManager.latestImageFile(it) }
    }

    private fun setupInputChangeListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonStates(repository.getSession())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        listOf(
            startTimeEdit,
            endTimeEdit,
            intervalEdit,
            folderEdit,
            jpegQualityEdit,
            minBatteryEdit,
            minFreeSpaceEdit
        ).forEach { it.addTextChangedListener(watcher) }

        intervalUnitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateButtonStates(repository.getSession())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun updateButtonStates(config: SessionConfig?) {
        val status = config?.status ?: SessionStatus.NOT_CONFIGURED
        val hasSession = config != null
        val canStart = isSessionFormValid() && status !in ACTIVE_STATUSES

        setButtonEnabled(startButton, canStart)
        setButtonEnabled(pauseButton, status == SessionStatus.WAITING || status == SessionStatus.RUNNING)
        setButtonEnabled(resumeButton, status == SessionStatus.PAUSED)
        setButtonEnabled(stopButton, status == SessionStatus.WAITING || status == SessionStatus.RUNNING || status == SessionStatus.PAUSED)
        setButtonEnabled(blackoutButton, status == SessionStatus.WAITING || status == SessionStatus.RUNNING)
        setButtonEnabled(logButton, hasSession)
        setButtonEnabled(latestImageButton, latestImageFile() != null)
        setButtonEnabled(openFolderButton, currentSaveDirectory() != null)
    }

    private fun setButtonEnabled(button: Button, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1.0f else 0.42f
    }

    private fun isSessionFormValid(): Boolean {
        val start = TimeUtils.parseDisplay(startTimeEdit.text.toString())
        val end = TimeUtils.parseDisplay(endTimeEdit.text.toString())
        val intervalValue = intervalEdit.text.toString().toLongOrNull()
        val jpegQuality = jpegQualityEdit.text.toString().toIntOrNull()
        val minBattery = minBatteryEdit.text.toString().toIntOrNull()
        val minFreeSpaceGb = minFreeSpaceEdit.text.toString().toDoubleOrNull()
        return start != null &&
            end != null &&
            end >= start &&
            end >= System.currentTimeMillis() &&
            intervalValue != null &&
            intervalValue > 0L &&
            jpegQuality != null &&
            jpegQuality in 1..100 &&
            minBattery != null &&
            minBattery in 0..100 &&
            minFreeSpaceGb != null &&
            minFreeSpaceGb >= 0.0
    }

    private fun logRowView(index: Int, columns: List<String>): View {
        val fileName = columns[4].substringAfterLast('/').ifBlank { "-" }
        val error = listOf(columns[7], columns[8]).filter { it.isNotBlank() }.joinToString(": ")
        val body = buildString {
            appendLine("${index}. ${columns[3]}")
            appendLine(getString(R.string.log_row_scheduled, columns[1]))
            appendLine(getString(R.string.log_row_actual, columns[2]))
            appendLine(getString(R.string.log_row_image, fileName))
            appendLine(getString(R.string.log_row_battery_storage, columns[5].ifBlank { "-" }, formatBytes(columns[6].toLongOrNull() ?: 0L)))
            if (error.isNotBlank()) append(getString(R.string.log_row_error, error))
        }

        return TextView(this).apply {
            text = body.trimEnd()
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(10), 0, dp(10))
        }
    }

    private fun parseCsvLine(line: String): List<String>? {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var inQuotes = false
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    values.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values.add(current.toString())
        return values.takeIf { it.size >= 9 }
    }

    private fun pickDateTime(target: EditText) {
        val baseMillis = TimeUtils.parseDisplay(target.text.toString()) ?: TimeUtils.defaultStartMillis()
        val zone = ZoneId.systemDefault()
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(baseMillis), zone)
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        val picked = LocalDateTime.of(year, month + 1, day, hour, minute)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        target.setText(TimeUtils.formatDisplay(picked))
                    },
                    base.hour,
                    base.minute,
                    true
                ).show()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        ).show()
    }

    private fun maybeShowBatteryGuidance() {
        val prefs = getSharedPreferences("app_guidance", MODE_PRIVATE)
        if (prefs.getBoolean("battery_guidance_seen", false)) return
        prefs.edit().putBoolean("battery_guidance_seen", true).apply()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_guidance_title))
            .setMessage(getString(R.string.battery_guidance_message))
            .setPositiveButton(getString(R.string.button_ok), null)
            .setNeutralButton(getString(R.string.button_open_power_settings)) { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .show()
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private fun labeledEdit(
        parent: LinearLayout,
        label: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT
    ): EditText {
        parent.addView(label(label))
        return EditText(this).apply {
            this.inputType = inputType
            setSingleLine(true)
            applyCompactEditStyle()
            parent.addView(this)
        }
    }

    private fun horizontalRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun fieldColumn(marginEnd: Boolean = false, weight: Float = 1f): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                if (marginEnd) setMargins(0, 0, dp(8), 0)
            }
        }
    }

    private fun labeledEditColumn(
        parent: LinearLayout,
        label: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        marginEnd: Boolean = false,
        weight: Float = 1f
    ): EditText {
        val column = fieldColumn(marginEnd, weight)
        column.addView(label(label))
        val edit = EditText(this).apply {
            this.inputType = inputType
            setSingleLine(true)
            applyCompactEditStyle()
        }
        column.addView(edit)
        parent.addView(column)
        return edit
    }

    private fun EditText.applyCompactEditStyle() {
        textSize = 16f
        includeFontPadding = false
        minHeight = dp(40)
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            includeFontPadding = false
            setPadding(0, dp(6), 0, 0)
        }
    }

    private fun statusRow(parent: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            parent.addView(this)
        }
    }

    private fun statusCell(
        parent: LinearLayout,
        label: String,
        marginEnd: Boolean = false,
        singleLine: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = getString(R.string.status_line, label, "-")
            textSize = 12f
            setPadding(0, dp(1), 0, dp(2))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (marginEnd) setMargins(0, 0, dp(8), 0)
            }
            if (singleLine) {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            parent.addView(this)
        }
    }

    private fun statusLineText(labelResId: Int, value: String): String {
        return getString(R.string.status_line, getString(labelResId), value)
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button {
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

    private fun buttonRow(vararg buttons: Button): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            buttons.forEachIndexed { index, button ->
                button.layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (index > 0) setMargins(dp(6), 0, 0, 0)
                }
                addView(button)
            }
        }
    }

    private fun showValidationError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.validation_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_ok), null)
            .show()
    }

    private fun sanitizeFolderName(value: String): String {
        return value.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    private fun defaultIntervalValue(intervalMillis: Long): Pair<String, Int> {
        val hour = 60L * 60L * 1000L
        val minute = 60L * 1000L
        return if (intervalMillis % hour == 0L) {
            (intervalMillis / hour).toString() to 0
        } else {
            (intervalMillis / minute).coerceAtLeast(1L).toString() to 1
        }
    }

    private fun bytesToGbText(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return if (gb % 1.0 == 0.0) gb.toInt().toString() else "%.2f".format(gb)
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return "%.2f GB".format(gb)
    }

    private fun formatInterval(intervalMillis: Long): String {
        val hour = 60L * 60L * 1000L
        val minute = 60L * 1000L
        return if (intervalMillis % hour == 0L) {
            getString(R.string.interval_hours, intervalMillis / hour)
        } else {
            getString(R.string.interval_minutes, intervalMillis / minute)
        }
    }

    private fun statusLabel(status: SessionStatus): String {
        return when (status) {
            SessionStatus.NOT_CONFIGURED -> getString(R.string.status_not_configured)
            SessionStatus.WAITING -> getString(R.string.status_waiting)
            SessionStatus.RUNNING -> getString(R.string.status_running)
            SessionStatus.PAUSED -> getString(R.string.status_paused)
            SessionStatus.COMPLETED -> getString(R.string.status_completed)
            SessionStatus.ERROR -> getString(R.string.status_error)
            SessionStatus.STOPPED -> getString(R.string.status_stopped)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 9001
        private const val REFRESH_INTERVAL_MILLIS = 3_000L
        private const val STALE_RUNNING_TIMEOUT_MILLIS = 3L * 60L * 1000L
        private const val LOG_DIALOG_MAX_ROWS = 100
        private const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
        private val ACTIVE_STATUSES = setOf(
            SessionStatus.WAITING,
            SessionStatus.RUNNING,
            SessionStatus.PAUSED
        )
    }
}
