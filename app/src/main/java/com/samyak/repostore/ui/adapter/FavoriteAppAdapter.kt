package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.databinding.ItemAppListRowBinding
import java.util.Locale

class FavoriteAppAdapter(
    private val onItemClick: (FavoriteApp) -> Unit,
    private val onDeveloperClick: ((String, String) -> Unit)? = null
) : ListAdapter<FavoriteApp, FavoriteAppAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemAppListRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class FavoriteViewHolder(
        private val binding: ItemAppListRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: FavoriteApp, rank: Int) {
            binding.apply {
                tvRank.text = rank.toString()
                tvAppName.text = item.name
                tvDeveloper.text = item.ownerLogin

                // Developer click listener
                tvDeveloper.setOnClickListener {
                    onDeveloperClick?.invoke(item.ownerLogin, item.ownerAvatarUrl)
                }

                // Show stars count
                tvStars.text = formatNumber(item.stars)
                tvSize.text = item.language ?: "Code"

                // Hide tag for favorites
                chipTag.visibility = View.GONE

                Glide.with(ivAppIcon)
                    .load(item.ownerAvatarUrl)
                    .placeholder(R.drawable.ic_app_placeholder)
                    .into(ivAppIcon)
            }
        }

        private fun formatNumber(number: Int): String {
            return when {
                number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
                number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
                else -> number.toString()
            }
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteApp>() {
        override fun areItemsTheSame(oldItem: FavoriteApp, newItem: FavoriteApp) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FavoriteApp, newItem: FavoriteApp) =
            oldItem == newItem
    }
}
