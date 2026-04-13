package com.example.cricarena.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.example.cricarena.MainActivity
import com.example.cricarena.R
import com.example.cricarena.base.BaseActivity
import com.example.cricarena.databinding.ActivityLoginBinding
import com.example.cricarena.util.FirebaseUtils

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    override fun setupViewBinding(): ActivityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (FirebaseUtils.auth.currentUser != null) {
            navigateToMain()
            return
        }

        binding.buttonLogin.setOnClickListener { attemptLogin() }
        binding.textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        if (!isLoginInputValid(email, password)) return

        setLoading(true)
        FirebaseUtils.auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: getString(R.string.error_login_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun isLoginInputValid(email: String, password: String): Boolean {
        if (email.isBlank()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_required)
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_invalid)
            return false
        }
        binding.inputLayoutEmail.error = null

        if (password.isBlank()) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_required)
            return false
        }
        if (password.length < 6) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_length)
            return false
        }
        binding.inputLayoutPassword.error = null
        return true
    }

    private fun setLoading(isLoading: Boolean) {
        binding.buttonLogin.isEnabled = !isLoading
        binding.progressLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
