package com.example.cricarena.ui.matchdetails

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.cricarena.R
import com.example.cricarena.base.BaseActivity
import com.example.cricarena.data.model.LeaderboardEntry
import com.example.cricarena.data.model.PlayerPointsEntry
import com.example.cricarena.data.scoring.FantasyScoringManager
import com.example.cricarena.databinding.ActivityMatchDetailsBinding
import com.example.cricarena.util.FirebaseUtils
import com.google.firebase.firestore.ListenerRegistration

class MatchDetailsActivity : BaseActivity<ActivityMatchDetailsBinding>() {

    private val leaderboardAdapter = LeaderboardAdapter()
    private val playerPointsAdapter = PlayerPointsAdapter()
    private val scoringManager = FantasyScoringManager()
    private var leaderboardListener: ListenerRegistration? = null
    private var playerPointsListener: ListenerRegistration? = null

    override fun setupViewBinding(): ActivityMatchDetailsBinding =
        ActivityMatchDetailsBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID).orEmpty()
        val matchName = intent.getStringExtra(EXTRA_MATCH_NAME).orEmpty()
        if (matchId.isBlank()) {
            Toast.makeText(this, R.string.error_match_id_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.toolbarDetails.title = matchName.ifBlank { getString(R.string.match_details) }
        binding.toolbarDetails.setNavigationOnClickListener { finish() }
        binding.recyclerLeaderboard.adapter = leaderboardAdapter
        binding.recyclerPlayerPoints.adapter = playerPointsAdapter
        recomputeAndFetch(matchId)
    }

    private fun recomputeAndFetch(matchId: String) {
        setLoading(true)
        scoringManager.calculateAndStoreResults(matchId) { _, error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
            fetchDetails(matchId)
        }
    }

    private fun fetchDetails(matchId: String) {
        fetchLeaderboard(matchId)
        fetchPlayerPoints(matchId)
    }

    private fun fetchLeaderboard(matchId: String) {
        leaderboardListener?.remove()
        leaderboardListener = FirebaseUtils.teamsCollection()
            .whereEqualTo("matchId", matchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    setLoading(false)
                    Toast.makeText(this, error.localizedMessage ?: getString(R.string.error_fetch_leaderboard), Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                val safeSnapshot = snapshot ?: return@addSnapshotListener
                val entries = safeSnapshot.documents
                    .map {
                        LeaderboardEntry(
                            userName = it.getString("userName").orEmpty().ifBlank { "User" },
                            points = it.getDouble("totalPoints") ?: 0.0,
                            rank = (it.getLong("rank") ?: 0L).toInt()
                        )
                    }
                    .sortedBy { if (it.rank == 0) Int.MAX_VALUE else it.rank }
                leaderboardAdapter.submitList(entries)
                binding.textLeaderboardEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                setLoading(false)
            }
    }

    private fun fetchPlayerPoints(matchId: String) {
        playerPointsListener?.remove()
        playerPointsListener = FirebaseUtils.matchesCollection()
            .document(matchId)
            .collection("playerPoints")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    binding.textPlayerPointsEmpty.visibility = View.VISIBLE
                    Toast.makeText(this, error.localizedMessage ?: getString(R.string.error_fetch_leaderboard), Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                val safeSnapshot = snapshot ?: return@addSnapshotListener
                val entries = safeSnapshot.documents.map {
                    PlayerPointsEntry(
                        playerName = it.getString("playerName").orEmpty().ifBlank { "Player" },
                        points = it.getDouble("points") ?: 0.0
                    )
                }.sortedByDescending { it.points }
                playerPointsAdapter.submitList(entries)
                binding.textPlayerPointsEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressDetails.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_MATCH_ID = "extra_match_id"
        const val EXTRA_MATCH_NAME = "extra_match_name"
    }

    override fun onDestroy() {
        leaderboardListener?.remove()
        playerPointsListener?.remove()
        leaderboardListener = null
        playerPointsListener = null
        super.onDestroy()
    }
}
