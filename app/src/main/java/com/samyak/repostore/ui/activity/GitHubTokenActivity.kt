package com.samyak.repostore.ui.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.samyak.repostore.R
import com.samyak.repostore.RepoStoreApp
import com.samyak.repostore.data.api.RetrofitClient
import com.samyak.repostore.data.auth.GitHubAuth
import com.samyak.repostore.databinding.ActivityGithubTokenBinding
import com.samyak.repostore.util.openExternalUrl

class GitHubTokenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGithubTokenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityGithubTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViews()
        loadCurrentToken()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViews() {
        binding.btnSaveToken.setOnClickListener {
            val token = binding.etToken.text?.toString()?.trim()
            if (token.isNullOrEmpty()) {
                clearStoredToken()
                Toast.makeText(this, R.string.token_removed, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
                return@setOnClickListener
            }

            if (token.any { it.isWhitespace() } || token.contains("****")) {
                binding.etToken.error = getString(R.string.invalid_token_format)
                Toast.makeText(this, R.string.invalid_token_format, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val saved = GitHubAuth.saveToken(this, token)
            if (saved) {
                refreshAuth()
                Toast.makeText(this, R.string.token_saved, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, R.string.token_save_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearToken.setOnClickListener {
            binding.etToken.text?.clear()
            binding.etToken.error = null
            clearStoredToken()
            Toast.makeText(this, R.string.token_removed, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        }

        binding.btnCreateToken.setOnClickListener {
            openExternalUrl("https://github.com/settings/tokens/new")
        }
    }

    private fun clearStoredToken() {
        val app = application as? RepoStoreApp
        if (app != null) {
            app.setGitHubToken(null)
        } else {
            GitHubAuth.signOut(this)
            RetrofitClient.refreshAuth()
        }
    }

    private fun refreshAuth() {
        val app = application as? RepoStoreApp
        if (app != null) {
            app.refreshAuth()
        } else {
            RetrofitClient.refreshAuth()
        }
    }

    private fun loadCurrentToken() {
        val token = GitHubAuth.getToken(this)
        token?.let {
            if (it.length > 8) {
                binding.etToken.setText("${it.take(4)}****${it.takeLast(4)}")
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, GitHubTokenActivity::class.java)
        }
    }
}
