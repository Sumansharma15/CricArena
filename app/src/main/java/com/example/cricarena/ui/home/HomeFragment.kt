package com.example.cricarena.ui.home

import android.os.Bundle
import android.util.Log
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
import com.example.cricarena.ui.fantasyteam.FantasyMatchSession
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
        binding.textEmptyMatches.text = getString(R.string.no_matches_available)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.matches.observe(viewLifecycleOwner) { refreshHomeList(it) }
        viewModel.loadError.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) {
            setLoading(it)
            if (!it) {
                // Re-evaluate empty state once initial loading is complete.
                refreshHomeList(loadedMatches)
            }
        }
        viewModel.loadMatches()
    }

    private fun refreshHomeList(list: List<Match>) {
        loadedMatches = list
        var chipId = binding.chipGroupFilters.checkedChipIds.firstOrNull() ?: R.id.chipAllMatches
        var filtered = applyChipFilter(chipId)
        // Restored chip (e.g. Live) can filter all rows while Firestore still returns many — looks like "not loaded".
        if (list.isNotEmpty() && filtered.isEmpty() && chipId != R.id.chipAllMatches) {
            binding.chipAllMatches.isChecked = true
            chipId = R.id.chipAllMatches
            filtered = list
        }
        val loading = viewModel.loading.value == true
        Log.d(TAG, "Home rendering ${filtered.size} matches (loaded=${list.size}, chip=$chipId, loading=$loading)")
        renderMatches(filtered, loading)
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
            renderMatches(applyChipFilter(selectedId), viewModel.loading.value == true)
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
        Log.d(NAV_TAG, "Sending matchId: ${match.id}")
        FantasyMatchSession.init(requireContext())
        FantasyMatchSession.remember(match.id)
        val bundle = Bundle().apply {
            putString(FantasyTeamFragment.ARG_MATCH_ID, match.id)
        }
        findNavController().navigate(R.id.action_home_to_fantasy, bundle)
    }

    private fun renderMatches(matches: List<Match>, stillLoading: Boolean = viewModel.loading.value == true) {
        binding.recyclerMatches.visibility = View.VISIBLE
        matchAdapter.submitList(matches)
        val showEmpty = matches.isEmpty() && !stillLoading
        binding.textEmptyMatches.visibility = if (showEmpty) View.VISIBLE else View.GONE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressHome.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val NAV_TAG = "NAV_DEBUG"
        private const val TAG = "HOME_UI"
    }
}
