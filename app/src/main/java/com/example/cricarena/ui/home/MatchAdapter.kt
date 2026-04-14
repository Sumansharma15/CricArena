package com.example.cricarena.ui.home

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
            val context = binding.root.context
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

            if (item.status == MatchStatus.LIVE) {
                binding.textLiveBadge.visibility = View.VISIBLE
                if (!item.scoreSummary.isNullOrBlank()) {
                    binding.textTimeOrStatus.text = item.scoreSummary
                } else {
                    binding.textTimeOrStatus.text = context.getString(R.string.live_now)
                }
            } else if (item.status == MatchStatus.COMPLETED) {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = context.getString(R.string.completed_label)
            } else {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = context.getString(R.string.starts_at, item.startTime)
            }

            binding.buttonJoin.setOnClickListener { onJoinClick(item) }
        }
    }
}
