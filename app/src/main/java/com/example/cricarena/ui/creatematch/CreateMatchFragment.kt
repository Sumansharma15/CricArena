package com.example.cricarena.ui.creatematch

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.databinding.FragmentCreateMatchBinding
import com.example.cricarena.databinding.DialogAddPlayerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateMatchFragment : Fragment() {

    private var _binding: FragmentCreateMatchBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: CreateMatchViewModel by viewModels()
    private val playerAdapter = PlayerAdapter(
        onEditClick = ::onEditPlayer,
        onDeleteClick = ::onDeletePlayer
    )
    private val liveScoringViewModel: LiveScoringViewModel by viewModels()
    private val livePlayerAdapter = LivePlayerAdapter(::openPlayerScoring)
    private val players = mutableListOf<MatchPlayer>()
    private val calendar: Calendar = Calendar.getInstance()
    private var selectedStartTimeMillis: Long = 0L
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val scoringLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val playerId = data.getStringExtra(PlayerScoringActivity.RESULT_PLAYER_ID).orEmpty()
            val action = data.getStringExtra(PlayerScoringActivity.RESULT_ACTION).orEmpty()
            val runs = data.getIntExtra(PlayerScoringActivity.RESULT_RUNS, 0)
            when (action) {
                PlayerScoringActivity.ACTION_RUN -> liveScoringViewModel.applyRun(playerId, runs)
                PlayerScoringActivity.ACTION_WIDE -> liveScoringViewModel.applyWide(playerId)
                PlayerScoringActivity.ACTION_NO_BALL -> liveScoringViewModel.applyNoBall(playerId)
                PlayerScoringActivity.ACTION_BYE -> liveScoringViewModel.applyBye(playerId)
                PlayerScoringActivity.ACTION_WICKET -> liveScoringViewModel.applyWicket(playerId)
                PlayerScoringActivity.ACTION_PLAYER_WICKET -> liveScoringViewModel.applyPlayerWicket(playerId)
                PlayerScoringActivity.ACTION_CATCH -> liveScoringViewModel.applyCatch(playerId)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateMatchBinding.inflate(inflater, container, false)
        setupMatchTypeDropdown()
        setupPlayersRecycler()
        setupLiveRecycler()
        setupStartDateTimePicker()
        setupTeamInputs()
        setupActions()
        setupLiveObservers()
        updateTeamCountUi(
            binding.inputTeamA.text?.toString()?.trim().orEmpty(),
            binding.inputTeamB.text?.toString()?.trim().orEmpty()
        )
        refreshLiveHeaderDefaults()
        return binding.root
    }

    private fun setupTeamInputs() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateTeamCountUi(
                    binding.inputTeamA.text?.toString()?.trim().orEmpty(),
                    binding.inputTeamB.text?.toString()?.trim().orEmpty()
                )
            }
        }
        binding.inputTeamA.addTextChangedListener(watcher)
        binding.inputTeamB.addTextChangedListener(watcher)
    }

    private fun setupMatchTypeDropdown() {
        val matchTypes = listOf("T20", "ODI", "Custom")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, matchTypes)
        binding.inputMatchType.setAdapter(adapter)
    }

    private fun setupPlayersRecycler() {
        binding.recyclerPlayers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playerAdapter
        }
        updatePlayersEmptyState()
    }

    private fun setupLiveRecycler() {
        binding.recyclerLivePlayers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = livePlayerAdapter
        }
    }

    private fun setupStartDateTimePicker() {
        binding.inputStartDateTime.setOnClickListener {
            showDatePicker()
        }
        binding.inputLayoutStartDateTime.setEndIconOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        val is24Hour = DateFormat.is24HourFormat(requireContext())
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                selectedStartTimeMillis = calendar.timeInMillis
                binding.inputStartDateTime.setText(dateFormatter.format(calendar.time))
                binding.inputLayoutStartDateTime.error = null
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            is24Hour
        ).show()
    }

    private fun setupActions() {
        binding.buttonAddPlayer.setOnClickListener { showAddPlayerDialog() }
        binding.buttonSaveTeam.setOnClickListener { saveTeamToFirestore() }
        binding.buttonSaveDraft.setOnClickListener { submitMatch("DRAFT") }
        binding.buttonPublishMatch.setOnClickListener { submitMatch("PUBLISHED") }
        binding.radioGroupTeams.setOnCheckedChangeListener { _, checkedId ->
            val selectedTeam = if (checkedId == R.id.radioTeamA) {
                binding.inputTeamA.text?.toString()?.trim().orEmpty()
            } else {
                binding.inputTeamB.text?.toString()?.trim().orEmpty()
            }
            if (selectedTeam.isNotBlank()) {
                liveScoringViewModel.setSelectedTeam(selectedTeam)
            }
        }
    }

    private fun showAddPlayerDialog() {
        showPlayerDialog(playerToEdit = null, editingIndex = null)
    }

    private fun showPlayerDialog(playerToEdit: MatchPlayer?, editingIndex: Int?) {
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty()
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty()
        if (teamA.isBlank() || teamB.isBlank()) {
            Snackbar.make(binding.root, R.string.error_enter_teams_before_players, Snackbar.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogAddPlayerBinding.inflate(layoutInflater)
        val roleOptions = listOf(
            getString(R.string.role_batsman),
            getString(R.string.role_bowler),
            getString(R.string.role_all_rounder),
            getString(R.string.role_wicketkeeper)
        )
        val teamOptions = listOf(teamA, teamB)
        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, roleOptions)
        val teamAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, teamOptions)
        dialogBinding.inputPlayerRole.setAdapter(roleAdapter)
        dialogBinding.inputPlayerTeam.setAdapter(teamAdapter)
        if (playerToEdit != null) {
            dialogBinding.inputPlayerName.setText(playerToEdit.name)
            dialogBinding.inputPlayerRole.setText(playerToEdit.role, false)
            dialogBinding.inputPlayerTeam.setText(playerToEdit.teamName, false)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (playerToEdit == null) R.string.add_player else R.string.edit_player)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (playerToEdit == null) R.string.add else R.string.update, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val name = dialogBinding.inputPlayerName.text?.toString()?.trim().orEmpty()
                        val role = dialogBinding.inputPlayerRole.text?.toString()?.trim().orEmpty()
                        val teamName = dialogBinding.inputPlayerTeam.text?.toString()?.trim().orEmpty()
                        if (name.isBlank()) {
                            dialogBinding.inputLayoutPlayerName.error = getString(R.string.error_player_name_required)
                            return@setOnClickListener
                        }
                        dialogBinding.inputLayoutPlayerName.error = null
                        if (role.isBlank()) {
                            dialogBinding.inputLayoutPlayerRole.error = getString(R.string.error_player_role_required)
                            return@setOnClickListener
                        }
                        dialogBinding.inputLayoutPlayerRole.error = null
                        if (teamName.isBlank()) {
                            dialogBinding.inputLayoutPlayerTeam.error = getString(R.string.error_player_team_required)
                            return@setOnClickListener
                        }
                        dialogBinding.inputLayoutPlayerTeam.error = null
                        if (players.withIndex().any {
                                it.index != editingIndex &&
                                    it.value.name.equals(name, ignoreCase = true) &&
                                    it.value.teamName == teamName
                            }
                        ) {
                            dialogBinding.inputLayoutPlayerName.error = getString(R.string.error_player_duplicate)
                            return@setOnClickListener
                        }

                        val teamCount = players.withIndex().count {
                            it.value.teamName == teamName && it.index != editingIndex
                        }
                        if (teamCount >= TEAM_SIZE) {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.error_max_players_per_team, teamName),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        if (editingIndex == null) {
                            players.add(MatchPlayer(name, role, teamName))
                        } else {
                            players[editingIndex] = MatchPlayer(name, role, teamName)
                        }
                        playerAdapter.submitList(players.toList())
                        updatePlayersEmptyState()
                        updateTeamCountUi(teamA, teamB)
                        initializeLiveScoring()
                        Snackbar.make(
                            binding.root,
                            if (editingIndex == null) R.string.player_added else R.string.player_updated,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun onEditPlayer(position: Int, player: MatchPlayer) {
        if (position !in players.indices) return
        showPlayerDialog(playerToEdit = player, editingIndex = position)
    }

    private fun onDeletePlayer(position: Int, player: MatchPlayer) {
        if (position !in players.indices) return
        players.removeAt(position)
        playerAdapter.submitList(players.toList())
        updatePlayersEmptyState()
        updateTeamCountUi(
            binding.inputTeamA.text?.toString()?.trim().orEmpty(),
            binding.inputTeamB.text?.toString()?.trim().orEmpty()
        )
        initializeLiveScoring()
        Snackbar.make(binding.root, R.string.player_removed, Snackbar.LENGTH_SHORT).show()
    }

    private fun saveTeamToFirestore() {
        val title = binding.inputMatchTitle.text?.toString()?.trim().orEmpty()
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty()
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty()
        val matchType = binding.inputMatchType.text?.toString()?.trim().orEmpty()
        if (!validateForm(title, teamA, teamB, matchType, status = STATUS_SQUADS_SAVED)) return

        setSubmitting(true)
        viewModel.saveMatch(
            title = title,
            teamA = teamA,
            teamB = teamB,
            matchType = matchType,
            startTimeMillis = selectedStartTimeMillis,
            players = players.toList(),
            status = STATUS_SQUADS_SAVED
        ) { success, idOrMessage ->
            setSubmitting(false)
            if (success) {
                val matchId = idOrMessage.orEmpty()
                initializeLiveScoring()
                liveScoringViewModel.attachFirestoreMatch(matchId, teamA, teamB)
                Toast.makeText(requireContext(), R.string.squads_saved, Toast.LENGTH_LONG).show()
                Snackbar.make(binding.root, R.string.squads_saved, Snackbar.LENGTH_LONG).show()
            } else {
                val err = idOrMessage ?: getString(R.string.error_match_save)
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun submitMatch(status: String) {
        val title = binding.inputMatchTitle.text?.toString()?.trim().orEmpty()
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty()
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty()
        val matchType = binding.inputMatchType.text?.toString()?.trim().orEmpty()

        if (!validateForm(title, teamA, teamB, matchType, status)) return

        setSubmitting(true)
        viewModel.saveMatch(
            title = title,
            teamA = teamA,
            teamB = teamB,
            matchType = matchType,
            startTimeMillis = selectedStartTimeMillis,
            players = players.toList(),
            status = status
        ) { success, idOrMessage ->
            setSubmitting(false)
            if (success) {
                Toast.makeText(
                    requireContext(),
                    if (status == "DRAFT") getString(R.string.draft_saved) else getString(R.string.match_published),
                    Toast.LENGTH_SHORT
                ).show()
                Snackbar.make(
                    binding.root,
                    if (status == "DRAFT") getString(R.string.draft_saved) else getString(R.string.match_published),
                    Snackbar.LENGTH_SHORT
                ).show()
                clearForm()
            } else {
                val err = idOrMessage ?: getString(R.string.error_match_save)
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun validateForm(
        title: String,
        teamA: String,
        teamB: String,
        matchType: String,
        status: String
    ): Boolean {
        if (title.isBlank()) {
            binding.inputLayoutMatchTitle.error = getString(R.string.error_match_title_required)
            return false
        }
        binding.inputLayoutMatchTitle.error = null

        if (teamA.isBlank()) {
            binding.inputLayoutTeamA.error = getString(R.string.error_team_a_required)
            return false
        }
        binding.inputLayoutTeamA.error = null

        if (teamB.isBlank()) {
            binding.inputLayoutTeamB.error = getString(R.string.error_team_b_required)
            return false
        }
        if (teamA.equals(teamB, ignoreCase = true)) {
            binding.inputLayoutTeamB.error = getString(R.string.error_team_duplicate)
            return false
        }
        binding.inputLayoutTeamB.error = null

        if (matchType.isBlank()) {
            binding.inputLayoutMatchType.error = getString(R.string.error_match_type_required)
            return false
        }
        binding.inputLayoutMatchType.error = null

        if (selectedStartTimeMillis <= 0L) {
            binding.inputLayoutStartDateTime.error = getString(R.string.error_start_time_required)
            return false
        }
        binding.inputLayoutStartDateTime.error = null

        if (players.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_player_required, Toast.LENGTH_SHORT).show()
            return false
        }
        val teamACount = players.count { it.teamName == teamA }
        val teamBCount = players.count { it.teamName == teamB }
        when (status) {
            "PUBLISHED", STATUS_SQUADS_SAVED -> {
                if (teamACount != TEAM_SIZE || teamBCount != TEAM_SIZE) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.error_exact_11_each_team),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return false
                }
            }
            else -> {
                if (teamACount > TEAM_SIZE || teamBCount > TEAM_SIZE) {
                    Snackbar.make(binding.root, getString(R.string.error_team_limit_exceeded), Snackbar.LENGTH_LONG)
                        .show()
                    return false
                }
            }
        }
        return true
    }

    private fun clearForm() {
        binding.inputMatchTitle.text?.clear()
        binding.inputTeamA.text?.clear()
        binding.inputTeamB.text?.clear()
        binding.inputMatchType.text?.clear()
        binding.inputStartDateTime.text?.clear()
        selectedStartTimeMillis = 0L
        players.clear()
        playerAdapter.submitList(emptyList())
        updatePlayersEmptyState()
        updateTeamCountUi("", "")
        setTeamFieldsLocked(false)
        livePlayerAdapter.submitList(emptyList())
        liveScoringViewModel.clearFirestoreAttachment()
        refreshLiveHeaderDefaults()
    }

    private fun updatePlayersEmptyState() {
        binding.textEmptyPlayers.visibility = if (players.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateTeamCountUi(teamA: String, teamB: String) {
        val teamAName = teamA.ifBlank { getString(R.string.team_a_short) }
        val teamBName = teamB.ifBlank { getString(R.string.team_b_short) }
        val teamACount = players.count { it.teamName == teamA }
        val teamBCount = players.count { it.teamName == teamB }
        binding.textTeamACount.text = getString(R.string.team_count_format, teamAName, teamACount, TEAM_SIZE)
        binding.textTeamBCount.text = getString(R.string.team_count_format, teamBName, teamBCount, TEAM_SIZE)
        binding.radioTeamA.text = teamAName
        binding.radioTeamB.text = teamBName
        setTeamFieldsLocked(players.isNotEmpty())
        val squadsReady = teamA.isNotBlank() && teamB.isNotBlank() &&
            teamACount == TEAM_SIZE && teamBCount == TEAM_SIZE
        binding.buttonSaveTeam.visibility = if (squadsReady) View.VISIBLE else View.GONE
    }

    private fun initializeLiveScoring() {
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty()
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty()
        if (teamA.isBlank() || teamB.isBlank() || players.isEmpty()) return
        val title = if (binding.inputMatchTitle.text.isNullOrBlank()) {
            getString(R.string.live_match_title_default, teamA, teamB)
        } else {
            "${binding.inputMatchTitle.text} ($teamA vs $teamB)"
        }
        binding.textLiveMatchTitle.text = title
        liveScoringViewModel.initialize(title, teamA, teamB, players)
    }

    private fun setupLiveObservers() {
        liveScoringViewModel.scoreLine.observe(viewLifecycleOwner) {
            binding.textLiveScore.text = it
        }
        liveScoringViewModel.currentBatsman.observe(viewLifecycleOwner) {
            binding.textCurrentBatsman.text = getString(R.string.current_batsman_format, it)
        }
        liveScoringViewModel.currentBowler.observe(viewLifecycleOwner) {
            binding.textCurrentBowler.text = getString(R.string.current_bowler_format, it)
        }
        liveScoringViewModel.filteredPlayers.observe(viewLifecycleOwner) { list ->
            livePlayerAdapter.submitList(list)
            binding.textEmptyLivePlayers.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openPlayerScoring(player: LivePlayer) {
        if (!liveScoringViewModel.isInitialized()) {
            initializeLiveScoring()
        }
        val current = liveScoringViewModel.getPlayerState(player.id)
        val intent = Intent(requireContext(), PlayerScoringActivity::class.java).apply {
            putExtra(PlayerScoringActivity.EXTRA_PLAYER_ID, player.id)
            putExtra(PlayerScoringActivity.EXTRA_PLAYER_NAME, player.name)
            putExtra(PlayerScoringActivity.EXTRA_CURRENT_RUNS, current?.runs ?: 0)
            putExtra(PlayerScoringActivity.EXTRA_CURRENT_BALLS, current?.balls ?: 0)
        }
        scoringLauncher.launch(intent)
    }

    private fun refreshLiveHeaderDefaults() {
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty().ifBlank { getString(R.string.team_a_short) }
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty().ifBlank { getString(R.string.team_b_short) }
        binding.textLiveMatchTitle.text = getString(R.string.live_match_title_default, teamA, teamB)
        binding.textLiveScore.text = getString(R.string.live_score_placeholder)
        binding.textCurrentBatsman.text = getString(R.string.current_batsman_placeholder)
        binding.textCurrentBowler.text = getString(R.string.current_bowler_placeholder)
    }

    private fun setTeamFieldsLocked(locked: Boolean) {
        binding.inputTeamA.isEnabled = !locked
        binding.inputTeamB.isEnabled = !locked
        if (locked) {
            binding.inputLayoutTeamA.helperText = getString(R.string.team_locked_message)
            binding.inputLayoutTeamB.helperText = getString(R.string.team_locked_message)
        } else {
            binding.inputLayoutTeamA.helperText = null
            binding.inputLayoutTeamB.helperText = null
        }
    }

    private fun setSubmitting(isSubmitting: Boolean) {
        binding.buttonSaveDraft.isEnabled = !isSubmitting
        binding.buttonPublishMatch.isEnabled = !isSubmitting
        binding.buttonSaveTeam.isEnabled = !isSubmitting
        binding.buttonAddPlayer.isEnabled = !isSubmitting
        binding.progressCreateMatch.visibility = if (isSubmitting) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerPlayers.adapter = null
        binding.recyclerLivePlayers.adapter = null
        _binding = null
    }

    companion object {
        private const val TEAM_SIZE = 11
        private const val STATUS_SQUADS_SAVED = "SQUADS_SAVED"
    }
}
