package com.example.cricarena.ui.creatematch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.LivePlayer
import com.example.cricarena.databinding.ItemLivePlayerBinding

class LivePlayerAdapter(
    private val onClickPlayer: (LivePlayer) -> Unit
) : RecyclerView.Adapter<LivePlayerAdapter.LivePlayerViewHolder>() {

    private val players = mutableListOf<LivePlayer>()

    fun submitList(items: List<LivePlayer>) {
        players.clear()
        players.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LivePlayerViewHolder {
        val binding = ItemLivePlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LivePlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LivePlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount(): Int = players.size

    inner class LivePlayerViewHolder(
        private val binding: ItemLivePlayerBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LivePlayer) {
            binding.textPlayerName.text = item.name
            binding.textPlayerRole.text = item.role
            binding.textRuns.text = binding.root.context.getString(R.string.live_runs_format, item.runs)
            binding.textBalls.text = binding.root.context.getString(R.string.live_balls_format, item.balls)
            val strikeRate = if (item.balls == 0) 0.0 else (item.runs.toDouble() / item.balls.toDouble()) * 100.0
            binding.textStrikeRate.text = binding.root.context.getString(R.string.live_sr_format, strikeRate)
            binding.textOutState.text = if (item.isOut) {
                binding.root.context.getString(R.string.live_out)
            } else {
                binding.root.context.getString(R.string.live_not_out)
            }
            binding.cardLivePlayer.setOnClickListener { onClickPlayer(item) }
        }
    }
}
