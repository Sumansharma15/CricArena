package com.example.cricarena.ui.fantasyteam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.databinding.FragmentFantasyTeamBinding
import com.google.android.material.snackbar.Snackbar

class FantasyTeamFragment : Fragment() {

    private var _binding: FragmentFantasyTeamBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: FantasyTeamViewModel by viewModels()
    private val selectedPlayerIds = mutableSetOf<String>()
    private var captainId: String? = null
    private var viceCaptainId: String? = null
    private var players = listOf<FantasyPlayer>()
    private var selectedMatchId: String = ""

    private val adapter: FantasyPlayerAdapter by lazy {
        FantasyPlayerAdapter(
            onPlayerToggle = ::togglePlayerSelection,
            onCaptainClick = ::setCaptain,
            onViceCaptainClick = ::setViceCaptain
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFantasyTeamBinding.inflate(inflater, container, false)
        setupRecycler()
        setupSaveButton()
        selectedMatchId = arguments?.getString(ARG_MATCH_ID).orEmpty()
        if (selectedMatchId.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_match_id_required, Toast.LENGTH_LONG).show()
        } else {
            fetchPlayers(selectedMatchId)
        }
        renderSelectionState()
        return binding.root
    }

    private fun setupRecycler() {
        binding.recyclerPlayers.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@FantasyTeamFragment.adapter
        }
    }

    private fun setupSaveButton() {
        binding.buttonSaveTeam.setOnClickListener {
            saveTeam()
        }
    }

    private fun fetchPlayers(matchId: String) {
        setLoading(true)
        viewModel.fetchPlayersFromMatch(matchId) { fetchedPlayers, error ->
            setLoading(false)
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                return@fetchPlayersFromMatch
            }
            players = fetchedPlayers
            adapter.submitPlayers(players, selectedPlayerIds, captainId, viceCaptainId)
            binding.textEmptyFantasyPlayers.visibility = if (players.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun togglePlayerSelection(player: FantasyPlayer) {
        if (selectedPlayerIds.contains(player.id)) {
            selectedPlayerIds.remove(player.id)
            if (captainId == player.id) captainId = null
            if (viceCaptainId == player.id) viceCaptainId = null
        } else {
            if (selectedPlayerIds.size >= MAX_PLAYERS) {
                Toast.makeText(requireContext(), R.string.error_max_11_players, Toast.LENGTH_SHORT).show()
                return
            }
            selectedPlayerIds.add(player.id)
        }
        renderSelectionState()
    }

    private fun setCaptain(player: FantasyPlayer) {
        if (!selectedPlayerIds.contains(player.id)) {
            Toast.makeText(requireContext(), R.string.error_select_player_first, Toast.LENGTH_SHORT).show()
            return
        }
        captainId = if (captainId == player.id) null else player.id
        if (captainId != null && captainId == viceCaptainId) viceCaptainId = null
        renderSelectionState()
    }

    private fun setViceCaptain(player: FantasyPlayer) {
        if (!selectedPlayerIds.contains(player.id)) {
            Toast.makeText(requireContext(), R.string.error_select_player_first, Toast.LENGTH_SHORT).show()
            return
        }
        viceCaptainId = if (viceCaptainId == player.id) null else player.id
        if (viceCaptainId != null && viceCaptainId == captainId) captainId = null
        renderSelectionState()
    }

    private fun saveTeam() {
        if (selectedMatchId.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_match_id_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPlayerIds.size != MAX_PLAYERS) {
            Toast.makeText(requireContext(), R.string.error_select_exactly_11, Toast.LENGTH_SHORT).show()
            return
        }
        if (captainId.isNullOrBlank() || viceCaptainId.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.error_captain_vice_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (captainId == viceCaptainId) {
            Toast.makeText(requireContext(), R.string.error_captain_vice_different, Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPlayers = players.filter { selectedPlayerIds.contains(it.id) }
        setLoading(true)
        viewModel.saveTeam(
            matchId = selectedMatchId,
            selectedPlayers = selectedPlayers,
            captainId = captainId.orEmpty(),
            viceCaptainId = viceCaptainId.orEmpty()
        ) { success, error ->
            setLoading(false)
            if (success) {
                Toast.makeText(requireContext(), R.string.team_saved_success, Toast.LENGTH_SHORT).show()
                Snackbar.make(binding.root, R.string.team_saved_success, Snackbar.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), error ?: getString(R.string.error_save_team), Toast.LENGTH_LONG).show()
                Snackbar.make(binding.root, error ?: getString(R.string.error_save_team), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun renderSelectionState() {
        binding.textSelectedCount.text = getString(
            R.string.selected_count_format,
            selectedPlayerIds.size,
            MAX_PLAYERS
        )
        binding.textEmptyFantasyPlayers.visibility = if (players.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitPlayers(players, selectedPlayerIds, captainId, viceCaptainId)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressFantasy.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSaveTeam.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerPlayers.adapter = null
        _binding = null
    }

    companion object {
        const val ARG_MATCH_ID = "match_id"
        private const val MAX_PLAYERS = 11
    }
}
