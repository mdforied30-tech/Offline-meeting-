package com.company.tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.company.tracker.databinding.ActivityEmployeeBinding

class EmployeeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmployeeBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val permissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startSharing()
        } else {
            binding.shareToggle.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmployeeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusText.text = "Location + Microphone + Camera Access"

        binding.shareToggle.setOnCheckedChangeListener { _, isOn ->
            if (isOn) requestPermissionsAndStart() else stopSharing()
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.shareToggle.isChecked) {
            auth.currentUser?.uid?.let { CameraHelper.startForegroundCapture(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        CameraHelper.stopCapture()
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startSharing() else permissionRequest.launch(missing.toTypedArray())
    }

    private fun startSharing() {
        val uid = auth.currentUser?.uid ?: return
        startForegroundService(Intent(this, LocationForegroundService::class.java))
        CameraHelper.startForegroundCapture(uid)
        binding.statusText.text = "Location + Microphone + Camera Access  •  On"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 42
            )
        }
    }

    private fun stopSharing() {
        val uid = auth.currentUser?.uid ?: return
        startService(Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        })
        CameraHelper.stopCapture()
        db.collection("locations").document(uid)
            .set(mapOf("active" to false), SetOptions.merge())
        binding.statusText.text = "Location + Microphone + Camera Access  •  Off"
    }
}
