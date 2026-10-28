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
import androidx.appcompat.app.AlertDialog
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
import com.samyak.repostore.databinding.LayoutReleaseVariantPickerBinding
import com.samyak.repostore.ui.adapter.AppShelf
import com.samyak.repostore.ui.adapter.PlayStoreAppAdapter
import com.samyak.repostore.ui.adapter.ReleaseVariantAdapter
import com.samyak.repostore.ui.adapter.ScreenshotAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.samyak.repostore.ui.viewmodel.DetailUiState
import com.samyak.repostore.ui.viewmodel.DetailViewModel
import com.samyak.repostore.ui.viewmodel.DetailViewModelFactory
import com.samyak.repostore.util.AppInstaller
import com.samyak.repostore.util.ApkArchitectureHelper
import com.samyak.repostore.util.SecurityChecker
import com.samyak.repostore.util.SecurityStatus
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import com.samyak.repostore.ui.widget.ShimmerFrameLayout
import com.samyak.repostore.util.ApkSelectionResult
import com.samyak.repostore.util.RateLimitDialog
import com.samyak.repostore.util.VersionComparator
import com.samyak.repostore.util.applyTransition
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.samyak.gitcore.util.IconResolver
import com.samyak.gitcore.util.DownloadCounter
import com.samyak.repostore.util.loadIconWithFallback
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
    private lateinit var similarAppsAdapter: PlayStoreAppAdapter

    /** Repo id the similar-apps search has already been issued for. */
    private var similarAppsRequestedFor: Long? = null
    private lateinit var appInstaller: AppInstaller
    private lateinit var favoriteAppDao: FavoriteAppDao
    
    private var owner: String = ""
    private var repoName: String = ""
    private var currentApkAsset: ReleaseAsset? = null
    private var allApkAssets: List<ReleaseAsset> = emptyList()
    private var installedPackageName: String? = null
    private var currentRepo: GitHubRepo? = null
    private var currentReleaseTag: String? = null
    private var setupButtonJob: kotlinx.coroutines.Job? = null
    private var downloadsJob: kotlinx.coroutines.Job? = null
    private var securityJob: kotlinx.coroutines.Job? = null

    // Asset waiting to be downloaded once the user grants "install unknown apps".
    private var pendingInstallAsset: ReleaseAsset? = null

    // Launches the system "Install unknown apps" settings screen and resumes the
    // pending download once the user returns (if permission was granted).
    private val unknownSourcesLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
            val asset = pendingInstallAsset
            pendingInstallAsset = null
            if (asset != null && canInstallPackages()) {
                startDownload(asset)
            } else if (asset != null) {
                Toast.makeText(this, R.string.install_permission_required, Toast.LENGTH_LONG).show()
                setDownloadButtonState(getString(R.string.install), true, formatAssetSubtitle(currentApkAsset))
                binding.btnDownloadDropdown.isEnabled = true
            }
        }
    
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
        setupExpandableSections()
        setupSimilarAppsShelf()
        observeViewModel()
        viewModel.loadAppDetails(owner, repoName, forceRefresh = true)
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

    /**
     * Play Store collapses long body text and puts a chevron on each section
     * heading. Tapping the heading expands that section and rotates the chevron
     * a quarter turn to point down.
     */
    private fun setupExpandableSections() {
        bindExpandableSection(
            header = binding.headerAbout,
            chevron = binding.ivAboutExpand,
            body = binding.tvDescription,
            collapsedMaxLines = ABOUT_COLLAPSED_LINES
        )
        bindExpandableSection(
            header = binding.headerRelease,
            chevron = binding.ivReleaseExpand,
            body = binding.tvReleaseNotes,
            collapsedMaxLines = RELEASE_NOTES_COLLAPSED_LINES
        )
        bindExpandableSection(
            header = binding.headerReadme,
            chevron = binding.ivReadmeExpand,
            body = binding.tvReadme,
            collapsedMaxLines = README_COLLAPSED_LINES
        )
    }

    private fun bindExpandableSection(
        header: View,
        chevron: android.widget.ImageView,
        body: android.widget.TextView,
        collapsedMaxLines: Int
    ) {
        header.setOnClickListener {
            val expanding = body.maxLines == collapsedMaxLines
            body.maxLines = if (expanding) Int.MAX_VALUE else collapsedMaxLines
            chevron.animate()
                .rotation(if (expanding) 90f else 0f)
                .setDuration(CHEVRON_ROTATION_MS)
                .start()
        }
    }

    /**
     * The "Similar apps" shelf reuses the same horizontally scrolling tile shelf
     * as the home and games tabs. There is no full list to open from here, so
     * the section header's chevron is hidden.
     */
    private fun setupSimilarAppsShelf() {
        similarAppsAdapter = PlayStoreAppAdapter(AppShelf.Style.TILES) { appItem ->
            startActivity(
                newIntent(this, appItem.repo.owner.login, appItem.repo.name)
            )
        }

        binding.sectionSimilar.tvSectionTitle.text = getString(R.string.similar_apps)
        binding.sectionSimilar.btnSeeMore.visibility = View.GONE
        binding.sectionSimilar.rvApps.apply {
            adapter = similarAppsAdapter
            AppShelf.setup(this, AppShelf.Style.TILES)
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
            applyTransition(isOpen = true, R.anim.slide_up, R.anim.fade_out)
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
                    viewModel.similarApps.collect { apps ->
                        val visible = if (apps.isEmpty()) View.GONE else View.VISIBLE
                        binding.sectionSimilar.root.visibility = visible
                        binding.dividerSimilar.visibility = visible
                        similarAppsAdapter.submitList(apps)
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

                launch {
                    viewModel.realAppName.collect { realName ->
                        if (!realName.isNullOrBlank()) {
                            binding.tvAppName.text = realName
                        }
                    }
                }
            }
        }
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

                // Success is emitted twice for one repo: once with the repo alone
                // and again once the release resolves. Guarding on the repo id
                // keeps the similar-apps search to a single request, which
                // matters against the GitHub rate limit.
                if (similarAppsRequestedFor != state.repo.id) {
                    similarAppsRequestedFor = state.repo.id
                    viewModel.loadSimilarApps(state.repo)
                }
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
            // Total app downloads — show the latest release's count instantly,
            // then load the full total across ALL releases (all versions) in the background.
            tvDownloads.text = DownloadCounter.totalFormatted(
                release?.assets?.map { it.downloadCount } ?: emptyList()
            )
            loadTotalDownloads(repo.owner.login, repo.name)
            tvLanguage.text = repo.language ?: "Code"
            tvUpdated.text = formatDate(repo.updatedAt)

            // Resolve app icons
            val iconUrls = IconResolver.resolve(repo.owner.login, repo.name, repo.defaultBranch, repo.language)

            // Load high-resolution icon with fallbacks
            ivAppIcon.loadIconWithFallback(iconUrls, repo.owner.avatarUrl)

            // Icon click - open icon viewer
            ivAppIcon.setOnClickListener {
                // Determine what to pass to the viewer
                // Note: We'll pass the list to the viewer so it can also resolve the best icon
                val intent = IconViewerActivity.newIntent(
                    this@DetailActivity,
                    iconUrls,
                    repo.owner.avatarUrl,
                    repo.name
                )
                startActivity(intent)
                applyTransition(isOpen = true, R.anim.slide_up, R.anim.fade_out)
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
            
            // Set current repo
            currentRepo = repo

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

                // Find all APK assets
                allApkAssets = release.assets.filter { 
                    it.name.endsWith(".apk", ignoreCase = true)
                }

                if (allApkAssets.isNotEmpty()) {
                    // Auto-select best APK for single APK or architecture-specific builds
                    val selection = ApkArchitectureHelper.selectBestApk(release.assets)
                    when (selection) {
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
                    
                    setupInstallButton(repo.name, repo.owner.login)
                } else {
                    currentApkAsset = null
                    setDownloadButtonState(getString(R.string.view_release))
                    btnDownloadMain.setOnClickListener {
                        openUrl(release.htmlUrl)
                    }
                    updateSplitButtonShape(true)
                    btnDownloadDropdown.setOnClickListener {
                        showReleaseVariantPicker()
                    }
                }
            } else {
                cardRelease.visibility = View.GONE
                setDownloadButtonState(getString(R.string.view_on_github))
                btnDownloadMain.setOnClickListener {
                    openUrl(repo.htmlUrl)
                }
                updateSplitButtonShape(true)
                btnDownloadDropdown.setOnClickListener {
                    showReleaseVariantPicker()
                }
            }

            // GitHub button
            btnGithub.setOnClickListener {
                openUrl(repo.htmlUrl)
            }
        }

        // Verify APK security via VirusTotal and show the badge
        checkAppSecurity()
    }

    /**
     * Look up the selected APK asset's security report on VirusTotal and update the
     * security badge. Runs off the main thread and never blocks the UI.
     */
    private fun checkAppSecurity() {
        securityJob?.cancel()

        val asset = currentApkAsset
        if (asset == null) {
            binding.layoutSecurityStat.visibility = View.GONE
            return
        }
        binding.layoutSecurityStat.visibility = View.VISIBLE

        // Show a "checking" state immediately.
        updateSecurityBadge(SecurityStatus.Checking)

        securityJob = lifecycleScope.launch {
            val status = SecurityChecker.check(asset)
            if (!isActive) return@launch
            updateSecurityBadge(status)
        }
    }

    private fun updateSecurityBadge(status: SecurityStatus) {
        val icon = binding.ivSecurityStat
        val text = binding.tvSecurityStat

        fun style(textColorRes: Int, iconRes: Int, label: String) {
            binding.layoutSecurityStat.visibility = View.VISIBLE
            val color = ContextCompat.getColor(this, textColorRes)
            text.text = label
            text.setTextColor(color)
            icon.setImageResource(iconRes)
            icon.imageTintList = ColorStateList.valueOf(color)
        }

        // Explanation shown when the stat is tapped, honest for each tier.
        val infoRes: Int

        when (status) {
            is SecurityStatus.Checking -> {
                style(
                    R.color.badge_unknown_text,
                    R.drawable.ic_cat_security, getString(R.string.security_stat_checking)
                )
                infoRes = R.string.security_powered_by
            }
            is SecurityStatus.Safe -> {
                style(
                    R.color.badge_safe_text,
                    R.drawable.ic_verified_shield, getString(R.string.security_stat_safe)
                )
                infoRes = R.string.security_info_scan
            }
            is SecurityStatus.Flagged -> {
                style(
                    R.color.badge_flagged_text,
                    R.drawable.ic_warning, getString(R.string.security_stat_warning)
                )
                infoRes = R.string.security_powered_by
            }
            is SecurityStatus.Verified -> {
                // GitHub-published checksum available: integrity verifiable for all users.
                style(
                    R.color.badge_safe_text,
                    R.drawable.ic_verified_shield, getString(R.string.security_stat_verified)
                )
                infoRes = R.string.security_info_integrity
            }
            is SecurityStatus.Unknown -> {
                // Baseline signal shown to every user: it is an official GitHub release.
                style(
                    R.color.badge_unknown_text,
                    R.drawable.ic_cat_security, getString(R.string.security_stat_source)
                )
                infoRes = R.string.security_info_source
            }
        }

        // For a flagged/safe scan, include the engine counts in the tap message.
        val message: String = when (status) {
            is SecurityStatus.Safe ->
                if (status.totalEngines > 0)
                    getString(R.string.security_verified_detail, status.totalEngines)
                else getString(infoRes)
            is SecurityStatus.Flagged ->
                getString(
                    R.string.security_flagged_detail,
                    status.malicious + status.suspicious,
                    status.totalEngines
                )
            else -> getString(infoRes)
        }

        binding.layoutSecurityStat.setOnClickListener {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Load the total download count summed across ALL releases (all versions)
     * and update the Downloads stat. Falls back silently to the latest-release
     * count already shown if the full fetch fails.
     */
    private fun loadTotalDownloads(ownerLogin: String, repo: String) {
        downloadsJob?.cancel()
        downloadsJob = lifecycleScope.launch {
            val repository = (application as RepoStoreApp).repository
            val result = repository.getTotalDownloads(ownerLogin, repo)
            if (!isActive) return@launch
            result.getOrNull()?.let { total ->
                binding.tvDownloads.text = DownloadCounter.format(total)
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

    private fun setDownloadButtonState(text: String, isEnabled: Boolean = true, subtitle: String? = null) {
        binding.apply {
            tvDownloadAction.text = text
            btnDownloadMain.isEnabled = isEnabled
            cardDownloadGroup.isEnabled = isEnabled
            cardDownloadGroup.alpha = if (isEnabled) 1.0f else 0.5f
            
            if (subtitle != null && subtitle.isNotEmpty()) {
                tvDownloadSubtitle.text = subtitle
                tvDownloadSubtitle.visibility = View.VISIBLE
            } else {
                tvDownloadSubtitle.visibility = View.GONE
            }
        }
    }

    private fun updateSplitButtonShape(showDropdown: Boolean) {
        val radius24 = 24f * resources.displayMetrics.density
        val radius6 = 6f * resources.displayMetrics.density
        
        val builder = com.google.android.material.shape.ShapeAppearanceModel.builder()
            .setTopLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius24)
            .setBottomLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius24)
            
        if (showDropdown) {
            builder.setTopRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius6)
            builder.setBottomRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius6)
            binding.btnDownloadDropdown.visibility = View.VISIBLE
            binding.dividerDownload.visibility = View.VISIBLE
        } else {
            builder.setTopRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius24)
            builder.setBottomRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, radius24)
            binding.btnDownloadDropdown.visibility = View.GONE
            binding.dividerDownload.visibility = View.GONE
        }
        binding.btnDownloadMainCard.shapeAppearanceModel = builder.build()
    }

    private fun formatAssetSubtitle(asset: ReleaseAsset?): String? {
        if (asset == null) return null
        
        val lowerName = asset.name.lowercase()
        val archStr = when {
            lowerName.contains("arm64") || lowerName.contains("aarch64") -> "aarch64"
            lowerName.contains("armeabi-v7a") || lowerName.contains("arm-v7a") || lowerName.contains("armv7") -> "armeabi-v7a"
            lowerName.contains("x86_64") -> "x86_64"
            lowerName.contains("x86") -> "x86"
            lowerName.contains("universal") || lowerName.contains("all") -> "Universal"
            else -> "App"
        }
        
        val sizeMb = String.format(java.util.Locale.US, "%.1f MB", asset.size / (1024.0 * 1024.0))
        return "$archStr • $sizeMb"
    }

    /**
     * True when this app is allowed to install APKs. On Android 8.0+ the user must
     * grant "Install unknown apps" for this app or every install intent is blocked.
     */
    private fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Send the user to the system screen where they can allow this app to install APKs,
     * then resume [asset]'s download when they come back.
     */
    private fun requestInstallPermission(asset: ReleaseAsset) {
        pendingInstallAsset = asset
        Toast.makeText(this, R.string.install_permission_prompt, Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            unknownSourcesLauncher.launch(intent)
        } catch (e: Exception) {
            // Some OEMs don't support the per-app screen; fall back to the global one.
            try {
                unknownSourcesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
            } catch (e2: Exception) {
                pendingInstallAsset = null
                Toast.makeText(this, R.string.install_permission_required, Toast.LENGTH_LONG).show()
                setDownloadButtonState(getString(R.string.install), true, formatAssetSubtitle(currentApkAsset))
                binding.btnDownloadDropdown.isEnabled = true
            }
        }
    }

    private fun startDownload(asset: ReleaseAsset) {
        // On Android 8.0+ the install will be silently blocked unless the user has
        // allowed this app to install unknown apps. Gate the download on that.
        if (!canInstallPackages()) {
            requestInstallPermission(asset)
            return
        }

        // Disable button immediately
        setDownloadButtonState("0%", false, formatAssetSubtitle(asset))
        binding.btnDownloadDropdown.isEnabled = false
        
        appInstaller.download(
            url = asset.downloadUrl,
            fileName = asset.name,
            title = repoName,
            repoName = repoName,
            ownerName = owner
        ) { state ->
            // Ensure UI updates run on main thread
            runOnUiThread {
                when (state) {
                    is AppInstaller.InstallState.Idle -> {
                        setDownloadButtonState(getString(R.string.install), true, formatAssetSubtitle(currentApkAsset))
                        binding.btnDownloadDropdown.isEnabled = true
                    }
                    is AppInstaller.InstallState.Downloading -> {
                        setDownloadButtonState("${state.progress}% (${state.downloaded}/${state.total})", false, formatAssetSubtitle(currentApkAsset))
                    }
                    is AppInstaller.InstallState.Installing -> {
                        setDownloadButtonState(getString(R.string.installing), false)
                    }
                    is AppInstaller.InstallState.Success -> {
                        binding.btnDownloadDropdown.isEnabled = true
                        Toast.makeText(this@DetailActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                        checkInstalledState()
                    }
                    is AppInstaller.InstallState.Error -> {
                        setDownloadButtonState(getString(R.string.install), true, formatAssetSubtitle(currentApkAsset))
                        binding.btnDownloadDropdown.isEnabled = true
                        if (state.message.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                            state.message.contains("signature", ignoreCase = true) ||
                            state.message.contains("incompatible", ignoreCase = true)) {
                            showSignatureMismatchDialog()
                        } else {
                            Toast.makeText(this@DetailActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
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
        setupButtonJob?.cancel()
        setupButtonJob = lifecycleScope.launch {
            // Run heavy findPackage (DB query + GitHub source lookup + PackageManager scan) off the main thread
            val detectedPackage = withContext(Dispatchers.IO) {
                appInstaller.findPackage(
                    repoName, ownerName, currentReleaseTag,
                    defaultBranch = currentRepo?.defaultBranch,
                    language = currentRepo?.language
                )
            }
            
            if (!isActive) return@launch
            
            installedPackageName = detectedPackage
            val isInstalled = installedPackageName?.let { appInstaller.isInstalled(it) } ?: false

            Log.d(TAG, "setupInstallButton: repo='$repoName', owner='$ownerName', " +
                    "detectedPkg='$installedPackageName', isInstalled=$isInstalled")

            // Always show dropdown arrow, even if only 1 variant exists
            updateSplitButtonShape(true)
            binding.btnDownloadDropdown.setOnClickListener {
                showReleaseVariantPicker()
            }

            if (isInstalled && installedPackageName != null) {
                // Check if update is available
                val installedVersion = appInstaller.getInstalledVersion(installedPackageName!!)
                if (installedVersion != null && currentReleaseTag != null) {
                    binding.tvVersion.text = getString(R.string.version_installed_format, currentReleaseTag, installedVersion)
                }
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
                    // Update available
                    setDownloadButtonState(getString(R.string.update), true, formatAssetSubtitle(currentApkAsset))
                    
                    binding.btnDownloadMain.setOnClickListener {
                        currentApkAsset?.let { startDownload(it) } ?: run {
                            openUrl(currentRepo?.htmlUrl ?: "")
                        }
                    }
                } else {
                    updateSplitButtonShape(false)
                    setDownloadButtonState(getString(R.string.open), true, null)
                    binding.btnDownloadMain.setOnClickListener {
                        if (!appInstaller.launch(installedPackageName!!)) {
                            Toast.makeText(this@DetailActivity, R.string.cannot_open_app, Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Allow overriding mismatch or switching variants via long-press
                    binding.btnDownloadMain.setOnLongClickListener {
                        showMismatchActionDialog(installedPackageName!!)
                        true
                    }
                }
            } else {
                // Not installed - show Install button
                binding.btnUninstall.visibility = View.GONE
                
                setDownloadButtonState(getString(R.string.install), true, formatAssetSubtitle(currentApkAsset))
                
                binding.btnDownloadMain.setOnClickListener {
                    currentApkAsset?.let { startDownload(it) } ?: run {
                        // Fallback
                        openUrl(currentRepo?.htmlUrl ?: "")
                    }
                }
            }
            
            // Remove lingering long-press listener if not installed
            if (!isInstalled) {
                binding.btnDownloadMain.setOnLongClickListener(null)
            }
        }
    }

    private fun showMismatchActionDialog(packageName: String) {
        val appLabel = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_mismatch_title)
            .setMessage(getString(R.string.app_mismatch_message, appLabel, packageName))
            .setPositiveButton(R.string.open) { _, _ ->
                if (!appInstaller.launch(packageName)) {
                    Toast.makeText(this, R.string.cannot_open_app, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.install_anyway) { _, _ ->
                currentApkAsset?.let { startDownload(it) } ?: run {
                    openUrl(currentRepo?.htmlUrl ?: "")
                }
            }
            .setNeutralButton(R.string.forget_mapping) { _, _ ->
                appInstaller.clearMapping(repoName, owner)
                // Briefly wait for DB update then re-check
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    checkInstalledState()
                }
            }
            .show()
    }

    private fun showSignatureMismatchDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.signature_mismatch_title)
            .setMessage(R.string.signature_mismatch_message)
            .setPositiveButton(R.string.uninstall_and_reinstall) { _, _ ->
                installedPackageName?.let { pkg ->
                    appInstaller.uninstall(pkg)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showReleaseVariantPicker() {
        val bottomSheet = BottomSheetDialog(this)
        val pickerBinding = com.samyak.repostore.databinding.LayoutReleaseVariantPickerBinding.inflate(layoutInflater)
        bottomSheet.setContentView(pickerBinding.root)

        if (allApkAssets.isEmpty()) {
            pickerBinding.layoutNoApk.visibility = View.VISIBLE
            pickerBinding.rvVariants.visibility = View.GONE
        } else {
            pickerBinding.layoutNoApk.visibility = View.GONE
            pickerBinding.rvVariants.visibility = View.VISIBLE

            val adapter = com.samyak.repostore.ui.adapter.ReleaseVariantAdapter { asset ->
                currentApkAsset = asset
                setDownloadButtonState(binding.tvDownloadAction.text.toString(), true, formatAssetSubtitle(asset))
                bottomSheet.dismiss()
            }
            
            // Find the actual recommended asset
            val recommendedResult = com.samyak.repostore.util.ApkArchitectureHelper.selectBestApk(allApkAssets)
            val recommendedAssetId = when (recommendedResult) {
                is com.samyak.repostore.util.ApkSelectionResult.Single -> recommendedResult.asset.id
                is com.samyak.repostore.util.ApkSelectionResult.ExactMatch -> recommendedResult.asset.id
                is com.samyak.repostore.util.ApkSelectionResult.Universal -> recommendedResult.asset.id
                is com.samyak.repostore.util.ApkSelectionResult.Fallback -> recommendedResult.asset.id
                else -> null
            }
            
            // Mark the actual recommended APK
            adapter.setRecommendedAssetId(recommendedAssetId)
            // Mark the currently selected APK (what will be downloaded)
            adapter.setSelectedAssetId(currentApkAsset?.id)

            pickerBinding.rvVariants.apply {
                this.adapter = adapter
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@DetailActivity)
            }

            // Sort variants: Recommended first, then others
            val sortedVariants = allApkAssets.sortedWith(compareByDescending { it.id == recommendedAssetId })
            adapter.submitList(sortedVariants)
        }

        bottomSheet.show()
    }

    companion object {
        private const val TAG = "DetailActivity"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_REPO = "repo"

        // Collapsed line counts for the expandable sections. These must match the
        // android:maxLines values in activity_detail.xml, which is what the
        // expand toggle compares against to work out the current state.
        private const val ABOUT_COLLAPSED_LINES = 4
        private const val RELEASE_NOTES_COLLAPSED_LINES = 6
        private const val README_COLLAPSED_LINES = 8
        private const val CHEVRON_ROTATION_MS = 150L

        fun newIntent(context: Context, owner: String, repo: String): Intent {
            return Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_OWNER, owner)
                putExtra(EXTRA_REPO, repo)
            }
        }
    }
}
