package com.samyak.repostore.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.samyak.repostore.ui.widget.ShimmerFrameLayout
import com.google.android.material.tabs.TabLayout
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.model.AppCategory
import com.samyak.repostore.data.model.AppItem
import com.samyak.repostore.databinding.FragmentHomeBinding
import com.samyak.repostore.databinding.SectionAppCarouselBinding
import com.samyak.repostore.databinding.SectionAppListBinding
import com.samyak.repostore.ui.activity.AppListActivity
import com.samyak.repostore.ui.activity.DetailActivity
import com.samyak.repostore.ui.adapter.FeaturedAppAdapter
import com.samyak.repostore.ui.adapter.PlayStoreAppAdapter
import com.samyak.repostore.ui.viewmodel.HomeUiState
import com.samyak.repostore.ui.viewmodel.HomeViewModel
import com.samyak.repostore.ui.viewmodel.HomeViewModelFactory
import com.samyak.repostore.ui.viewmodel.ListType
import com.samyak.repostore.util.RateLimitDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory((requireActivity().application as RepoStoreApp).repository)
    }

    private lateinit var featuredAdapter: FeaturedAppAdapter
    private lateinit var trendingAdapter: PlayStoreAppAdapter
    private lateinit var updatedAdapter: PlayStoreAppAdapter

    private lateinit var sectionFeatured: SectionAppCarouselBinding
    private lateinit var sectionTrending: SectionAppListBinding
    private lateinit var sectionUpdated: SectionAppListBinding


    
    // Shimmer layout for skeleton loading
    private var shimmerLayout: ShimmerFrameLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize shimmer layout from the included layout
        // ViewBinding exposes included layouts, but ShimmerFrameLayout is the root
        shimmerLayout = view.findViewById(R.id.skeleton_layout)
        
        bindSections()
        setupSearchBar()
        setupCategoryTabs()
        setupFeaturedCarousel()
        setupAppSections()
        setupSeeMoreButtons()
        setupSwipeRefresh()
        setupErrorRetry()
        observeViewModel()
    }

    private fun bindSections() {
        sectionFeatured = SectionAppCarouselBinding.bind(binding.sectionFeatured.root)
        sectionTrending = SectionAppListBinding.bind(binding.sectionTrending.root)
        sectionUpdated = SectionAppListBinding.bind(binding.sectionUpdated.root)

        sectionFeatured.tvSectionTitle.text = getString(R.string.recommended_for_you)
        sectionTrending.tvSectionTitle.text = getString(R.string.top_free_apps)
        sectionUpdated.tvSectionTitle.text = getString(R.string.recently_updated)
    }

    private fun setupSearchBar() {
        binding.cardSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SearchFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupCategoryTabs() {
        val categories = AppCategory.entries
        categories.forEach { category ->
            binding.tabCategories.addTab(
                binding.tabCategories.newTab().setText(category.displayName)
            )
        }

        binding.tabCategories.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val category = categories[it.position]
                    viewModel.selectCategory(category)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFeaturedCarousel() {
        featuredAdapter = FeaturedAppAdapter { appItem ->
            navigateToDetail(appItem)
        }

        sectionFeatured.viewpagerFeatured.apply {
            adapter = featuredAdapter
            offscreenPageLimit = 3
            clipToPadding = false
            clipChildren = false

            val transformer = CompositePageTransformer()
            transformer.addTransformer(MarginPageTransformer(24))
            transformer.addTransformer { page, position ->
                val scale = 1 - abs(position) * 0.1f
                page.scaleY = scale
            }
            setPageTransformer(transformer)

            // No page change callback needed - WormDotsIndicator handles it automatically
        }
    }

    private fun setupWormDotsIndicator() {
        // Attach WormDotsIndicator to ViewPager2 - handles page changes automatically
        sectionFeatured.wormDotsIndicator.attachTo(sectionFeatured.viewpagerFeatured)
    }

    private fun setupAppSections() {
        trendingAdapter = PlayStoreAppAdapter { appItem ->
            navigateToDetail(appItem)
        }
        sectionTrending.rvApps.apply {
            adapter = trendingAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }

        updatedAdapter = PlayStoreAppAdapter { appItem ->
            navigateToDetail(appItem)
        }
        sectionUpdated.rvApps.apply {
            adapter = updatedAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupSeeMoreButtons() {
        sectionFeatured.btnSeeMore.setOnClickListener {
            val category = viewModel.selectedCategory.value
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.FEATURED,
                getString(R.string.recommended_for_you),
                category.name
            )
            startActivity(intent)
        }

        sectionTrending.btnSeeMore.setOnClickListener {
            val category = viewModel.selectedCategory.value
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.TRENDING,
                getString(R.string.top_free_apps),
                category.name
            )
            startActivity(intent)
        }

        sectionUpdated.btnSeeMore.setOnClickListener {
            val category = viewModel.selectedCategory.value
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.UPDATED,
                getString(R.string.recently_updated),
                category.name
            )
            startActivity(intent)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupErrorRetry() {
        binding.tvError.setOnClickListener {
            viewModel.retry()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: HomeUiState) {
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            is HomeUiState.Loading -> {
                showSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.GONE
            }

            is HomeUiState.Empty -> {
                hideSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = getString(R.string.no_apps_found)
            }

            is HomeUiState.LoadingMore -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateSections(state.currentApps)
            }

            is HomeUiState.Success -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateSections(state.apps)
            }

            is HomeUiState.Error -> {
                hideSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "${state.message}\n\n${getString(R.string.tap_to_retry)}"
                
                // Show rate limit dialog if applicable
                RateLimitDialog.showIfNeeded(requireContext(), state.message)
            }
        }
    }
    
    /**
     * Show skeleton loading animation
     */
    private fun showSkeleton() {
        shimmerLayout?.apply {
            visibility = View.VISIBLE
            startShimmer()
        }
    }
    
    /**
     * Hide skeleton loading animation
     */
    private fun hideSkeleton() {
        shimmerLayout?.apply {
            stopShimmer()
            visibility = View.GONE
        }
    }

    private fun updateSections(apps: List<AppItem>) {
        if (apps.isEmpty()) return

        // Sort by stars for featured/trending
        val sortedByStars = apps.sortedByDescending { it.repo.stars }
        
        // Featured: top apps by stars (show at least some)
        val featured = sortedByStars.take(minOf(5, apps.size))
        featuredAdapter.submitList(featured)
        setupWormDotsIndicator()

        // Trending: mix of top apps (may overlap for small sets)
        val trending = if (apps.size > 5) {
            sortedByStars.drop(3).take(10)
        } else {
            sortedByStars.take(10)
        }
        trendingAdapter.submitList(trending.ifEmpty { sortedByStars.take(10) })

        // Recently updated: sorted by update date
        val updated = apps.sortedByDescending { it.repo.updatedAt }.take(10)
        updatedAdapter.submitList(updated.ifEmpty { sortedByStars.take(10) })
    }

    private fun navigateToDetail(appItem: AppItem) {
        val intent = DetailActivity.newIntent(
            requireContext(),
            appItem.repo.owner.login,
            appItem.repo.name
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerLayout = null
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
