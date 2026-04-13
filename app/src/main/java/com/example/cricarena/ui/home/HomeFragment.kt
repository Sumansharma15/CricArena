package com.example.cricarena.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: HomeViewModel by viewModels()
    private val matchAdapter = MatchAdapter()
    private val allMatches = listOf(
        Match("1", "IND", "AUS", "T20", MatchCategory.MANUAL, MatchStatus.LIVE, "07:30 PM"),
        Match("2", "ENG", "NZ", "ODI", MatchCategory.AUTO, MatchStatus.UPCOMING, "08:15 PM"),
        Match("3", "PAK", "SA", "Test", MatchCategory.MANUAL, MatchStatus.COMPLETED, "10:00 AM"),
        Match("4", "WI", "SL", "T20", MatchCategory.AUTO, MatchStatus.UPCOMING, "09:00 PM"),
        Match("5", "BAN", "AFG", "ODI", MatchCategory.MANUAL, MatchStatus.LIVE, "06:45 PM")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupFilterChips()
        renderMatches(allMatches)
        return binding.root
    }

    private fun setupRecyclerView() {
        binding.recyclerMatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = matchAdapter
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: R.id.chipAllMatches
            val filtered = when (selectedId) {
                R.id.chipLiveMatches -> allMatches.filter { it.status == MatchStatus.LIVE }
                R.id.chipManualMatches -> allMatches.filter { it.category == MatchCategory.MANUAL }
                R.id.chipUpcomingMatches -> allMatches.filter { it.status == MatchStatus.UPCOMING }
                R.id.chipCompletedMatches -> allMatches.filter { it.status == MatchStatus.COMPLETED }
                else -> allMatches
            }
            renderMatches(filtered)
        }

        binding.chipAllMatches.isChecked = true
    }

    private fun renderMatches(matches: List<Match>) {
        matchAdapter.submitList(matches)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerMatches.adapter = null
        _binding = null
    }
}
