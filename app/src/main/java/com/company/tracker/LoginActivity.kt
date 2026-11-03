package com.company.tracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.company.tracker.databinding.ActivityLoginBinding

const val OWNER_EMAIL = "mdforied30@gmail.com"

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.roleGroup.check(binding.roleEmployee.id)

        binding.loginBtn.setOnClickListener { login() }
        binding.signupBtn.setOnClickListener { signup() }
    }

    private fun login() {
        val email = binding.emailInput.text.toString().trim()
        val pass = binding.passwordInput.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            toast("Email ও password দিন")
            return
        }
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val role = doc.getString("role") ?: "employee"
                        routeTo(role)
                    }
            }
            .addOnFailureListener { toast("Login ব্যর্থ: ${it.message}") }
    }

    private fun signup() {
        val email = binding.emailInput.text.toString().trim()
        val pass = binding.passwordInput.text.toString()
        val name = binding.nameInput.text.toString().trim().ifEmpty { email.substringBefore("@") }
        val isOwner = binding.roleGroup.checkedRadioButtonId == binding.roleOwner.id

        if (email.isEmpty() || pass.length < 6) {
            toast("Email ও কমপক্ষে ৬ ক্যারেক্টারের password দিন")
            return
        }
        if (isOwner && !email.equals(OWNER_EMAIL, ignoreCase = true)) {
            toast("এই email দিয়ে Owner account খোলা যাবে না।")
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val role = if (isOwner) "owner" else "employee"
                val data = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to role
                )
                db.collection("users").document(uid).set(data)
                    .addOnSuccessListener { routeTo(role) }
            }
            .addOnFailureListener { toast("Signup ব্যর্থ: ${it.message}") }
    }

    private fun routeTo(role: String) {
        val cls = if (role == "owner") OwnerActivity::class.java else EmployeeActivity::class.java
        startActivity(Intent(this, cls))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
