package com.example.cricarena.ui.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchCategory
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.databinding.ItemMatchBinding

class MatchAdapter(
    private val onJoinClick: (Match) -> Unit
) : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

    private val items = mutableListOf<Match>()

    fun submitList(matches: List<Match>) {
        items.clear()
        items.addAll(matches)
        Log.d("HOME_UI", "MatchAdapter submitList size=${items.size}")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchViewHolder {
        val binding = ItemMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MatchViewHolder(private val binding: ItemMatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Match) {
            Log.d("HOME_UI", "Binding match card for id=${item.id}")
            val context = binding.root.context
            val now = System.currentTimeMillis()
            val lastUpdatedMs = item.lastUpdated?.toDate()?.time ?: 0L
            val recentlyUpdated = lastUpdatedMs > 0L && (now - lastUpdatedMs) <= LIVE_RECENT_WINDOW_MS
            binding.textTeams.text = context.getString(R.string.teams_vs, item.teamA, item.teamB)
            val categoryText = if (item.category == MatchCategory.MANUAL) {
                context.getString(R.string.match_category_manual)
            } else {
                context.getString(R.string.match_category_auto)
            }
            val title = item.title.ifBlank { context.getString(R.string.default_match_title, item.teamA, item.teamB) }
            binding.textMatchType.text = context.getString(
                R.string.match_card_title_format,
                title,
                item.matchType,
                categoryText
            )

            if (item.status == MatchStatus.LIVE || recentlyUpdated) {
                binding.textLiveBadge.visibility = View.VISIBLE
                if (!item.scoreSummary.isNullOrBlank()) {
                    binding.textTimeOrStatus.text = item.scoreSummary
                } else {
                    binding.textTimeOrStatus.text = context.getString(R.string.live_now)
                }
            } else if (item.status == MatchStatus.COMPLETED) {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = if (!item.scoreSummary.isNullOrBlank()) {
                    item.scoreSummary
                } else {
                    context.getString(R.string.completed_label)
                }
            } else {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = when {
                    !item.scoreSummary.isNullOrBlank() -> item.scoreSummary
                    else -> context.getString(R.string.starts_at, item.startTime)
                }
            }

            binding.buttonJoin.setOnClickListener {
                Log.d("NAV_DEBUG", "Join clicked from adapter for matchId=${item.id}")
                onJoinClick(item)
            }
            binding.root.setOnClickListener {
                Log.d("NAV_DEBUG", "Card clicked from adapter for matchId=${item.id}")
                onJoinClick(item)
            }
        }
    }

    companion object {
        private const val LIVE_RECENT_WINDOW_MS = 30_000L
    }
}
