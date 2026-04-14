package com.example.cricarena.ui.creatematch

import android.content.Intent
import android.os.Bundle
import com.example.cricarena.R
import com.example.cricarena.base.BaseActivity
import com.example.cricarena.databinding.ActivityPlayerScoringBinding

class PlayerScoringActivity : BaseActivity<ActivityPlayerScoringBinding>() {

    override fun setupViewBinding(): ActivityPlayerScoringBinding =
        ActivityPlayerScoringBinding.inflate(layoutInflater)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME).orEmpty()
        val playerId = intent.getStringExtra(EXTRA_PLAYER_ID).orEmpty()
        val runs = intent.getIntExtra(EXTRA_CURRENT_RUNS, 0)
        val balls = intent.getIntExtra(EXTRA_CURRENT_BALLS, 0)
        binding.toolbarScoring.title = if (playerName.isBlank()) getString(R.string.live_scoring) else playerName
        binding.toolbarScoring.subtitle = getString(R.string.player_scoring_current_totals, runs, balls)
        binding.toolbarScoring.setNavigationOnClickListener { finish() }

        binding.buttonRun0.setOnClickListener { sendResult(playerId, ACTION_RUN, 0) }
        binding.buttonRun1.setOnClickListener { sendResult(playerId, ACTION_RUN, 1) }
        binding.buttonRun2.setOnClickListener { sendResult(playerId, ACTION_RUN, 2) }
        binding.buttonRun3.setOnClickListener { sendResult(playerId, ACTION_RUN, 3) }
        binding.buttonRun4.setOnClickListener { sendResult(playerId, ACTION_RUN, 4) }
        binding.buttonRun6.setOnClickListener { sendResult(playerId, ACTION_RUN, 6) }
        binding.buttonWide.setOnClickListener { sendResult(playerId, ACTION_WIDE) }
        binding.buttonNoBall.setOnClickListener { sendResult(playerId, ACTION_NO_BALL) }
        binding.buttonBye.setOnClickListener { sendResult(playerId, ACTION_BYE) }
        binding.buttonWicket.setOnClickListener { sendResult(playerId, ACTION_WICKET) }
        binding.buttonCatch.setOnClickListener { sendResult(playerId, ACTION_CATCH) }
    }

    private fun sendResult(playerId: String, action: String, runs: Int = 0) {
        val result = Intent().apply {
            putExtra(RESULT_PLAYER_ID, playerId)
            putExtra(RESULT_ACTION, action)
            putExtra(RESULT_RUNS, runs)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_PLAYER_ID = "extra_player_id"
        const val EXTRA_PLAYER_NAME = "extra_player_name"
        const val EXTRA_CURRENT_RUNS = "extra_current_runs"
        const val EXTRA_CURRENT_BALLS = "extra_current_balls"

        const val RESULT_PLAYER_ID = "result_player_id"
        const val RESULT_ACTION = "result_action"
        const val RESULT_RUNS = "result_runs"

        const val ACTION_RUN = "ACTION_RUN"
        const val ACTION_WIDE = "ACTION_WIDE"
        const val ACTION_NO_BALL = "ACTION_NO_BALL"
        const val ACTION_BYE = "ACTION_BYE"
        const val ACTION_WICKET = "ACTION_WICKET"
        const val ACTION_CATCH = "ACTION_CATCH"
    }
}
