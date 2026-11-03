package com.company.tracker

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.company.tracker.databinding.ActivityOwnerBinding

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
                    binding.employeeList.addView(row)

                    db.collection("locations").document(userDoc.id)
                        .addSnapshotListener { locDoc, _ ->
                            val active = locDoc?.getBoolean("active") ?: false
                            val lat = locDoc?.getDouble("lat")
                            val lng = locDoc?.getDouble("lng")
                            row.text = if (active && lat != null && lng != null) {
                                "${userDoc.getString("name")}  •  ON  •  $lat, $lng"
                            } else {
                                "${userDoc.getString("name")}  •  OFF"
                            }
                        }
                }
            }
    }
}
