package com.example.cricarena.ui.fantasyteam

import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.data.model.Match
import com.example.cricarena.databinding.FragmentFantasyTeamBinding

class FantasyTeamFragment : Fragment() {

    private var _binding: FragmentFantasyTeamBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: FantasyTeamViewModel by viewModels()
    private val selectedPlayerIds = mutableSetOf<String>()
    private var captainId: String? = null
    private var viceCaptainId: String? = null
    private var players = listOf<FantasyPlayer>()
    private var selectedMatchId: String = ""
    private var selectedMatchTitle: String = ""
    private var hasAppliedSavedTeam = false

    private val matchPickerAdapter: FantasyMatchPickerAdapter by lazy {
        FantasyMatchPickerAdapter(::onFantasyMatchPicked)
    }

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
        FantasyMatchSession.init(requireContext())
        _binding = FragmentFantasyTeamBinding.inflate(inflater, container, false)
        setupRecycler()
        setupMatchPickerRecycler()
        setupSaveButton()
        setupChangeMatch()

        val fromArgs = arguments?.getString(ARG_MATCH_ID).orEmpty()
            .ifBlank { arguments?.getString("matchId").orEmpty() }
        if (fromArgs.isNotBlank()) {
            FantasyMatchSession.remember(fromArgs)
        }
        selectedMatchId = fromArgs.ifBlank { FantasyMatchSession.lastMatchId }
        Log.d(NAV_TAG, "matchId from args='$fromArgs' resolved='$selectedMatchId'")

        if (selectedMatchId.isNotBlank()) {
            selectedMatchTitle = ""
            enterTeamBuilderForMatch(selectedMatchId)
        } else {
            showMatchPickerOnly()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.observeEligibleMatches()
        bindObservers()
    }

    private fun matchDisplayName(match: Match?): String {
        if (match == null) return ""
        return match.title.ifBlank {
            getString(R.string.default_match_title, match.teamA, match.teamB)
        }
    }

