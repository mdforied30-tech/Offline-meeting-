package com.company.tracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LocationForegroundService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private var callback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "location_tracking"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.company.tracker.STOP_TRACKING"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val notification = createTrackingNotification()
    startForeground(NOTIFICATION_ID, notification)
    startLocationUpdates()
    return START_STICKY
}

private fun createTrackingNotification(): Notification {
    val channelId = "tracking_channel"
    val channel = NotificationChannel(
        channelId, "Tracking Active",
        NotificationManager.IMPORTANCE_LOW
    )
    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)

    return NotificationCompat.Builder(this, channelId)
        .setContentTitle("Tracking System Active")
        .setContentText("Location tracking is currently on")
        .setSmallIcon(R.drawable.ic_tracking)
        .setOngoing(true)   // স্ক্রিপ করা যাবে না, সবসময় দেখা যাবে
        .build()
}
    private fun startLocationUpdates() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return stopSelf()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        // Balanced accuracy + a real interval keeps this running reliably
        // for hours without draining the battery or getting killed by the
        // OS for being too aggressive.
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMaxUpdateDelayMillis(30_000L)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                db.collection("locations").document(uid).set(
                    mapOf(
                        "lat" to loc.latitude,
                        "lng" to loc.longitude,
                        "accuracy" to loc.accuracy,
                        "active" to true,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).addOnFailureListener {
                    // Firestore queues writes locally and retries once the
                    // network returns, so a brief offline moment here is
                    // not lost — just delayed.
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // GPS/network signal temporarily lost (e.g. indoors, tunnel).
                // Nothing to do — updates resume automatically once
                // location becomes available again.
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback as LocationCallback, mainLooper)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopTracking() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            db.collection("locations").document(uid)
                .set(mapOf("active" to false), SetOptions.merge())
        }
        callback?.let { if (::fusedClient.isInitialized) fusedClient.removeLocationUpdates(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        callback?.let { if (::fusedClient.isInitialized) fusedClient.removeLocationUpdates(it) }
    }
}
