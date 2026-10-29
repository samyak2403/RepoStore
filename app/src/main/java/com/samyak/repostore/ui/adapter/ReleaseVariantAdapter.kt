package com.samyak.repostore.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.samyak.repostore.R
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.databinding.ItemReleaseVariantBinding
import com.samyak.repostore.util.ApkArchitectureHelper
import java.util.Locale

class ReleaseVariantAdapter(
    private val onAssetClicked: (ReleaseAsset) -> Unit
) : ListAdapter<ReleaseAsset, ReleaseVariantAdapter.ViewHolder>(AssetDiffCallback()) {

    private var recommendedAssetId: Long? = null
    private var selectedAssetId: Long? = null

    fun setRecommendedAssetId(id: Long?) {
        recommendedAssetId = id
        notifyDataSetChanged()
    }

    fun setSelectedAssetId(id: Long?) {
        selectedAssetId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReleaseVariantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemReleaseVariantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(asset: ReleaseAsset) {
            val variantTitle = ApkArchitectureHelper.getVariantTitle(asset.name)
            val variantArchDesc = ApkArchitectureHelper.getArchitectureDescription(asset.name)
            val isCompatible = ApkArchitectureHelper.isApkCompatible(asset.name)

            binding.tvVariantName.text = variantTitle
            binding.tvVariantFileName.text = asset.name
            binding.tvVariantSize.text = formatFileSize(asset.size)
            binding.tvVariantArch.text = variantArchDesc

            binding.tvCompatibility.apply {
                if (isCompatible) {
                    text = context.getString(R.string.compatible)
                    setTextColor(ContextCompat.getColor(context, R.color.primary))
                } else {
                    text = context.getString(R.string.incompatible)
                    setTextColor(ContextCompat.getColor(context, R.color.error))
                }
            }

            binding.tvRecommended.visibility = if (asset.id == recommendedAssetId) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            // Selected state: green check + primary stroke
            val isSelected = asset.id == selectedAssetId
            binding.ivSelectedCheck.visibility = if (isSelected) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

            val card = binding.cardVariant
            if (isSelected) {
                card.strokeColor = ContextCompat.getColor(card.context, R.color.primary)
                card.strokeWidth = card.context.resources.getDimensionPixelSize(R.dimen.selected_stroke_width)
            } else {
                val outlineColor = MaterialColors.getColor(
                    card,
                    com.google.android.material.R.attr.colorOutlineVariant,
                    Color.LTGRAY
                )
                card.strokeColor = outlineColor
                card.strokeWidth = (1.2f * card.context.resources.displayMetrics.density).toInt()
            }

            binding.root.setOnClickListener {
                onAssetClicked(asset)
            }
        }

        private fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(
                Locale.US,
                "%.1f %s",
                size / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }
    }

    private class AssetDiffCallback : DiffUtil.ItemCallback<ReleaseAsset>() {
        override fun areItemsTheSame(oldItem: ReleaseAsset, newItem: ReleaseAsset): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReleaseAsset, newItem: ReleaseAsset): Boolean {
            return oldItem == newItem
        }
    }
}
