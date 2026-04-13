package com.example.cricarena.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: error("ViewBinding accessed before initialization.")

    protected abstract fun setupViewBinding(): VB

    protected abstract fun onActivityCreated(savedInstanceState: Bundle?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = setupViewBinding()
        setContentView(binding.root)
        onActivityCreated(savedInstanceState)
    }
}
