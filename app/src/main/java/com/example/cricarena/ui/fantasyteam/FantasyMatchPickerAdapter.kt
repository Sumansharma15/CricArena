package com.example.cricarena.ui.fantasyteam

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.Match
import com.example.cricarena.data.model.MatchStatus
import com.example.cricarena.databinding.ItemFantasyMatchPickBinding

class FantasyMatchPickerAdapter(
    private val onMatchClick: (Match) -> Unit
) : RecyclerView.Adapter<FantasyMatchPickerAdapter.VH>() {

    private val items = mutableListOf<Match>()

    fun submitList(matches: List<Match>) {
        items.clear()
        items.addAll(matches)
        Log.d("NAV_DEBUG", "FantasyMatchPickerAdapter submitList size=${items.size}")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFantasyMatchPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemFantasyMatchPickBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(match: Match) {
            Log.d("NAV_DEBUG", "Binding fantasy picker card matchId=${match.id}")
            val ctx = binding.root.context
            val title = match.title.ifBlank { ctx.getString(R.string.default_match_title, match.teamA, match.teamB) }
            binding.textMatchTitle.text = title
            val statusLabel = when (match.status) {
                MatchStatus.LIVE -> ctx.getString(R.string.live_label)
                MatchStatus.COMPLETED -> ctx.getString(R.string.completed_label)
                MatchStatus.UPCOMING -> ctx.getString(R.string.filter_upcoming)
            }
            binding.textMatchMeta.text = ctx.getString(
                R.string.fantasy_match_meta_format,
                match.matchType,
                statusLabel
            )
            if (!match.scoreSummary.isNullOrBlank()) {
                binding.textMatchScore.visibility = View.VISIBLE
                binding.textMatchScore.text = match.scoreSummary
            } else {
                binding.textMatchScore.visibility = View.GONE
            }
            binding.root.setOnClickListener { onMatchClick(match) }
        }
    }
}
