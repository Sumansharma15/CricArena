package com.example.cricarena.ui.creatematch

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.databinding.FragmentCreateMatchBinding
import com.example.cricarena.databinding.DialogAddPlayerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateMatchFragment : Fragment() {

    private var _binding: FragmentCreateMatchBinding? = null
    private val binding get() = _binding ?: error("Binding is only valid between onCreateView and onDestroyView.")
    private val viewModel: CreateMatchViewModel by viewModels()
    private val playerAdapter = PlayerAdapter()
    private val players = mutableListOf<MatchPlayer>()
    private val calendar: Calendar = Calendar.getInstance()
    private var selectedStartTimeMillis: Long = 0L
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateMatchBinding.inflate(inflater, container, false)
        setupMatchTypeDropdown()
        setupPlayersRecycler()
        setupStartDateTimePicker()
        setupActions()
        return binding.root
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
        binding.buttonSaveDraft.setOnClickListener { submitMatch("DRAFT") }
        binding.buttonPublishMatch.setOnClickListener { submitMatch("PUBLISHED") }
    }

    private fun showAddPlayerDialog() {
        val dialogBinding = DialogAddPlayerBinding.inflate(layoutInflater)
        val roleOptions = listOf(
            getString(R.string.role_batsman),
            getString(R.string.role_bowler),
            getString(R.string.role_all_rounder),
            getString(R.string.role_wicketkeeper)
        )
        val roleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, roleOptions)
        dialogBinding.inputPlayerRole.setAdapter(roleAdapter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_player)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val name = dialogBinding.inputPlayerName.text?.toString()?.trim().orEmpty()
                        val role = dialogBinding.inputPlayerRole.text?.toString()?.trim().orEmpty()
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

                        players.add(MatchPlayer(name, role))
                        playerAdapter.submitList(players.toList())
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun submitMatch(status: String) {
        val title = binding.inputMatchTitle.text?.toString()?.trim().orEmpty()
        val teamA = binding.inputTeamA.text?.toString()?.trim().orEmpty()
        val teamB = binding.inputTeamB.text?.toString()?.trim().orEmpty()
        val matchType = binding.inputMatchType.text?.toString()?.trim().orEmpty()
        val runs = binding.inputRuns.text?.toString()?.trim().orEmpty()
        val wickets = binding.inputWickets.text?.toString()?.trim().orEmpty()
        val catch = binding.inputCatch.text?.toString()?.trim().orEmpty()

        if (!validateForm(title, teamA, teamB, matchType, runs, wickets, catch)) return

        val playerPayload = players.map { mapOf("name" to it.name, "role" to it.role) }
        val scoringRules = mapOf(
            "runs" to runs.toInt(),
            "wickets" to wickets.toInt(),
            "catch" to catch.toInt()
        )

        setSubmitting(true)
        viewModel.saveMatch(
            title = title,
            teamA = teamA,
            teamB = teamB,
            matchType = matchType,
            startTimeMillis = selectedStartTimeMillis,
            players = playerPayload,
            scoringRules = scoringRules,
            status = status
        ) { success, message ->
            setSubmitting(false)
            if (success) {
                Toast.makeText(
                    requireContext(),
                    if (status == "DRAFT") getString(R.string.draft_saved) else getString(R.string.match_published),
                    Toast.LENGTH_SHORT
                ).show()
                clearForm()
            } else {
                Toast.makeText(requireContext(), message ?: getString(R.string.error_match_save), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun validateForm(
        title: String,
        teamA: String,
        teamB: String,
        matchType: String,
        runs: String,
        wickets: String,
        catch: String
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

        val runsPoints = runs.toIntOrNull()
        val wicketsPoints = wickets.toIntOrNull()
        val catchPoints = catch.toIntOrNull()
        if (runsPoints == null || wicketsPoints == null || catchPoints == null) {
            Toast.makeText(requireContext(), R.string.error_scoring_rules_invalid, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun clearForm() {
        binding.inputMatchTitle.text?.clear()
        binding.inputTeamA.text?.clear()
        binding.inputTeamB.text?.clear()
        binding.inputMatchType.text?.clear()
        binding.inputStartDateTime.text?.clear()
        binding.inputRuns.text?.clear()
        binding.inputWickets.text?.clear()
        binding.inputCatch.text?.clear()
        selectedStartTimeMillis = 0L
        players.clear()
        playerAdapter.submitList(emptyList())
    }

    private fun setSubmitting(isSubmitting: Boolean) {
        binding.buttonSaveDraft.isEnabled = !isSubmitting
        binding.buttonPublishMatch.isEnabled = !isSubmitting
        binding.buttonAddPlayer.isEnabled = !isSubmitting
        binding.progressCreateMatch.visibility = if (isSubmitting) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerPlayers.adapter = null
        _binding = null
    }
}
