package com.company.tracker

import android.content.Context

/**
 * Thin bridge between EmployeeActivity's toggle and the real WebRTC work
 * in CallerClient. No UI of its own — on() mirrors the toggle going on,
 * off() mirrors it going off.
 */
object CameraHelper {
    fun startForegroundCapture(context: Context, ownerUid: String) {
        CallerClient.start(context, ownerUid)
    }

    fun stopCapture() {
        CallerClient.stop()
    }
}
