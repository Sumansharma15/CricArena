package com.example.cricarena.ui.mymatches

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.cricarena.databinding.FragmentMyMatchesBinding
import com.google.android.material.tabs.TabLayoutMediator

class MyMatchesFragment : Fragment() {

    private var _binding: FragmentMyMatchesBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: MyMatchesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyMatchesBinding.inflate(inflater, container, false)
        setupTabs()
        return binding.root
    }

    private fun setupTabs() {
        binding.viewPagerMyMatches.adapter = MyMatchesPagerAdapter(this)
        TabLayoutMediator(binding.tabLayoutMyMatches, binding.viewPagerMyMatches) { tab, position ->
            tab.text = when (position) {
                0 -> getString(com.example.cricarena.R.string.tab_joined_matches)
                1 -> getString(com.example.cricarena.R.string.tab_created_matches)
                else -> getString(com.example.cricarena.R.string.tab_completed_matches)
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.viewPagerMyMatches.adapter = null
        _binding = null
    }
}
