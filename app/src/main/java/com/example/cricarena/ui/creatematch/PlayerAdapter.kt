package com.example.cricarena.ui.creatematch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.databinding.ItemPlayerBinding

class PlayerAdapter : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    private val players = mutableListOf<MatchPlayer>()

    fun submitList(newPlayers: List<MatchPlayer>) {
        players.clear()
        players.addAll(newPlayers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position], position + 1)
    }

    override fun getItemCount(): Int = players.size

    class PlayerViewHolder(
        private val binding: ItemPlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(player: MatchPlayer, serial: Int) {
            binding.textPlayerName.text = "$serial. ${player.name}"
            binding.textPlayerRole.text = player.role
        }
    }
}
