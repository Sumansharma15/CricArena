package com.example.cricarena.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.databinding.FragmentHomeBinding
import com.example.cricarena.ui.fantasyteam.FantasyTeamFragment

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: HomeViewModel by viewModels()
    private val matchAdapter = MatchAdapter(::onJoinMatch)
    private var loadedMatches: List<Match> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupRecyclerView()
        setupFilterChips()
        viewModel.matches.observe(viewLifecycleOwner) { list ->
            loadedMatches = list
            val chipId = binding.chipGroupFilters.checkedChipIds.firstOrNull() ?: R.id.chipAllMatches
            renderMatches(applyChipFilter(chipId))
        }
        viewModel.loadError.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { setLoading(it) }
        viewModel.loadMatches()
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
            renderMatches(applyChipFilter(selectedId))
        }

        binding.chipAllMatches.isChecked = true
    }

    private fun applyChipFilter(selectedChipId: Int): List<Match> {
        return when (selectedChipId) {
            R.id.chipLiveMatches -> loadedMatches.filter { it.status == MatchStatus.LIVE }
            R.id.chipManualMatches -> loadedMatches.filter { it.category == MatchCategory.MANUAL }
            R.id.chipUpcomingMatches -> loadedMatches.filter { it.status == MatchStatus.UPCOMING }
            R.id.chipCompletedMatches -> loadedMatches.filter { it.status == MatchStatus.COMPLETED }
            else -> loadedMatches
        }
    }

    private fun onJoinMatch(match: Match) {
        val bundle = Bundle().apply {
            putString(FantasyTeamFragment.ARG_MATCH_ID, match.id)
        }
        findNavController().navigate(R.id.fantasyTeamFragment, bundle)
    }

    private fun renderMatches(matches: List<Match>) {
        matchAdapter.submitList(matches)
        binding.textEmptyMatches.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressHome.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMatches()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerMatches.adapter = null
        _binding = null
    }
}
