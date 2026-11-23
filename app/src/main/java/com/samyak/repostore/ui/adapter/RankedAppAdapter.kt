package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.AppTag
import com.samyak.repostore.databinding.ItemAppListRowBinding
import com.samyak.repostore.util.loadIconWithFallback
import com.samyak.repostore.util.loadRealAppName
import java.util.Locale

class RankedAppAdapter(
    private val onItemClick: (AppItem) -> Unit
) : ListAdapter<AppItem, RankedAppAdapter.AppViewHolder>(AppDiffCallback()) {

    companion object {
        const val PAYLOAD_RANK = "payload_rank"
    }

    private var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    override fun onCurrentListChanged(previousList: List<AppItem>, currentList: List<AppItem>) {
        super.onCurrentListChanged(previousList, currentList)
        if (previousList.isNotEmpty() && currentList.isNotEmpty()) {
            attachedRecyclerView?.let { rv ->
                if (rv.isComputingLayout) {
                    rv.post {
                        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_RANK)
                    }
                } else {
                    notifyItemRangeChanged(0, currentList.size, PAYLOAD_RANK)
                }
            } ?: notifyItemRangeChanged(0, currentList.size, PAYLOAD_RANK)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppListRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_RANK)) {
            holder.updateRank(position + 1)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class AppViewHolder(
        val binding: ItemAppListRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun updateRank(rank: Int) {
            binding.tvRank.text = rank.toString()
        }

        fun bind(item: AppItem, rank: Int) {
            val repo = item.repo

            binding.apply {
                tvRank.text = rank.toString()
                tvAppName.loadRealAppName(repo)
                tvDeveloper.text = repo.owner.login

                // Show actual GitHub stars count
                tvStars.text = formatNumber(repo.stars)
                tvSize.text = repo.language ?: "Code"

                // Tag
                when (item.tag) {
                    AppTag.NEW -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_new)
                        chipTag.setChipBackgroundColorResource(R.color.tag_new)
                    }
                    AppTag.UPDATED -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_updated)
                        chipTag.setChipBackgroundColorResource(R.color.tag_updated)
                    }
                    else -> chipTag.visibility = View.GONE
                }

                // Load high-resolution icon with fallbacks
                ivAppIcon.loadIconWithFallback(item.iconUrls, repo.owner.avatarUrl)
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

    class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem.repo.id == newItem.repo.id

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem) =
            oldItem == newItem
    }
}
