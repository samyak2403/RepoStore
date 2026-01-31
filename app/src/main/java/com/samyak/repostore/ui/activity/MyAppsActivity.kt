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
import com.samyak.repostore.databinding.ActivityMyAppsBinding
import com.samyak.repostore.ui.adapter.FavoriteAppAdapter
import com.samyak.repostore.ui.viewmodel.MyAppsUiState
import com.samyak.repostore.ui.viewmodel.MyAppsViewModel
import com.samyak.repostore.ui.viewmodel.MyAppsViewModelFactory
import com.samyak.repostore.ui.widget.ShimmerFrameLayout
import kotlinx.coroutines.launch

class MyAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyAppsBinding
    
    private val viewModel: MyAppsViewModel by viewModels {
        MyAppsViewModelFactory(
            (application as RepoStoreApp).favoriteAppDao,
            packageManager
        )
    }

    private lateinit var appAdapter: FavoriteAppAdapter
    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMyAppsBinding.inflate(layoutInflater)
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
            layoutManager = LinearLayoutManager(this@MyAppsActivity)
        }
    }

    private fun setupSwipeRefresh() {
        // Installed apps are from local check, just stop refreshing immediately
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

    private fun handleUiState(state: MyAppsUiState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is MyAppsUiState.Loading -> {
                showSkeleton()
                binding.rvApps.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }

            is MyAppsUiState.Success -> {
                hideSkeleton()
                binding.rvApps.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                appAdapter.submitList(state.apps)
            }

            is MyAppsUiState.Empty -> {
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
            return Intent(context, MyAppsActivity::class.java)
        }
    }
}
