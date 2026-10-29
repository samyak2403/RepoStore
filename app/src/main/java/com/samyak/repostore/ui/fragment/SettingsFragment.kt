package com.samyak.repostore.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.samyak.repostore.R
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.prefs.ThemePreferences
import com.samyak.repostore.databinding.FragmentSettingsBinding
import com.samyak.repostore.ui.activity.AboutActivity
import com.samyak.repostore.ui.activity.AppDeveloperActivity
import com.samyak.repostore.ui.activity.AppUpdatesActivity
import com.samyak.repostore.ui.activity.DonateActivity
import com.samyak.repostore.ui.activity.GitHubSignInActivity
import com.samyak.repostore.ui.activity.LicensesActivity
import com.samyak.repostore.ui.activity.FavoriteActivity
import com.samyak.repostore.ui.activity.DownloadSettingsActivity


import com.samyak.repostore.ui.dialog.showLanguageDialog

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAccountSection()
        setupLanguageSection()
        setupAppearanceSection()
        setupMyAppsSection()
        setupAppUpdatesSection()
        setupDownloadSettingsSection()
        setupAboutSection()
        setupDeveloperSection()
        setupDonateSection()
        setupTelegramSection()
        setupLinkedInSection()
        setupLicensesSection()
        setupSourceCodeSection()
    }

    override fun onResume() {
        super.onResume()
        updateAccountStatus()
        // Refresh API client to pick up new auth
        RetrofitClient.refreshAuth()
    }

    private fun setupAccountSection() {
        updateAccountStatus()

        binding.accountCard.setOnClickListener {
            startActivity(Intent(requireContext(), GitHubSignInActivity::class.java))
        }
    }

    private fun updateAccountStatus() {
        val user = GitHubAuth.getUser(requireContext())
        val isSignedIn = GitHubAuth.isSignedIn(requireContext())

        if (isSignedIn && user != null) {
            // Show user info
            binding.tvAccountName.text = user.login
            binding.tvAccountStatus.text = getString(R.string.rate_limit_increased)
            
            // Load avatar
            if (!user.avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(user.avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_account)
                    .into(binding.ivAccountAvatar)
            }
        } else {
            // Show sign in prompt
            binding.tvAccountName.text = getString(R.string.github_sign_in)
            binding.tvAccountStatus.text = getString(R.string.sign_in_to_increase_limit)
            binding.ivAccountAvatar.setImageResource(R.drawable.ic_account)
        }
    }

    private fun setupLanguageSection() {
        updateCurrentLanguageText()

        binding.languageCard.setOnClickListener {
            showLanguageDialog()
        }
    }

    private fun updateCurrentLanguageText() {
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            binding.tvCurrentLanguage.text = getString(R.string.theme_system)
        } else {
            val currentLocale = currentLocales.get(0)
            if (currentLocale?.language == "hi" && currentLocale.country == "IN") {
                binding.tvCurrentLanguage.text = getString(R.string.hinglish)
            } else {
                binding.tvCurrentLanguage.text = currentLocale?.getDisplayName(currentLocale)
            }
        }
    }



    private fun setupAppearanceSection() {
        // Update selection state based on current theme
        updateThemeSelection()

        // Set click listeners for each theme option
        binding.themeSystemCard.setOnClickListener {
            selectTheme(ThemePreferences.THEME_SYSTEM)
        }

        binding.themeForYouCard.setOnClickListener {
            selectTheme(ThemePreferences.THEME_FOR_YOU)
        }

        binding.themeDarkCard.setOnClickListener {
            selectTheme(ThemePreferences.THEME_DARK)
        }

        binding.themeLightCard.setOnClickListener {
            selectTheme(ThemePreferences.THEME_LIGHT)
        }
    }

    private fun selectTheme(themeMode: Int) {
        ThemePreferences.setThemeMode(requireContext(), themeMode)
        updateThemeSelection()
    }

    private fun updateThemeSelection() {
        val currentTheme = ThemePreferences.getThemeMode(requireContext())
        
        val selectedBgColor = ContextCompat.getColor(requireContext(), R.color.theme_card_selected_bg)
        val selectedStrokeColor = ContextCompat.getColor(requireContext(), R.color.theme_card_selected_stroke)
        val selectedTintColor = ContextCompat.getColor(requireContext(), R.color.theme_card_selected_tint)

        val unselectedBgColor = ContextCompat.getColor(requireContext(), R.color.theme_card_unselected_bg)
        val unselectedTintColor = ContextCompat.getColor(requireContext(), R.color.theme_card_unselected_tint)
        
        val strokeWidth = resources.getDimensionPixelSize(R.dimen.theme_card_stroke_width)
        
        // Helper function to update card visual state with light color effect
        fun updateCard(card: MaterialCardView, icon: ImageView, text: TextView, isSelected: Boolean) {
            if (isSelected) {
                card.setCardBackgroundColor(selectedBgColor)
                card.strokeWidth = strokeWidth
                card.strokeColor = selectedStrokeColor
                icon.imageTintList = ColorStateList.valueOf(selectedTintColor)
                text.setTextColor(selectedTintColor)
            } else {
                card.setCardBackgroundColor(unselectedBgColor)
                card.strokeWidth = 0
                icon.imageTintList = ColorStateList.valueOf(unselectedTintColor)
                text.setTextColor(unselectedTintColor)
            }
        }
        
        updateCard(binding.themeSystemCard, binding.ivThemeSystem, binding.tvThemeSystem, currentTheme == ThemePreferences.THEME_SYSTEM)
        updateCard(binding.themeForYouCard, binding.ivThemeForYou, binding.tvThemeForYou, currentTheme == ThemePreferences.THEME_FOR_YOU)
        updateCard(binding.themeDarkCard, binding.ivThemeDark, binding.tvThemeDark, currentTheme == ThemePreferences.THEME_DARK)
        updateCard(binding.themeLightCard, binding.ivThemeLight, binding.tvThemeLight, currentTheme == ThemePreferences.THEME_LIGHT)
    }

    private fun setupMyAppsSection() {
        binding.myAppsCard.setOnClickListener {
            startActivity(Intent(requireContext(), FavoriteActivity::class.java))
        }
    }

    private fun setupAppUpdatesSection() {
        binding.appUpdatesCard.setOnClickListener {
            startActivity(Intent(requireContext(), AppUpdatesActivity::class.java))
        }
    }


    private fun setupDownloadSettingsSection() {
        binding.downloadSettingsCard.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadSettingsActivity::class.java))
        }
    }

    private fun setupAboutSection() {
        binding.aboutCard.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
    }

    private fun setupDeveloperSection() {
        binding.developerCard.setOnClickListener {
            startActivity(Intent(requireContext(), AppDeveloperActivity::class.java))
        }
    }

    private fun setupDonateSection() {
        binding.donateCard.setOnClickListener {
            startActivity(Intent(requireContext(), DonateActivity::class.java))
        }
    }

    private fun setupTelegramSection() {
        binding.telegramCard.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
                startActivity(intent)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    private fun setupLinkedInSection() {
        binding.linkedinCard.setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.linkedin_url))
                )
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    R.string.cannot_open_app,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupLicensesSection() {
        binding.licensesCard.setOnClickListener {
            startActivity(Intent(requireContext(), LicensesActivity::class.java))
        }
    }

    private fun setupSourceCodeSection() {
        binding.sourceCodeCard.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_CODE_URL))
                startActivity(intent)
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // TODO: Replace with your actual GitHub repository URL
        private const val SOURCE_CODE_URL = "https://github.com/samyak2403/RepoStore"
        private const val TELEGRAM_URL = "https://t.me/+q2rca77Sx9phYTE1"

        fun newInstance() = SettingsFragment()
    }
}
