package com.company.tracker

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.company.tracker.databinding.ActivityOwnerBinding

/**
 * Employee list with live location. Tap a row to open ViewerActivity and
 * watch that employee's camera/mic feed (if they currently have sharing on).
 */
class OwnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOwnerBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db.collection("users").whereEqualTo("role", "employee")
            .addSnapshotListener { snap, _ ->
                binding.employeeList.removeAllViews()
                snap?.documents?.forEach { userDoc ->
                    val row = TextView(this).apply {
                        text = "${userDoc.getString("name")}  (loading location…)"
                        setPadding(24, 24, 24, 24)
                        textSize = 16f
                    }
                    row.setOnClickListener {
                        startActivity(
                            Intent(this, ViewerActivity::class.java)
                                .putExtra("employeeUid", userDoc.id)
                        )
                    }
                    binding.employeeList.addView(row)

                    db.collection("locations").document(userDoc.id)
                        .addSnapshotListener { locDoc, _ ->
                            val active = locDoc?.getBoolean("active") ?: false
                            val lat = locDoc?.getDouble("lat")
                            val lng = locDoc?.getDouble("lng")
                            row.text = if (active && lat != null && lng != null) {
                                "${userDoc.getString("name")}  •  ON  •  $lat, $lng  (tap to view)"
                            } else {
                                "${userDoc.getString("name")}  •  OFF"
                            }
                        }
                }
            }
    }
}
