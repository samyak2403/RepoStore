package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.samyak.repostore.R
import com.samyak.repostore.databinding.ActivityScreenshotViewerBinding
import com.samyak.repostore.ui.adapter.ScreenshotPagerAdapter
import com.samyak.repostore.util.applyTransition

class ScreenshotViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenshotViewerBinding
    private var isToolbarVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityScreenshotViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViewPager()
        setupBackPress()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finishWithAnimation()
        }
    }

    private fun setupViewPager() {
        val screenshots = intent.getStringArrayListExtra(EXTRA_SCREENSHOTS) ?: arrayListOf()
        val initialPosition = intent.getIntExtra(EXTRA_POSITION, 0)

        if (screenshots.isEmpty()) {
            finish()
            return
        }

        val adapter = ScreenshotPagerAdapter(screenshots) {
            toggleToolbarVisibility()
        }
        binding.viewpagerScreenshots.adapter = adapter

        binding.viewpagerScreenshots.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position, screenshots.size)
            }
        })

        binding.viewpagerScreenshots.setCurrentItem(initialPosition, false)
        updatePageIndicator(initialPosition, screenshots.size)
    }

    private fun toggleToolbarVisibility() {
        isToolbarVisible = !isToolbarVisible
        val alpha = if (isToolbarVisible) 1f else 0f
        binding.toolbar.animate().alpha(alpha).setDuration(200).start()
        binding.tvPageIndicator.animate().alpha(alpha).setDuration(200).start()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })
    }

    private fun finishWithAnimation() {
        finish()
        applyTransition(isOpen = false, R.anim.fade_in, R.anim.slide_down)
    }

    private fun updatePageIndicator(position: Int, total: Int) {
        binding.tvPageIndicator.text = getString(R.string.page_indicator, position + 1, total)
    }

    companion object {
        private const val EXTRA_SCREENSHOTS = "screenshots"
        private const val EXTRA_POSITION = "position"

        fun newIntent(context: Context, screenshots: ArrayList<String>, position: Int): Intent {
            return Intent(context, ScreenshotViewerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_SCREENSHOTS, screenshots)
                putExtra(EXTRA_POSITION, position)
            }
        }
    }
}
