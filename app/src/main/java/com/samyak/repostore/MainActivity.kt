package com.samyak.repostore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.samyak.repostore.databinding.ActivityMainBinding
import com.samyak.repostore.ui.fragment.GameFragment
import com.samyak.repostore.ui.fragment.HomeFragment
import com.samyak.repostore.ui.fragment.SearchFragment
import com.samyak.repostore.ui.fragment.SettingsFragment
import com.samyak.repostore.ui.fragment.TrendingFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        applyWindowInsets()

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(GameFragment.newInstance())
        }
    }

    /**
     * Edge-to-edge insets: the shell takes the status bar and the horizontal
     * cutouts. The bottom inset is deliberately left unconsumed and passed
     * through so BottomNavigationView can pad itself above the system
     * navigation bar, which it does out of the box.
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }

    /**
     * Ask for POST_NOTIFICATIONS on Android 13+ so update-available
     * notifications can be shown.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_games -> {
                    loadFragment(GameFragment.newInstance())
                    true
                }
                R.id.nav_apps -> {
                    loadFragment(HomeFragment.newInstance())
                    true
                }
                R.id.nav_trending -> {
                    loadFragment(TrendingFragment.newInstance())
                    true
                }
                R.id.nav_search -> {
                    loadFragment(SearchFragment.newInstance())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment.newInstance())
                    true
                }
                else -> false
            }
        }

        // Tapping the tab you are already on should not rebuild the fragment and
        // throw away its scroll position, which is how the Play Store behaves.
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
