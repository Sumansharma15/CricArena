package com.example.cricarena

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.cricarena.base.BaseActivity
import com.example.cricarena.databinding.ActivitySplashBinding

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        startActivity(Intent(this, com.example.cricarena.auth.LoginActivity::class.java))
        finish()
    }

    override fun setupViewBinding(): ActivitySplashBinding = ActivitySplashBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        handler.postDelayed(navigateRunnable, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 3000L
    }
}
