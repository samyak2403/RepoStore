package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ItemAppUpdateBinding
import com.samyak.repostore.ui.viewmodel.AppUpdate
import com.samyak.repostore.util.ApkArchitectureHelper
import java.util.Locale

class AppUpdateAdapter(
    private val onUpdateClick: (AppUpdate) -> Unit,
    private val onItemClick: (AppUpdate) -> Unit
) : ListAdapter<AppUpdate, AppUpdateAdapter.UpdateViewHolder>(DiffCallback()) {

    /** Per-package download progress state, keyed by packageName. */
    sealed class ItemState {
        data object Idle : ItemState()
        data class Downloading(val progress: Int) : ItemState()
        data object Installing : ItemState()
    }

    private val states = mutableMapOf<String, ItemState>()

    fun setItemState(packageName: String, state: ItemState) {
        states[packageName] = state
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != RecyclerView.NO_POSITION && index >= 0) {
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdateViewHolder {
        val binding = ItemAppUpdateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UpdateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UpdateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UpdateViewHolder(
        private val binding: ItemAppUpdateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(getItem(pos))
            }
            binding.btnUpdate.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onUpdateClick(getItem(pos))
            }
        }

        fun bind(item: AppUpdate) {
            binding.tvAppName.text = item.appName
            binding.tvVersionDiff.text = "${item.installedVersion} → ${item.latestVersion}"
            val arch = ApkArchitectureHelper.getShortArchitectureName(item.asset.name)
            binding.tvAppSize.text = "${formatSize(item.sizeBytes)} • $arch"

            // Load the installed app's real launcher icon, fall back to placeholder.
            try {
                val icon = binding.root.context.packageManager.getApplicationIcon(item.packageName)
                binding.ivAppIcon.setImageDrawable(icon)
            } catch (e: Exception) {
                binding.ivAppIcon.setImageResource(R.drawable.ic_app_placeholder)
            }

            when (val state = states[item.packageName] ?: ItemState.Idle) {
                is ItemState.Idle -> {
                    binding.progressUpdate.visibility = View.GONE
                    binding.btnUpdate.visibility = View.VISIBLE
                    binding.btnUpdate.isEnabled = true
                    binding.btnUpdate.text = binding.root.context.getString(R.string.update)
                }
                is ItemState.Downloading -> {
                    binding.progressUpdate.visibility = View.GONE
                    binding.btnUpdate.visibility = View.VISIBLE
                    binding.btnUpdate.isEnabled = false
                    binding.btnUpdate.text = "${state.progress}%"
                }
                is ItemState.Installing -> {
                    binding.btnUpdate.visibility = View.GONE
                    binding.progressUpdate.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppUpdate>() {
        override fun areItemsTheSame(oldItem: AppUpdate, newItem: AppUpdate) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppUpdate, newItem: AppUpdate) =
            oldItem == newItem
    }
}
