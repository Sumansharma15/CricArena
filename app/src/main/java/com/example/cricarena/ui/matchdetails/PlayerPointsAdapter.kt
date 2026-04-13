package com.example.cricarena.ui.matchdetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.PlayerPointsEntry
import com.example.cricarena.databinding.ItemPlayerPointsBinding

class PlayerPointsAdapter : RecyclerView.Adapter<PlayerPointsAdapter.PlayerPointsViewHolder>() {

    private val items = mutableListOf<PlayerPointsEntry>()

    fun submitList(entries: List<PlayerPointsEntry>) {
        items.clear()
        items.addAll(entries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerPointsViewHolder {
        val binding = ItemPlayerPointsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerPointsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerPointsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class PlayerPointsViewHolder(private val binding: ItemPlayerPointsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: PlayerPointsEntry) {
            binding.textPlayerName.text = entry.playerName
            binding.textPoints.text = binding.root.context.getString(R.string.points_format, entry.points)
        }
    }
}
