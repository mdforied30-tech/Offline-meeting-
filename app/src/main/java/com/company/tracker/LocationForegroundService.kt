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
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location sharing is on")
            .setContentText("Your manager can see your location. Tap Stop to turn it off.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop sharing", stopPending)
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
