package com.samyak.repostore.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.data.auth.PendingDeviceFlow
import com.samyak.repostore.data.auth.SessionResetReason
import com.samyak.repostore.databinding.ActivityGithubSignInBinding
import com.samyak.repostore.util.openExternalUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * GitHub Device Flow Sign In Activity
 *
 * Flow:
 * 1. Check existing signed-in session (and fetch profile if missing)
 * 2. Check for an unexpired PendingDeviceFlow so browser handoffs never discard the code
 * 3. Otherwise request a new device code, persist it, copy & open GitHub, and poll RFC 8628
 * 4. Save encrypted token, refresh RetrofitClient auth, and optionally fall back to PAT
 */
class GitHubSignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGithubSignInBinding
    private var pollingJob: Job? = null
    private var flowJob: Job? = null

    private val tokenFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (GitHubAuth.isSignedIn(this)) {
            onSignInComplete(fetchProfileIfMissing = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGithubSignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupClickListeners()
        initializeAuthFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        flowJob?.cancel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.btnCopyCode.setOnClickListener {
            val code = binding.tvUserCode.text.toString()
            if (code.isNotEmpty() && code != "--------") {
                copyToClipboard(code)
                Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenGithub.setOnClickListener {
            openGitHubVerification()
        }

        binding.btnNewCode.setOnClickListener {
            startDeviceFlow(forceNew = true)
        }

        binding.btnUseToken.setOnClickListener {
            openTokenFallback()
        }

        binding.btnErrorUseToken.setOnClickListener {
            openTokenFallback()
        }

        binding.btnSignOut.setOnClickListener {
            signOut()
        }

        binding.btnRetry.setOnClickListener {
            startDeviceFlow(forceNew = true)
        }
    }

    private fun openTokenFallback() {
        tokenFallbackLauncher.launch(GitHubTokenActivity.newIntent(this))
    }

    private fun initializeAuthFlow() {
        if (GitHubAuth.isSignedIn(this)) {
            onSignInComplete(fetchProfileIfMissing = GitHubAuth.getUser(this) == null)
            return
        }

        updateResetNoticeBanner()

        // Resume any unexpired device flow so switching to Chrome/Edge never wipes the active code.
        val pending = GitHubAuth.getPendingDeviceFlow(this)
        if (pending != null) {
            showCodeState(pending.userCode, pending.verificationUri)
            startPolling(pending.deviceCode, pending.intervalSeconds, pending.expiresAtEpochMs)
        } else {
            startDeviceFlow(forceNew = false)
        }
    }

    private fun updateResetNoticeBanner() {
        when (GitHubAuth.getSessionResetReason(this)) {
            SessionResetReason.CORRUPTED_OR_RESTORED -> {
                binding.cardResetNotice.visibility = View.VISIBLE
                binding.tvResetNotice.setText(R.string.reauth_reset_notice_reinstall)
            }
            SessionResetReason.TOKEN_REVOKED -> {
                binding.cardResetNotice.visibility = View.VISIBLE
                binding.tvResetNotice.setText(R.string.reauth_reset_notice_revoked)
            }
            null -> {
                binding.cardResetNotice.visibility = View.GONE
            }
        }
    }

    private fun startDeviceFlow(forceNew: Boolean = false) {
        pollingJob?.cancel()
        flowJob?.cancel()
        if (forceNew) {
            GitHubAuth.clearPendingDeviceFlow(this)
        }

        showLoadingState()

        flowJob = lifecycleScope.launch {
            try {
                when (val result = GitHubAuth.requestDeviceCodeResult()) {
                    is GitHubAuth.DeviceCodeResult.Success -> {
                        val deviceCode = result.response
                        val expiresAt = System.currentTimeMillis() +
                            (deviceCode.expiresIn.coerceAtLeast(60) * 1000L)
                        val pending = PendingDeviceFlow(
                            deviceCode = deviceCode.deviceCode,
                            userCode = deviceCode.userCode,
                            verificationUri = deviceCode.verificationUri,
                            intervalSeconds = deviceCode.interval.coerceAtLeast(5),
                            expiresAtEpochMs = expiresAt
                        )
                        GitHubAuth.savePendingDeviceFlow(this@GitHubSignInActivity, pending)
                        updateResetNoticeBanner()
                        showCodeState(pending.userCode, pending.verificationUri)
                        startPolling(pending.deviceCode, pending.intervalSeconds, pending.expiresAtEpochMs)
                    }
                    is GitHubAuth.DeviceCodeResult.Error -> {
                        showErrorState(result.message.ifBlank { getString(R.string.failed_to_get_code) })
                    }
                }
            } catch (e: Exception) {
                showErrorState(e.message ?: getString(R.string.unknown_error))
            }
        }
    }

    private fun startPolling(deviceCode: String, intervalSeconds: Int, expiresAtEpochMs: Long) {
        pollingJob?.cancel()

        pollingJob = lifecycleScope.launch {
            when (val result = GitHubAuth.pollForTokenResult(deviceCode, intervalSeconds, expiresAtEpochMs)) {
                is GitHubAuth.PollResult.Success -> {
                    val token = result.tokenResponse.accessToken
                    if (token.isNullOrBlank()) {
                        GitHubAuth.clearPendingDeviceFlow(this@GitHubSignInActivity)
                        showErrorState(getString(R.string.unknown_error))
                        return@launch
                    }

                    val saved = GitHubAuth.saveToken(this@GitHubSignInActivity, token)
                    if (!saved) {
                        showErrorState(getString(R.string.token_save_failed))
                        return@launch
                    }

                    refreshAppAuth()
                    setResult(RESULT_OK)
                    fetchUserInfo(token)
                }
                is GitHubAuth.PollResult.Expired -> {
                    GitHubAuth.clearPendingDeviceFlow(this@GitHubSignInActivity)
                    showErrorState(getString(R.string.auth_expired))
                }
                is GitHubAuth.PollResult.AccessDenied -> {
                    GitHubAuth.clearPendingDeviceFlow(this@GitHubSignInActivity)
                    showErrorState(getString(R.string.auth_denied))
                }
                is GitHubAuth.PollResult.Error -> {
                    GitHubAuth.clearPendingDeviceFlow(this@GitHubSignInActivity)
                    showErrorState(result.message.ifBlank { getString(R.string.unknown_error) })
                }
            }
        }
    }

    private fun onSignInComplete(fetchProfileIfMissing: Boolean) {
        pollingJob?.cancel()
        refreshAppAuth()
        setResult(RESULT_OK)
        showSignedInState()
        if (fetchProfileIfMissing) {
            val token = GitHubAuth.getToken(this) ?: return
            lifecycleScope.launch {
                fetchUserInfo(token, showWelcomeToast = false)
            }
        }
    }

    private fun refreshAppAuth() {
        val app = application as? RepoStoreApp
        if (app != null) {
            app.refreshAuth()
        } else {
            RetrofitClient.refreshAuth()
        }
    }

    private suspend fun fetchUserInfo(token: String, showWelcomeToast: Boolean = true) {
        try {
            val user = GitHubAuth.fetchUser(token)
            if (user != null) {
                GitHubAuth.saveUser(this, user)
                showSignedInState()
                if (showWelcomeToast) {
                    Toast.makeText(this, getString(R.string.signed_in_as, user.login), Toast.LENGTH_SHORT).show()
                }
            } else {
                showSignedInState()
            }
        } catch (_: Exception) {
            showSignedInState()
        }
    }

    private fun showLoadingState() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutCode.visibility = View.GONE
        binding.layoutSignedIn.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
    }

    private fun showCodeState(userCode: String, verificationUri: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutCode.visibility = View.VISIBLE
        binding.layoutSignedIn.visibility = View.GONE
        binding.layoutError.visibility = View.GONE

        binding.tvUserCode.text = userCode
        binding.tvVerificationUrl.text = verificationUri
    }

    private fun showSignedInState() {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutCode.visibility = View.GONE
        binding.layoutSignedIn.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE

        binding.tvUserInfo.text = getString(R.string.rate_limit_increased)

        val user = GitHubAuth.getUser(this)
        if (user != null) {
            val displayName = user.name?.takeIf { it.isNotBlank() } ?: user.login
            binding.tvDisplayName.text = displayName
            binding.tvDisplayName.visibility = View.VISIBLE

            binding.tvUsername.text = getString(R.string.user_handle, user.login)
            binding.tvUsername.visibility = View.VISIBLE

            Glide.with(this)
                .load(user.avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_account)
                .error(R.drawable.ic_account)
                .into(binding.ivAvatar)
        } else {
            binding.tvDisplayName.text = getString(R.string.signed_in)
            binding.tvDisplayName.visibility = View.VISIBLE
            binding.tvUsername.visibility = View.GONE
            binding.ivAvatar.setImageResource(R.drawable.ic_account)
        }
    }

    private fun showErrorState(message: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.layoutCode.visibility = View.GONE
        binding.layoutSignedIn.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE

        binding.tvErrorMessage.text = message
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GitHub Code", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun openGitHubVerification() {
        val code = binding.tvUserCode.text.toString().trim()
        if (code.isNotEmpty() && code != "--------") {
            copyToClipboard(code)
            Toast.makeText(this, R.string.code_copied_open_hint, Toast.LENGTH_LONG).show()
        }
        val url = binding.tvVerificationUrl.text.toString().trim()
        if (url.isNotEmpty()) {
            openExternalUrl(url)
        }
    }

    private fun signOut() {
        pollingJob?.cancel()
        flowJob?.cancel()
        GitHubAuth.signOut(this)
        refreshAppAuth()
        Toast.makeText(this, R.string.signed_out, Toast.LENGTH_SHORT).show()
        updateResetNoticeBanner()
        startDeviceFlow(forceNew = true)
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, GitHubSignInActivity::class.java)
        }
    }
}
