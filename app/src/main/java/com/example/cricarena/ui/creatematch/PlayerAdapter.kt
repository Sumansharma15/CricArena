package com.example.cricarena.ui.creatematch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.MatchPlayer
import com.example.cricarena.databinding.ItemPlayerBinding

class PlayerAdapter(
    private val onEditClick: (position: Int, player: MatchPlayer) -> Unit,
    private val onDeleteClick: (position: Int, player: MatchPlayer) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

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

    override fun getItemCount(): Int = players.size

    class PlayerViewHolder(
        private val binding: ItemPlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            player: MatchPlayer,
            serial: Int,
            onEditClick: (position: Int, player: MatchPlayer) -> Unit,
            onDeleteClick: (position: Int, player: MatchPlayer) -> Unit
        ) {
            binding.textPlayerName.text = "$serial. ${player.name}"
            binding.textPlayerRole.text = player.role
            binding.textPlayerTeam.text = player.teamName
            val iconRes = when (player.role.lowercase()) {
                "batsman" -> R.drawable.ic_role_bat
                "bowler" -> R.drawable.ic_role_ball
                "wicketkeeper" -> R.drawable.ic_role_gloves
                else -> R.drawable.ic_role_allrounder
            }
            binding.imageRoleIcon.setImageResource(iconRes)
            binding.buttonEditPlayer.setOnClickListener {
                onEditClick(bindingAdapterPosition, player)
            }
            binding.buttonDeletePlayer.setOnClickListener {
                onDeleteClick(bindingAdapterPosition, player)
            }
        }
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(
            players[position],
            position + 1,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick
        )
    }
}
