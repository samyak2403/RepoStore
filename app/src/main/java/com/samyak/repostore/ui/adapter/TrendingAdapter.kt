package com.samyak.repostore.ui.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.samyak.repostore.R
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.data.model.AppTag
import com.samyak.repostore.databinding.ItemTrendingAppBinding
import com.samyak.repostore.util.loadIconWithFallback
import com.samyak.repostore.util.loadRealAppName
import java.util.Locale

class TrendingAdapter(
    private val onItemClick: (AppItem) -> Unit
) : ListAdapter<AppItem, TrendingAdapter.TrendingViewHolder>(AppDiffCallback()) {

    private val screenshotFolders = listOf(
        "screenshots", "screenshot", "images", "image", "assets",
        "art", "media", "pics", "pictures", "img", "fastlane/metadata/android/en-US/images"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingViewHolder {
        val binding = ItemTrendingAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TrendingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrendingViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class TrendingViewHolder(
        private val binding: ItemTrendingAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: AppItem, position: Int) {
            val repo = item.repo
            val owner = repo.owner.login
            val repoName = repo.name
            val branch = repo.defaultBranch ?: "main"

            // Coloured gradient plus circles tinted to the same hue, used until
            // (or unless) a banner image loads for this repository.
            val palette = CardGradient.forPosition(position)
            binding.gradientBg.background = palette.gradient
            binding.gradientBg.visibility = View.VISIBLE
            binding.decorCircles.applyCircleTint(palette.circleTint)
            binding.decorCircles.visibility = View.VISIBLE
            binding.overlay.alpha = CardGradient.GRADIENT_OVERLAY_ALPHA
            binding.ivBanner.visibility = View.GONE

            // Try to load banner
            tryLoadBanner(owner, repoName, branch)

            binding.apply {
                tvAppName.loadRealAppName(repo)
                tvDeveloper.text = repo.owner.login
                tvStars.text = formatNumber(repo.stars)
                tvLanguage.text = repo.language ?: "Code"

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
                    AppTag.ARCHIVED -> {
                        chipTag.visibility = View.VISIBLE
                        chipTag.text = itemView.context.getString(R.string.tag_archived)
                        chipTag.setChipBackgroundColorResource(R.color.tag_archived)
                    }
                    null -> chipTag.visibility = View.GONE
                }

                // Load high-resolution icon with fallbacks
                ivAppIcon.loadIconWithFallback(item.iconUrls, repo.owner.avatarUrl)
            }
        }

        private fun tryLoadBanner(owner: String, repoName: String, branch: String) {
            val bannerNames = listOf(
                "banner.png", "banner.jpg", "banner.jpeg", "banner.webp",
                "feature.png", "feature.jpg", "feature_graphic.png", "feature_graphic.jpg",
                "header.png", "header.jpg", "cover.png", "cover.jpg"
            )

            val bannerUrls = mutableListOf<String>()
            for (folder in screenshotFolders) {
                for (name in bannerNames) {
                    bannerUrls.add("https://raw.githubusercontent.com/$owner/$repoName/$branch/$folder/$name")
                }
            }
            for (name in bannerNames) {
                bannerUrls.add("https://raw.githubusercontent.com/$owner/$repoName/$branch/$name")
            }

            loadBannerWithFallback(bannerUrls, 0)
        }

        private fun loadBannerWithFallback(urls: List<String>, index: Int) {
            if (index >= urls.size || index >= 10) return

            Glide.with(binding.ivBanner)
                .load(urls[index])
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        loadBannerWithFallback(urls, index + 1)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.ivBanner.visibility = View.VISIBLE
                        binding.gradientBg.visibility = View.GONE
                        binding.decorCircles.visibility = View.GONE
                        binding.overlay.alpha = 1f
                        return false
                    }
                })
                .into(binding.ivBanner)
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
