package com.example.cricarena.ui.matchdetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.LeaderboardEntry
import com.example.cricarena.databinding.ItemLeaderboardBinding

class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    private val items = mutableListOf<LeaderboardEntry>()

    fun submitList(entries: List<LeaderboardEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class LeaderboardViewHolder(private val binding: ItemLeaderboardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LeaderboardEntry) {
            binding.textRank.text = binding.root.context.getString(R.string.rank_number, entry.rank)
            binding.textUserName.text = entry.userName
            binding.textPoints.text = binding.root.context.getString(R.string.points_format, entry.points)
        }
    }
}
