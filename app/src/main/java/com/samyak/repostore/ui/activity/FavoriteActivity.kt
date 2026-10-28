package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.databinding.ActivityFavoriteBinding
import com.samyak.repostore.ui.adapter.FavoriteAppAdapter
import com.samyak.repostore.ui.viewmodel.FavoriteUiState
import com.samyak.repostore.ui.viewmodel.FavoriteViewModel
import com.samyak.repostore.ui.viewmodel.FavoriteViewModelFactory
import com.samyak.repostore.ui.widget.ShimmerFrameLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samyak.repostore.R
import com.samyak.repostore.data.model.FavoriteApp

class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    
    private val viewModel: FavoriteViewModel by viewModels {
        val app = application as RepoStoreApp
        FavoriteViewModelFactory(app.favoriteAppDao, app.installedAppMappingDao, packageManager)
    }

    private lateinit var appAdapter: FavoriteAppAdapter
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportFavoritesToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFavoritesFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize shimmer layout
        shimmerLayout = binding.skeletonLayout.root as? ShimmerFrameLayout

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilterChips()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_favorite)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_export -> {
                    exportLauncher.launch("favorites.json")
                    true
                }
                R.id.action_import -> {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        appAdapter = FavoriteAppAdapter(
            onItemClick = { favoriteApp ->
                val intent = DetailActivity.newIntent(
                    this,
                    favoriteApp.ownerLogin,
                    favoriteApp.name
                )
                startActivity(intent)
            }
        )

        binding.rvApps.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(this@FavoriteActivity)
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(R.id.chip_favorites)) {
                viewModel.setFilterMode(FavoriteViewModel.FilterMode.FAVORITES)
            } else {
                viewModel.setFilterMode(FavoriteViewModel.FilterMode.ALL_INSTALLED)
            }
        }
    }

    private fun setupSwipeRefresh() {
        // Favorites are from local DB, just stop refreshing immediately
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: FavoriteUiState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is FavoriteUiState.Loading -> {
                showSkeleton()
                binding.rvApps.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }

            is FavoriteUiState.Success -> {
                hideSkeleton()
                binding.rvApps.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                appAdapter.submitList(state.apps)
            }

            is FavoriteUiState.Empty -> {
                hideSkeleton()
                binding.rvApps.visibility = View.GONE
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
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
    
    private fun exportFavoritesToFile(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val favorites = viewModel.getFavoritesForExport()
                val json = Gson().toJson(favorites)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray())
                    }
                }
                Toast.makeText(this@FavoriteActivity, R.string.export_successful, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FavoriteActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importFavoritesFromFile(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                }
                if (json != null) {
                    val type = object : TypeToken<List<FavoriteApp>>() {}.type
                    val favorites: List<FavoriteApp> = Gson().fromJson(json, type)
                    viewModel.importFavorites(favorites)
                    Toast.makeText(this@FavoriteActivity, R.string.import_successful, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@FavoriteActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FavoriteActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shimmerLayout = null
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, FavoriteActivity::class.java)
        }
    }
}
