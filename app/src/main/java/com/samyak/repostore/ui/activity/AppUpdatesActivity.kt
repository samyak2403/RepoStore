package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.databinding.ActivityAppUpdatesBinding
import com.samyak.repostore.ui.adapter.AppUpdateAdapter
import com.samyak.repostore.ui.viewmodel.AppUpdate
import com.samyak.repostore.ui.viewmodel.AppUpdatesUiState
import com.samyak.repostore.ui.viewmodel.AppUpdatesViewModel
import com.samyak.repostore.ui.viewmodel.AppUpdatesViewModelFactory
import com.samyak.repostore.util.AppInstaller
import kotlinx.coroutines.launch

class AppUpdatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppUpdatesBinding
    private lateinit var appInstaller: AppInstaller
    private lateinit var adapter: AppUpdateAdapter

    private val viewModel: AppUpdatesViewModel by viewModels {
        val app = application as RepoStoreApp
        AppUpdatesViewModelFactory(
            repository = app.repository,
            installedAppMappingDao = app.installedAppMappingDao,
            favoriteAppDao = app.favoriteAppDao,
            cachedRepoDao = app.database.cachedRepoDao(),
            appInstaller = AppInstaller.getInstance(this)
        )
    }

    // Current list being displayed (mutable so we can drop items as they finish updating).
    private val currentUpdates = mutableListOf<AppUpdate>()

    // Sequential download queue — AppInstaller only handles one download at a time.
    private val updateQueue = ArrayDeque<AppUpdate>()
    private val queuedPackages = mutableSetOf<String>()
    private var isProcessing = false

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (canInstall()) {
            processQueue()
        }
    }

    private fun canInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAppUpdatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        appInstaller = AppInstaller.getInstance(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AppUpdateAdapter(
            onUpdateClick = { enqueueUpdate(it) },
            onItemClick = {
                startActivity(DetailActivity.newIntent(this, it.owner, it.repo))
            }
        )
        binding.rvUpdates.apply {
            adapter = this@AppUpdatesActivity.adapter
            layoutManager = LinearLayoutManager(this@AppUpdatesActivity)
        }
    }

    private fun setupListeners() {
        binding.btnUpdateAll.setOnClickListener {
            currentUpdates.toList().forEach { enqueueUpdate(it) }
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.btnCheckUpdates.setOnClickListener {
            viewModel.refresh()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { handleState(it) }
            }
        }
    }

    private fun handleState(state: AppUpdatesUiState) {
        binding.swipeRefresh.isRefreshing = false
        when (state) {
            is AppUpdatesUiState.Loading -> {
                binding.layoutLoading.visibility = View.VISIBLE
                binding.layoutHeader.visibility = View.GONE
                binding.rvUpdates.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            }
            is AppUpdatesUiState.Success -> {
                binding.layoutLoading.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.layoutHeader.visibility = View.VISIBLE
                binding.rvUpdates.visibility = View.VISIBLE
                currentUpdates.clear()
                currentUpdates.addAll(state.updates)
                submitAndUpdateHeader()
            }
            is AppUpdatesUiState.Empty -> {
                binding.layoutLoading.visibility = View.GONE
                binding.layoutHeader.visibility = View.GONE
                binding.rvUpdates.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            }
            is AppUpdatesUiState.Error -> {
                binding.layoutLoading.visibility = View.GONE
                binding.layoutHeader.visibility = View.GONE
                binding.rvUpdates.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                Toast.makeText(this, R.string.updates_check_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitAndUpdateHeader() {
        binding.tvAvailableCount.text = getString(R.string.available_updates, currentUpdates.size)
        binding.btnUpdateAll.isEnabled = currentUpdates.isNotEmpty()
        adapter.submitList(currentUpdates.toList())
    }

    private fun enqueueUpdate(app: AppUpdate) {
        if (queuedPackages.contains(app.packageName)) return
        queuedPackages.add(app.packageName)
        updateQueue.addLast(app)
        processQueue()
    }

    private fun processQueue() {
        if (isProcessing) return
        val next = updateQueue.removeFirstOrNull() ?: return
        isProcessing = true
        startDownload(next)
    }

    private fun startDownload(app: AppUpdate) {
        if (!canInstall()) {
            Toast.makeText(this, R.string.install_permission_prompt, Toast.LENGTH_LONG).show()
            try {
                unknownSourcesLauncher.launch(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                try {
                    unknownSourcesLauncher.launch(
                        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    )
                } catch (_: Exception) {}
            }
            isProcessing = false
            updateQueue.addFirst(app)
            return
        }

        adapter.setItemState(app.packageName, AppUpdateAdapter.ItemState.Downloading(0))
        Toast.makeText(this, getString(R.string.updating_app, app.appName), Toast.LENGTH_SHORT).show()

        appInstaller.download(
            url = app.asset.downloadUrl,
            fileName = app.asset.name,
            title = app.appName,
            repoName = app.repo,
            ownerName = app.owner
        ) { state ->
            runOnUiThread {
                when (state) {
                    is AppInstaller.InstallState.Idle -> { /* no-op */ }
                    is AppInstaller.InstallState.Downloading -> {
                        adapter.setItemState(
                            app.packageName,
                            AppUpdateAdapter.ItemState.Downloading(state.progress)
                        )
                    }
                    is AppInstaller.InstallState.Installing -> {
                        adapter.setItemState(app.packageName, AppUpdateAdapter.ItemState.Installing)
                    }
                    is AppInstaller.InstallState.Success -> {
                        onDownloadFinished(app, success = true)
                    }
                    is AppInstaller.InstallState.Error -> {
                        Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                        onDownloadFinished(app, success = false)
                    }
                }
            }
        }
    }

    private fun onDownloadFinished(app: AppUpdate, success: Boolean) {
        queuedPackages.remove(app.packageName)
        isProcessing = false

        if (success) {
            // The system installer takes over from here; drop it from the pending list.
            currentUpdates.removeAll { it.packageName == app.packageName }
            if (currentUpdates.isEmpty()) {
                binding.layoutHeader.visibility = View.GONE
                binding.rvUpdates.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
            } else {
                submitAndUpdateHeader()
            }
        } else {
            adapter.setItemState(app.packageName, AppUpdateAdapter.ItemState.Idle)
        }

        processQueue()
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, AppUpdatesActivity::class.java)
        }
    }
}
