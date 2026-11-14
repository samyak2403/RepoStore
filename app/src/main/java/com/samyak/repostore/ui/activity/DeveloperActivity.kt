package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.databinding.ActivityDeveloperBinding
import com.samyak.repostore.ui.adapter.RankedAppAdapter
import com.samyak.repostore.ui.viewmodel.DeveloperUiState
import com.samyak.repostore.ui.viewmodel.DeveloperViewModel
import com.samyak.repostore.ui.viewmodel.DeveloperViewModelFactory
import com.samyak.repostore.util.RateLimitDialog
import com.samyak.repostore.util.openExternalUrl
import kotlinx.coroutines.launch

class DeveloperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperBinding

    private val viewModel: DeveloperViewModel by viewModels {
        DeveloperViewModelFactory(
            (application as RepoStoreApp).repository,
            intent.getStringExtra(EXTRA_DEVELOPER) ?: ""
        )
    }

    private lateinit var appAdapter: RankedAppAdapter
    private var developerName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDeveloperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        developerName = intent.getStringExtra(EXTRA_DEVELOPER) ?: ""
        val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL)

        if (developerName.isEmpty()) {
            finish()
            return
        }

        setupToolbar()
        setupHeader(avatarUrl)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.title = developerName
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupHeader(avatarUrl: String?) {
        binding.tvDeveloperName.text = developerName
        
        avatarUrl?.let {
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.ic_app_placeholder)
                .circleCrop()
                .into(binding.ivDeveloperAvatar)
        }

        binding.btnGithubProfile.setOnClickListener {
            openExternalUrl("https://github.com/$developerName")
        }
    }

    private fun setupRecyclerView() {
        appAdapter = RankedAppAdapter(
            onItemClick = { appItem ->
                val intent = DetailActivity.newIntent(
                    this,
                    appItem.repo.owner.login,
                    appItem.repo.name
                )
                startActivity(intent)
            }
        )

        binding.rvApps.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(this@DeveloperActivity)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                    if (lastVisibleItem >= totalItemCount - 5 && dy > 0) {
                        viewModel.loadMore()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
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

    private fun handleUiState(state: DeveloperUiState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is DeveloperUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvApps.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }

            is DeveloperUiState.LoadingMore -> {
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                appAdapter.submitList(state.currentApps)
                binding.tvRepoCount.text = getString(R.string.repositories_count, state.currentApps.size)
            }

            is DeveloperUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                appAdapter.submitList(state.apps)
                binding.tvRepoCount.text = getString(R.string.repositories_count, state.apps.size)
            }

            is DeveloperUiState.Empty -> {
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = getString(R.string.no_repos_found)
            }

            is DeveloperUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvApps.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "${state.message}\n\n${getString(R.string.tap_to_retry)}"
                binding.tvError.setOnClickListener {
                    viewModel.retry()
                }
                
                // Show rate limit dialog if applicable
                RateLimitDialog.showIfNeeded(this, state.message)
            }
        }
    }

    companion object {
        private const val EXTRA_DEVELOPER = "developer"
        private const val EXTRA_AVATAR_URL = "avatar_url"

        fun newIntent(context: Context, developer: String, avatarUrl: String? = null): Intent {
            return Intent(context, DeveloperActivity::class.java).apply {
                putExtra(EXTRA_DEVELOPER, developer)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)
            }
        }
    }
}
