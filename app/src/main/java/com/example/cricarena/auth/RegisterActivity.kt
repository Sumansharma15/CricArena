package com.example.cricarena.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.example.cricarena.MainActivity
import com.example.cricarena.R
import com.example.cricarena.base.BaseActivity
import com.example.cricarena.databinding.ActivityRegisterBinding
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.FieldValue

class RegisterActivity : BaseActivity<ActivityRegisterBinding>() {

    override fun setupViewBinding(): ActivityRegisterBinding = ActivityRegisterBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.buttonRegister.setOnClickListener { attemptRegister() }
        binding.textLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val confirmPassword = binding.inputConfirmPassword.text?.toString().orEmpty()

        if (!isRegisterInputValid(name, email, password, confirmPassword)) return

        setLoading(true)
        FirebaseUtils.auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { authTask ->
                if (!authTask.isSuccessful) {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        authTask.exception?.localizedMessage ?: getString(R.string.error_register_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnCompleteListener
                }

                val userId = FirebaseUtils.auth.currentUser?.uid
                if (userId.isNullOrBlank()) {
                    setLoading(false)
                    Toast.makeText(this, getString(R.string.error_register_failed), Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }
                val payload = hashMapOf(
                    "userId" to userId,
                    "name" to name,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                FirebaseUtils.userDocument(userId).set(payload)
                    .addOnSuccessListener { navigateToMain() }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Toast.makeText(
                            this,
                            e.localizedMessage ?: getString(R.string.error_user_save_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
    }

    private fun isRegisterInputValid(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        if (name.isBlank()) {
            binding.inputLayoutName.error = getString(R.string.error_name_required)
            return false
        }
        binding.inputLayoutName.error = null

        if (email.isBlank()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_required)
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_invalid)
            return false
        }
        binding.inputLayoutEmail.error = null

        if (password.length < 6) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_length)
            return false
        }
        binding.inputLayoutPassword.error = null

        if (confirmPassword != password) {
            binding.inputLayoutConfirmPassword.error = getString(R.string.error_password_mismatch)
            return false
        }
        binding.inputLayoutConfirmPassword.error = null
        return true
    }

    private fun setLoading(isLoading: Boolean) {
        binding.buttonRegister.isEnabled = !isLoading
        binding.progressRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
