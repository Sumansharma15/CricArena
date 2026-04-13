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

class MatchAdapter : RecyclerView.Adapter<MatchAdapter.MatchViewHolder>() {

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
            binding.textMatchType.text = context.getString(R.string.match_type_format, item.matchType, categoryText)

            if (item.status == MatchStatus.LIVE) {
                binding.textLiveBadge.visibility = View.VISIBLE
                binding.textTimeOrStatus.text = context.getString(R.string.live_now)
            } else if (item.status == MatchStatus.COMPLETED) {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = context.getString(R.string.completed_label)
            } else {
                binding.textLiveBadge.visibility = View.GONE
                binding.textTimeOrStatus.text = context.getString(R.string.starts_at, item.startTime)
            }
        }
    }
}
