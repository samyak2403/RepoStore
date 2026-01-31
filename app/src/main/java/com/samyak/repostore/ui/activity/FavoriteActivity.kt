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

class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    
    private val viewModel: FavoriteViewModel by viewModels {
        FavoriteViewModelFactory((application as RepoStoreApp).favoriteAppDao)
    }

    private lateinit var appAdapter: FavoriteAppAdapter
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

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
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
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
            },
            onDeveloperClick = { developer, avatarUrl ->
                val intent = DeveloperActivity.newIntent(this, developer, avatarUrl)
                startActivity(intent)
            }
        )

        binding.rvApps.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(this@FavoriteActivity)
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
