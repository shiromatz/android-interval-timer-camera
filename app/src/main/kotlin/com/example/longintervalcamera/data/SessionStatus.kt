package com.example.longintervalcamera.data

enum class SessionStatus {
    NOT_CONFIGURED,
    WAITING,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR,
    STOPPED;

    val isUnfinished: Boolean
        get() = this == WAITING || this == RUNNING || this == PAUSED
}
