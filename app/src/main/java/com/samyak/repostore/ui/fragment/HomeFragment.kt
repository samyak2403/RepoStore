package com.samyak.repostore.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
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
import com.samyak.repostore.ui.activity.CategoriesActivity
import com.samyak.repostore.ui.activity.DetailActivity
import com.samyak.repostore.ui.adapter.AppShelf
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
    private lateinit var sectionFeatured: SectionAppCarouselBinding

    // Dynamic category sections: category -> (section view, binding, adapter)
    private val categorySections = mutableMapOf<AppCategory, Triple<View, SectionAppListBinding, PlayStoreAppAdapter>>()

    // Shelf style per category, so the page alternates between three-row lists
    // and artwork tiles the way the Play Store home page does.
    private val categoryShelfStyles = mutableMapOf<AppCategory, AppShelf.Style>()

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

        shimmerLayout = view.findViewById(R.id.skeleton_layout)

        bindSections()
        setupSearchBar()
        setupCategoryTabs()
        setupFeaturedCarousel()
        setupCategorySections()
        setupFeaturedSeeMore()
        setupSwipeRefresh()
        setupErrorRetry()
        setupScrollListener()
        observeViewModel()
    }

    private fun bindSections() {
        sectionFeatured = SectionAppCarouselBinding.bind(binding.sectionFeatured.root)
        sectionFeatured.tvSectionTitle.text = getString(R.string.recommended_for_you)
    }

    private fun setupSearchBar() {
        binding.cardSearch.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SearchFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    /**
     * Setup category tabs - show first 5 categories + "More" tab.
     * Tapping a category tab scrolls to that section.
     * Tapping "More" opens CategoriesActivity with all categories.
     */
    private fun setupCategoryTabs() {
        val allCategories = viewModel.displayCategories
        val visibleCategories = allCategories.take(5)

        // Add "All" tab first (scrolls to top)
        binding.tabCategories.addTab(
            binding.tabCategories.newTab().setText(getString(AppCategory.ALL.titleRes))
        )

        // Add first 5 category tabs
        visibleCategories.forEach { category ->
            binding.tabCategories.addTab(
                binding.tabCategories.newTab().setText(getString(category.titleRes))
            )
        }

        // Add "More" tab as the 6th option
        binding.tabCategories.addTab(
            binding.tabCategories.newTab().setText(getString(R.string.more))
        )

        binding.tabCategories.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    when {
                        it.position == 0 -> {
                            // "All" tab - scroll to top
                            binding.scrollView.smoothScrollTo(0, 0)
                        }
                        it.position <= visibleCategories.size -> {
                            // Category tab - scroll to section
                            val category = visibleCategories[it.position - 1]
                            viewModel.loadCategoryIfNeeded(category)
                            scrollToCategorySection(category)
                        }
                        else -> {
                            // "More" tab - open CategoriesActivity
                            startActivity(CategoriesActivity.newIntent(requireContext()))
                            // Reset tab selection to previous tab
                            binding.tabCategories.post {
                                binding.tabCategories.getTabAt(0)?.select()
                            }
                        }
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.let {
                    when {
                        it.position == 0 -> {
                            binding.scrollView.smoothScrollTo(0, 0)
                        }
                        it.position <= visibleCategories.size -> {
                            val category = visibleCategories[it.position - 1]
                            scrollToCategorySection(category)
                        }
                        else -> {
                            startActivity(CategoriesActivity.newIntent(requireContext()))
                        }
                    }
                }
            }
        })
    }

    /**
     * Scroll the NestedScrollView to the given category's section
     */
    private fun scrollToCategorySection(category: AppCategory) {
        categorySections[category]?.let { (sectionView, _, _) ->
            // Ensure the section is visible first
            if (sectionView.visibility == View.GONE) {
                sectionView.visibility = View.VISIBLE
            }
            sectionView.post {
                binding.scrollView.smoothScrollTo(0, sectionView.top)
            }
        }
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
        }
    }

    private fun setupWormDotsIndicator() {
        sectionFeatured.wormDotsIndicator.attachTo(sectionFeatured.viewpagerFeatured)
    }

    /**
     * Dynamically create a section_app_list for each AppCategory
     */
    private fun setupCategorySections() {
        val contentLayout = binding.contentLayout
        val displayCategories = viewModel.displayCategories
        val density = resources.displayMetrics.density

        displayCategories.forEachIndexed { index, category ->
            // Every other shelf is a tile strip, mirroring how the Play Store
            // breaks up its stacked row lists with artwork rows.
            val shelfStyle =
                if (index % 2 == 0) AppShelf.Style.ROWS else AppShelf.Style.TILES
            categoryShelfStyles[category] = shelfStyle

            val sectionView = layoutInflater.inflate(
                R.layout.section_app_list,
                contentLayout,
                false
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (16 * density).toInt()
            sectionView.layoutParams = params

            val sectionBinding = SectionAppListBinding.bind(sectionView)
            sectionBinding.tvSectionTitle.text = getString(category.titleRes)

            val adapter = PlayStoreAppAdapter(shelfStyle) { appItem ->
                navigateToDetail(appItem)
            }
            sectionBinding.rvApps.apply {
                this.adapter = adapter
                AppShelf.setup(this, shelfStyle)
            }

            // Hide until data is loaded
            sectionView.visibility = View.GONE

            // See more button navigates to full app list for this category
            sectionBinding.btnSeeMore.setOnClickListener {
                val intent = AppListActivity.newIntent(
                    requireContext(),
                    ListType.CATEGORY,
                    getString(category.titleRes),
                    category.name
                )
                startActivity(intent)
            }

            contentLayout.addView(sectionView)
            categorySections[category] = Triple(sectionView, sectionBinding, adapter)
        }
    }

    private fun setupFeaturedSeeMore() {
        sectionFeatured.btnSeeMore.setOnClickListener {
            val intent = AppListActivity.newIntent(
                requireContext(),
                ListType.FEATURED,
                getString(R.string.recommended_for_you),
                AppCategory.ALL.name
            )
            startActivity(intent)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            categorySections.values.forEach { (sectionView, _, _) ->
                sectionView.visibility = View.GONE
            }
            viewModel.refresh()
        }
    }

    private fun setupErrorRetry() {
        binding.tvError.setOnClickListener {
            viewModel.retry()
        }
    }

    /**
     * Lazy-load more categories as user scrolls down
     */
    private fun setupScrollListener() {
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                val child = binding.scrollView.getChildAt(0) ?: return@setOnScrollChangeListener
                val scrollRange = child.height - binding.scrollView.height
                if (scrollRange > 0 && scrollY > scrollRange * 0.6) {
                    viewModel.loadMoreCategories()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                launch {
                    viewModel.categoryApps.collect { categoryAppsMap ->
                        updateCategorySections(categoryAppsMap)
                    }
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
                binding.tvError.text = "${getString(R.string.no_apps_found)}\n\n${getString(R.string.tap_to_retry)}"
            }

            is HomeUiState.LoadingMore -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateFeaturedSection(state.currentApps)
            }

            is HomeUiState.Success -> {
                hideSkeleton()
                binding.scrollView.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                updateFeaturedSection(state.apps)
            }

            is HomeUiState.Error -> {
                hideSkeleton()
                binding.scrollView.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "${state.message}\n\n${getString(R.string.tap_to_retry)}"
                RateLimitDialog.showIfNeeded(requireContext(), state.message)
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

    private fun updateFeaturedSection(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        val sortedByStars = apps.sortedByDescending { it.repo.stars }
        val featured = sortedByStars.take(minOf(5, apps.size))
        featuredAdapter.submitList(featured)
        setupWormDotsIndicator()
    }

    private fun updateCategorySections(categoryAppsMap: Map<AppCategory, List<AppItem>>) {
        categoryAppsMap.forEach { (category, apps) ->
            categorySections[category]?.let { (sectionView, _, adapter) ->
                if (apps.isNotEmpty()) {
                    // Whole columns only, so a row shelf never trails a stub column.
                    val style = categoryShelfStyles[category] ?: AppShelf.Style.ROWS
                    adapter.submitList(AppShelf.trimToShelf(apps, style))
                    sectionView.visibility = View.VISIBLE
                }
            }
        }
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
        categorySections.clear()
        shimmerLayout = null
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
