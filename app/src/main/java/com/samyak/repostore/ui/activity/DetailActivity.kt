package com.samyak.repostore.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.db.FavoriteAppDao
import com.samyak.repostore.data.model.FavoriteApp
import com.samyak.repostore.data.model.GitHubRelease
import com.samyak.repostore.data.model.GitHubRepo
import com.samyak.repostore.data.model.ReleaseAsset
import com.samyak.repostore.databinding.ActivityDetailBinding
import com.samyak.repostore.ui.adapter.ScreenshotAdapter
import com.samyak.repostore.ui.viewmodel.DetailUiState
import com.samyak.repostore.ui.viewmodel.DetailViewModel
import com.samyak.repostore.ui.viewmodel.DetailViewModelFactory
import com.samyak.repostore.util.AppInstaller
import com.samyak.repostore.util.ApkArchitectureHelper
import com.samyak.repostore.ui.widget.ShimmerFrameLayout
import com.samyak.repostore.util.ApkSelectionResult
import com.samyak.repostore.util.RateLimitDialog
import com.samyak.repostore.util.VersionComparator
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var markwon: Markwon

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModelFactory((application as RepoStoreApp).repository)
    }

    private lateinit var screenshotAdapter: ScreenshotAdapter
    private lateinit var appInstaller: AppInstaller
    private lateinit var favoriteAppDao: FavoriteAppDao
    
    private var owner: String = ""
    private var repoName: String = ""
    private var currentApkAsset: ReleaseAsset? = null
    private var installedPackageName: String? = null
    private var currentRepo: GitHubRepo? = null
    private var currentReleaseTag: String? = null
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    private val packageInstallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Check if this app was installed/updated
            checkInstalledState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        owner = intent.getStringExtra(EXTRA_OWNER) ?: ""
        repoName = intent.getStringExtra(EXTRA_REPO) ?: ""

        if (owner.isEmpty() || repoName.isEmpty()) {
            finish()
            return
        }

        appInstaller = AppInstaller.getInstance(this)
        favoriteAppDao = (application as RepoStoreApp).favoriteAppDao
        
        // Initialize shimmer layout
        shimmerLayout = findViewById(R.id.skeleton_layout)

        // Register for package install/uninstall events
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // For Android 13+, use RECEIVER_EXPORTED for system broadcasts
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageInstallReceiver, packageFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(packageInstallReceiver, packageFilter)
        }

        setupMarkwon()
        setupToolbar()
        setupScreenshotsRecyclerView()
        observeViewModel()
        viewModel.loadAppDetails(owner, repoName)
    }

    override fun onResume() {
        super.onResume()
        // Check installed state when returning to activity
        checkInstalledState()
    }

    override fun onDestroy() {
        super.onDestroy()
        appInstaller.cancel()
        shimmerLayout = null
        try {
            unregisterReceiver(packageInstallReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }

    private fun setupMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(GlideImagesPlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .build()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private var currentScreenshots: List<String> = emptyList()

    private fun setupScreenshotsRecyclerView() {
        screenshotAdapter = ScreenshotAdapter { _, position ->
            openScreenshotViewer(position)
        }

        binding.rvScreenshots.apply {
            adapter = screenshotAdapter
            layoutManager = LinearLayoutManager(this@DetailActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun openScreenshotViewer(position: Int) {
        if (currentScreenshots.isNotEmpty()) {
            val intent = ScreenshotViewerActivity.newIntent(
                this,
                ArrayList(currentScreenshots),
                position
            )
            startActivity(intent)
            overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                launch {
                    viewModel.readme.collect { readme ->
                        readme?.let {
                            // Render markdown
                            markwon.setMarkdown(binding.tvReadme, it)
                            binding.cardReadme.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    viewModel.screenshots.collect { screenshots ->
                        if (screenshots.isNotEmpty()) {
                            currentScreenshots = screenshots
                            binding.layoutScreenshots.visibility = View.VISIBLE
                            screenshotAdapter.submitList(screenshots)
                        } else {
                            currentScreenshots = emptyList()
                            binding.layoutScreenshots.visibility = View.GONE
                        }
                    }
                }
            }
        }
        
        // Observe favorite state
        observeFavoriteState()
    }
    
    private fun observeFavoriteState() {
        // We'll start observing after we have the repo ID
    }

    private fun handleUiState(state: DetailUiState) {
        when (state) {
            is DetailUiState.Loading -> {
                showSkeleton()
                binding.scrollContent.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }
            is DetailUiState.Success -> {
                hideSkeleton()
                binding.scrollContent.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                bindRepoData(state.repo, state.release)
            }
            is DetailUiState.Error -> {
                hideSkeleton()
                binding.scrollContent.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "${state.message}\n\n${getString(R.string.tap_to_retry)}"
                binding.tvError.setOnClickListener {
                    viewModel.retry(owner, repoName)
                }
                
                // Show rate limit dialog if applicable
                RateLimitDialog.showIfNeeded(this, state.message)
            }
        }
    }
    
    private fun showSkeleton() {
        shimmerLayout?.apply {
            visibility = View.VISIBLE
            startShimmer()
        }
    }
    
    private fun hideSkeleton() {
        shimmerLayout?.apply {
            stopShimmer()
            visibility = View.GONE
        }
    }
    
    private fun setupFavoriteButton(repo: GitHubRepo) {
        // Observe favorite state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoriteAppDao.isFavorite(repo.id).collect { isFavorite ->
                    updateFavoriteIcon(isFavorite)
                }
            }
        }
        
        // Set click listener for favorite button
        binding.ivFavorite.setOnClickListener {
            lifecycleScope.launch {
                val isFavorite = favoriteAppDao.isFavoriteSync(repo.id)
                if (isFavorite) {
                    favoriteAppDao.removeFavorite(repo.id)
                    Toast.makeText(this@DetailActivity, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
                } else {
                    val favoriteApp = FavoriteApp.fromRepo(repo)
                    favoriteAppDao.addFavorite(favoriteApp)
                    Toast.makeText(this@DetailActivity, R.string.added_to_favorites, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateFavoriteIcon(isFavorite: Boolean) {
        if (isFavorite) {
            binding.ivFavorite.setImageResource(R.drawable.ic_favorite)
            binding.ivFavorite.imageTintList = getColorStateList(R.color.favorite_active)
            binding.ivFavorite.contentDescription = getString(R.string.remove_from_favorites)
        } else {
            binding.ivFavorite.setImageResource(R.drawable.ic_favorite_border)
            binding.ivFavorite.imageTintList = null
            binding.ivFavorite.contentDescription = getString(R.string.add_to_favorites)
        }
    }

    private fun bindRepoData(repo: GitHubRepo, release: GitHubRelease?) {
        // Store current repo for favorite functionality
        currentRepo = repo
        
        binding.apply {
            tvAppName.text = repo.name
            tvDeveloper.text = repo.owner.login
            tvDescription.text = repo.description ?: getString(R.string.no_description)

            // Developer click - open developer page
            tvDeveloper.setOnClickListener {
                val intent = DeveloperActivity.newIntent(
                    this@DetailActivity,
                    repo.owner.login,
                    repo.owner.avatarUrl
                )
                startActivity(intent)
            }
            
            // Setup favorite button
            setupFavoriteButton(repo)

            // Stats
            tvStars.text = formatNumber(repo.stars)
            tvForks.text = formatNumber(repo.forks)
            tvLanguage.text = repo.language ?: "Code"
            tvUpdated.text = formatDate(repo.updatedAt)

            // Load avatar
            Glide.with(this@DetailActivity)
                .load(repo.owner.avatarUrl)
                .placeholder(R.drawable.ic_app_placeholder)
                .into(ivAppIcon)

            // Icon click - open icon viewer
            ivAppIcon.setOnClickListener {
                val intent = IconViewerActivity.newIntent(
                    this@DetailActivity,
                    repo.owner.avatarUrl,
                    repo.name
                )
                startActivity(intent)
                overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
            }

            // Topics as chips
            if (!repo.topics.isNullOrEmpty()) {
                chipGroupTopics.removeAllViews()
                repo.topics.take(6).forEach { topic ->
                    val chip = Chip(this@DetailActivity).apply {
                        text = topic
                        isClickable = false
                        setChipBackgroundColorResource(R.color.chip_background)
                    }
                    chipGroupTopics.addView(chip)
                }
                chipGroupTopics.visibility = View.VISIBLE
            } else {
                chipGroupTopics.visibility = View.GONE
            }

            // Archived badge
            chipArchived.visibility = if (repo.archived) View.VISIBLE else View.GONE

            // Release info
            if (release != null) {
                cardRelease.visibility = View.VISIBLE
                tvVersion.text = release.tagName
                tvReleaseName.text = release.name ?: release.tagName
                
                // Store release tag for version comparison
                currentReleaseTag = release.tagName
                
                // Render release notes as markdown
                val releaseNotes = release.body ?: getString(R.string.no_release_notes)
                markwon.setMarkdown(tvReleaseNotes, releaseNotes)
                
                tvReleaseDate.text = formatDate(release.publishedAt)

                // Find best APK for device architecture (arm64-v8a, armeabi-v7a, x86_64, x86)
                when (val selection = ApkArchitectureHelper.selectBestApk(release.assets)) {
                    is ApkSelectionResult.NoApkFound -> {
                        Log.d(TAG, "No APK assets found")
                        currentApkAsset = null
                    }
                    is ApkSelectionResult.Single -> {
                        Log.d(TAG, "Single APK found: ${selection.asset.name}")
                        currentApkAsset = selection.asset
                    }
                    is ApkSelectionResult.ExactMatch -> {
                        Log.d(TAG, "Found exact match for ${selection.abi}: ${selection.asset.name}")
                        currentApkAsset = selection.asset
                    }
                    is ApkSelectionResult.Universal -> {
                        Log.d(TAG, "Found universal APK: ${selection.asset.name}")
                        currentApkAsset = selection.asset
                    }
                    is ApkSelectionResult.Fallback -> {
                        Log.d(TAG, "No architecture match, using first APK: ${selection.asset.name}")
                        currentApkAsset = selection.asset
                    }
                }

                if (currentApkAsset != null) {
                    setupInstallButton(repo.name, repo.owner.login)
                } else {
                    btnDownload.text = getString(R.string.view_release)
                    btnDownload.setOnClickListener {
                        openUrl(release.htmlUrl)
                    }
                }
            } else {
                cardRelease.visibility = View.GONE
                btnDownload.text = getString(R.string.view_on_github)
                btnDownload.setOnClickListener {
                    openUrl(repo.htmlUrl)
                }
            }

            // GitHub button
            btnGithub.setOnClickListener {
                openUrl(repo.htmlUrl)
            }
        }
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
            else -> NumberFormat.getInstance(Locale.US).format(number)
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoDate)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            date?.let { outputFormat.format(it) } ?: isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun startDownload(asset: ReleaseAsset) {
        // Disable button immediately
        binding.btnDownload.isEnabled = false
        binding.btnDownload.text = "0%"
        
        appInstaller.download(
            url = asset.downloadUrl,
            fileName = asset.name,
            title = repoName
        ) { state ->
            // Ensure UI updates run on main thread
            runOnUiThread {
                when (state) {
                    is AppInstaller.InstallState.Idle -> {
                        binding.btnDownload.isEnabled = true
                        binding.btnDownload.text = getString(R.string.install)
                    }
                    is AppInstaller.InstallState.Downloading -> {
                        binding.btnDownload.isEnabled = false
                        binding.btnDownload.text = "${state.progress}% (${state.downloaded}/${state.total})"
                    }
                    is AppInstaller.InstallState.Installing -> {
                        binding.btnDownload.text = getString(R.string.installing)
                    }
                    is AppInstaller.InstallState.Success -> {
                        binding.btnDownload.isEnabled = true
                        Toast.makeText(this@DetailActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                        checkInstalledState()
                    }
                    is AppInstaller.InstallState.Error -> {
                        binding.btnDownload.isEnabled = true
                        binding.btnDownload.text = getString(R.string.install)
                        Toast.makeText(this@DetailActivity, state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkInstalledState() {
        if (currentApkAsset != null && repoName.isNotEmpty()) {
            setupInstallButton(repoName, owner)
        }
    }

    private fun setupInstallButton(repoName: String, ownerName: String) {
        installedPackageName = appInstaller.findPackage(repoName, ownerName)
        val isInstalled = installedPackageName?.let { appInstaller.isInstalled(it) } ?: false

        if (isInstalled && installedPackageName != null) {
            // Check if update is available
            val installedVersion = appInstaller.getInstalledVersion(installedPackageName!!)
            val hasUpdate = if (installedVersion != null && currentReleaseTag != null) {
                VersionComparator.isNewerVersion(installedVersion, currentReleaseTag!!)
            } else {
                false
            }
            
            // Show uninstall button
            binding.btnUninstall.visibility = View.VISIBLE
            binding.btnUninstall.setOnClickListener {
                appInstaller.uninstall(installedPackageName!!)
            }
            
            if (hasUpdate) {
                // Update available - show Update button
                binding.btnDownload.text = getString(R.string.update)
                binding.btnDownload.setOnClickListener {
                    currentApkAsset?.let { startDownload(it) }
                }
            } else {
                // Already up to date - show Open button
                binding.btnDownload.text = getString(R.string.open)
                binding.btnDownload.setOnClickListener {
                    if (!appInstaller.launch(installedPackageName!!)) {
                        Toast.makeText(this, R.string.cannot_open_app, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            // Not installed - show Install button
            binding.btnUninstall.visibility = View.GONE
            binding.btnDownload.text = getString(R.string.install)
            binding.btnDownload.setOnClickListener {
                currentApkAsset?.let { startDownload(it) }
            }
        }
    }

    companion object {
        private const val TAG = "DetailActivity"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_REPO = "repo"

        fun newIntent(context: Context, owner: String, repo: String): Intent {
            return Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
            }
        }
    }
}
