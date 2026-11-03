package com.company.tracker

object CameraHelper {
    fun startForegroundCapture(uid: String) {
        // TODO: integrate CameraX preview + mic capture + WebRTC peer
        // connection here, started only from EmployeeActivity.onResume().
    }

    fun stopCapture() {
        // TODO: tear down the peer connection / camera session here,
        // called from EmployeeActivity.onPause() and on toggle-off.
    }
}
