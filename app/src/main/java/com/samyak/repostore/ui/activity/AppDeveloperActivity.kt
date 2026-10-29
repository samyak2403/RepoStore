package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.model.GitHubUser
import com.samyak.repostore.databinding.ActivityAppDeveloperBinding
import com.samyak.repostore.util.RateLimitDialog
import kotlinx.coroutines.launch

class AppDeveloperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDeveloperBinding
    private var githubUser: GitHubUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAppDeveloperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        loadDeveloperInfo()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadDeveloperInfo() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        
        lifecycleScope.launch {
            val app = application as RepoStoreApp
            val repository = app.repository
            
            val userResult = repository.getUser(DEVELOPER_USERNAME)
            val contributorsResult = repository.getContributors(DEVELOPER_USERNAME, "RepoStore")
            
            binding.progressBar.visibility = View.GONE
            
            userResult.fold(
                onSuccess = { user ->
                    githubUser = user
                    displayUserInfo(user)
                    displayContributors(contributorsResult.getOrDefault(emptyList()))
                },
                onFailure = { error ->
                    binding.tvError.visibility = View.VISIBLE
                    val errorMsg = error.message ?: getString(R.string.network_error)
                    binding.tvError.text = "$errorMsg\n\n${getString(R.string.tap_to_retry)}"
                    binding.tvError.setOnClickListener {
                        loadDeveloperInfo()
                    }
                    RateLimitDialog.showIfNeeded(this@AppDeveloperActivity, errorMsg)
                }
            )
        }
    }

    private fun displayUserInfo(user: GitHubUser) {
        binding.progressBar.visibility = View.GONE
        
        Glide.with(this)
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_app_placeholder)
            .into(binding.ivAvatar)

        binding.tvName.text = user.name ?: user.login
        binding.tvUsername.text = getString(R.string.username_format, user.login)
        
        if (!user.bio.isNullOrEmpty()) {
            binding.tvBio.text = user.bio
            binding.tvBio.visibility = View.VISIBLE
        }

        binding.tvReposCount.text = user.publicRepos.toString()
        binding.tvFollowersCount.text = user.followers.toString()
        binding.tvFollowingCount.text = user.following.toString()

        if (!user.location.isNullOrEmpty()) {
            binding.layoutLocation.visibility = View.VISIBLE
            binding.tvLocation.text = user.location
        }

        if (!user.company.isNullOrEmpty()) {
            binding.layoutCompany.visibility = View.VISIBLE
            binding.tvCompany.text = user.company
        }

        if (!user.blog.isNullOrEmpty()) {
            binding.layoutBlog.visibility = View.VISIBLE
            binding.tvBlog.text = user.blog
            binding.layoutBlog.setOnClickListener {
                val url = if (user.blog.startsWith("http")) user.blog else "https://${user.blog}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        binding.btnViewGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(user.htmlUrl)))
        }
    }

    private fun displayContributors(contributors: List<GitHubUser>) {
        if (contributors.isEmpty()) {
            binding.layoutContributors.visibility = View.GONE
            return
        }
        
        binding.layoutContributors.visibility = View.VISIBLE
        binding.containerContributors.removeAllViews()
        
        val maxToShow = 6
        val toShow = contributors.take(maxToShow)
        
        for (contributor in toShow) {
            val avatarView = layoutInflater.inflate(R.layout.item_contributor_avatar, binding.containerContributors, false)
            val imageView = avatarView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.iv_contributor_avatar)
            
            Glide.with(this)
                .load(contributor.avatarUrl)
                .placeholder(R.drawable.ic_app_placeholder)
                .into(imageView)
                
            avatarView.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contributor.htmlUrl)))
            }
            
            binding.containerContributors.addView(avatarView)
        }
        
        if (contributors.size > maxToShow) {
            val remaining = contributors.size - maxToShow
            val moreText = android.widget.TextView(this).apply {
                text = "+$remaining"
                textSize = 14f
                setPadding(24, 0, 24, 0)
                gravity = android.view.Gravity.CENTER

                // Fetch dynamic text and background color programmatically using Material variables
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                setTextColor(typedValue.data)

                // Make the overflow label look and behave like a button.
                val ripple = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, ripple, true
                )
                setBackgroundResource(ripple.resourceId)
                isClickable = true
                isFocusable = true

                // Add fixed height
                val heightPx = (40f * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    heightPx
                )

                setOnClickListener { showAllContributors(contributors) }
            }
            binding.containerContributors.addView(moreText)
        }
    }

    /** Shows every contributor in a scrollable bottom sheet, each opening their GitHub profile. */
    private fun showAllContributors(contributors: List<GitHubUser>) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        fun themeColor(attr: Int): Int {
            val tv = android.util.TypedValue()
            theme.resolveAttribute(attr, tv, true)
            return tv.data
        }

        val list = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        list.addView(android.widget.TextView(this).apply {
            text = getString(R.string.contributors)
            textSize = 18f
            setPadding(dp(8), dp(4), dp(8), dp(12))
            setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
        })

        for (contributor in contributors) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = true
                isFocusable = true
                val ripple = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contributor.htmlUrl)))
                }
            }

            val avatar = com.google.android.material.imageview.ShapeableImageView(this).apply {
                val size = dp(40)
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(size / 2f)
                    .build()
            }
            Glide.with(this)
                .load(contributor.avatarUrl)
                .placeholder(R.drawable.ic_app_placeholder)
                .into(avatar)

            val name = android.widget.TextView(this).apply {
                text = contributor.name ?: contributor.login
                textSize = 16f
                setPadding(dp(16), 0, 0, 0)
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface))
            }

            row.addView(avatar)
            row.addView(name)
            list.addView(row)
        }

        val scroll = androidx.core.widget.NestedScrollView(this).apply { addView(list) }

        com.google.android.material.bottomsheet.BottomSheetDialog(this).apply {
            setContentView(scroll)
        }.show()
    }

    companion object {
        private const val DEVELOPER_USERNAME = "samyak2403"

        fun newIntent(context: Context): Intent {
            return Intent(context, AppDeveloperActivity::class.java)
        }
    }
}
