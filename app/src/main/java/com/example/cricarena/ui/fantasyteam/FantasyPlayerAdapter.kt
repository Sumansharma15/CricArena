package com.example.cricarena.ui.fantasyteam

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.FantasyPlayer
import com.example.cricarena.databinding.ItemFantasyPlayerBinding

class FantasyPlayerAdapter(
    private val onPlayerToggle: (FantasyPlayer) -> Unit,
    private val onCaptainClick: (FantasyPlayer) -> Unit,
    private val onViceCaptainClick: (FantasyPlayer) -> Unit
) : RecyclerView.Adapter<FantasyPlayerAdapter.FantasyPlayerViewHolder>() {

    private val players = mutableListOf<FantasyPlayer>()
    private val selectedIds = mutableSetOf<String>()
    private var captainId: String? = null
    private var viceCaptainId: String? = null

    fun submitPlayers(
        items: List<FantasyPlayer>,
        selected: Set<String>,
        captain: String?,
        viceCaptain: String?
    ) {
        players.clear()
        players.addAll(items)
        selectedIds.clear()
        selectedIds.addAll(selected)
        captainId = captain
        viceCaptainId = viceCaptain
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FantasyPlayerViewHolder {
        val binding = ItemFantasyPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FantasyPlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FantasyPlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount(): Int = players.size

    inner class FantasyPlayerViewHolder(
        private val binding: ItemFantasyPlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(player: FantasyPlayer) {
            val context = binding.root.context
            val isSelected = selectedIds.contains(player.id)
            val isCaptain = captainId == player.id
            val isViceCaptain = viceCaptainId == player.id

            binding.textPlayerName.text = player.name
            binding.textPlayerRole.text = player.role
            val roleIcon = when (player.role.lowercase()) {
                "batsman" -> R.drawable.ic_role_bat
                "bowler" -> R.drawable.ic_role_ball
                "wicketkeeper" -> R.drawable.ic_role_gloves
                else -> R.drawable.ic_role_allrounder
            }
            binding.textPlayerRole.setCompoundDrawablesRelativeWithIntrinsicBounds(roleIcon, 0, 0, 0)
            binding.textSelectedState.text =
                if (isSelected) context.getString(R.string.selected_label) else context.getString(R.string.tap_to_select)

            binding.cardPlayer.strokeWidth = if (isSelected) 3 else 1
            binding.cardPlayer.strokeColor =
                context.getColor(if (isSelected) R.color.primary else R.color.background)

            binding.buttonCaptain.text = if (isCaptain) {
                context.getString(R.string.captain_selected)
            } else {
                context.getString(R.string.set_captain)
            }
            binding.buttonViceCaptain.text = if (isViceCaptain) {
                context.getString(R.string.vice_captain_selected)
            } else {
                context.getString(R.string.set_vice_captain)
            }

            binding.cardPlayer.setOnClickListener { onPlayerToggle(player) }
            binding.buttonCaptain.setOnClickListener { onCaptainClick(player) }
            binding.buttonViceCaptain.setOnClickListener { onViceCaptainClick(player) }
        }
    }
}
