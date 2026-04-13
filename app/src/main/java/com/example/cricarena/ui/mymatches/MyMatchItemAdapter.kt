package com.example.cricarena.ui.mymatches

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cricarena.R
import com.example.cricarena.data.model.MyMatchItem
import com.example.cricarena.databinding.ItemMyMatchBinding

class MyMatchItemAdapter(
    private val onItemClick: (MyMatchItem) -> Unit
) : RecyclerView.Adapter<MyMatchItemAdapter.MyMatchViewHolder>() {

    private val items = mutableListOf<MyMatchItem>()

    fun submitList(newItems: List<MyMatchItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyMatchViewHolder {
        val binding = ItemMyMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyMatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyMatchViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MyMatchViewHolder(
        private val binding: ItemMyMatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MyMatchItem) {
            binding.textMatchName.text = item.matchName
            binding.textMatchStatus.text = binding.root.context.getString(R.string.status_format, item.status)
            binding.textPoints.text = binding.root.context.getString(R.string.points_format, item.pointsEarned)
            binding.textRank.text = binding.root.context.getString(R.string.rank_format, item.rank)
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