    private fun enterTeamBuilderForMatch(matchId: String) {
        selectedMatchId = matchId
        FantasyMatchSession.remember(matchId)
        resetLocalSelection()
        hasAppliedSavedTeam = false

        showTeamBuilderUi()
        viewModel.loadMatchForFantasy(matchId)
        viewModel.fetchSavedTeam(matchId) { _, fetchErr ->
            if (fetchErr != null) {
                Toast.makeText(requireContext(), fetchErr, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onFantasyMatchPicked(match: Match) {
        selectedMatchTitle = matchDisplayName(match)
        enterTeamBuilderForMatch(match.id)
    }

    private fun showMatchPickerOnly() {
        binding.containerMatchPicker.visibility = View.VISIBLE
        binding.containerTeamBuilder.visibility = View.GONE
        binding.recyclerFantasyMatches.visibility = View.VISIBLE
        binding.textSelectedCount.visibility = View.GONE
        binding.buttonChangeMatch.visibility = View.GONE
    }

    private fun showTeamBuilderUi() {
        binding.containerMatchPicker.visibility = View.GONE
        binding.containerTeamBuilder.visibility = View.VISIBLE
        binding.textSelectedCount.visibility = View.VISIBLE
        binding.buttonChangeMatch.visibility = View.VISIBLE
    }

    private fun setupChangeMatch() {
        binding.buttonChangeMatch.setOnClickListener {
            viewModel.resetPlayerStreams()
            selectedMatchId = ""
            selectedMatchTitle = ""
            resetLocalSelection()
            hasAppliedSavedTeam = false
            showMatchPickerOnly()
        }
    }

    private fun resetLocalSelection() {
        selectedPlayerIds.clear()
        captainId = null
        viceCaptainId = null
    }

    private fun setupRecycler() {
        binding.recyclerPlayers.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@FantasyTeamFragment.adapter
        }
    }

    private fun setupMatchPickerRecycler() {
        binding.recyclerFantasyMatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = matchPickerAdapter
        }
    }

    private fun setupSaveButton() {
        binding.buttonSaveTeam.setOnClickListener {
            saveTeam()
        }
    }

    private fun renderFantasyMatchPicker() {
        val matches = viewModel.eligibleMatches.value.orEmpty()
        val listLoading = viewModel.eligibleMatchesLoading.value == true
        Log.d(NAV_TAG, "Fantasy picker showing ${matches.size} eligible matches (listLoading=$listLoading)")
        binding.recyclerFantasyMatches.visibility = View.VISIBLE
        matchPickerAdapter.submitList(matches)
        binding.progressFantasyMatchList.visibility = if (listLoading) View.VISIBLE else View.GONE
        binding.textEmptyFantasyMatches.visibility =
            if (matches.isEmpty() && !listLoading) View.VISIBLE else View.GONE
        if (selectedMatchId.isNotBlank() && selectedMatchTitle.isBlank()) {
            matches.find { it.id == selectedMatchId }?.let {
                selectedMatchTitle = matchDisplayName(it)
            }
        }
    }

    private fun bindObservers() {
        viewModel.eligibleMatches.observe(viewLifecycleOwner) { renderFantasyMatchPicker() }
        viewModel.eligibleMatchesLoading.observe(viewLifecycleOwner) { renderFantasyMatchPicker() }
        viewModel.players.observe(viewLifecycleOwner) { fetchedPlayers ->
            players = fetchedPlayers
            binding.textPoolHint.text = getString(R.string.fantasy_pool_hint, fetchedPlayers.size)
            applySavedSelectionIfAvailable()
            renderSelectionState()
        }
        viewModel.savedTeam.observe(viewLifecycleOwner) {
            applySavedSelectionIfAvailable()
            renderSelectionState()
        }
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            setLoading(isLoading)
        }
        viewModel.loadError.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applySavedSelectionIfAvailable() {
        val saved = viewModel.savedTeam.value ?: return
        if (hasAppliedSavedTeam) return
        val validIds = players.map { it.id }.toSet()
        selectedPlayerIds.clear()
        selectedPlayerIds.addAll(saved.selectedPlayerIds.intersect(validIds))
        captainId = saved.captainId.takeIf { it.isNotBlank() && validIds.contains(it) }
        viceCaptainId = saved.viceCaptainId.takeIf { it.isNotBlank() && validIds.contains(it) }
        if (captainId != null && captainId == viceCaptainId) viceCaptainId = null
        if (selectedPlayerIds.isNotEmpty()) {
            Toast.makeText(requireContext(), R.string.fantasy_loaded_saved_team, Toast.LENGTH_SHORT).show()
        }
        hasAppliedSavedTeam = true
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

        val displayName = selectedMatchTitle.ifBlank {
            viewModel.eligibleMatches.value?.find { it.id == selectedMatchId }?.let { matchDisplayName(it) }
                .orEmpty()
        }.ifBlank { "Match $selectedMatchId" }

        val selectedPlayers = players.filter { selectedPlayerIds.contains(it.id) }
        setLoading(true)
        viewModel.saveTeam(
            matchId = selectedMatchId,
            matchDisplayName = displayName,
            selectedPlayers = selectedPlayers,
            captainId = captainId.orEmpty(),
            viceCaptainId = viceCaptainId.orEmpty()
        ) { success, error ->
            setLoading(false)
            if (success) {
                FantasyMatchSession.remember(selectedMatchId)
                Toast.makeText(requireContext(), R.string.team_saved_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), error ?: getString(R.string.error_save_team), Toast.LENGTH_LONG).show()
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
        _binding = null
    }

    companion object {
        const val ARG_MATCH_ID = "match_id"
        private const val MAX_PLAYERS = 11
        private const val TAG = "FIREBASE_DEBUG"
        private const val NAV_TAG = "NAV_DEBUG"
    }
}
