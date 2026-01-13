package com.samyak.repostore.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Professional App Installer - Inspired by GitHub Store project
 * Handles APK download and installation with proper state management
 */
class AppInstaller private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppInstaller"
        private const val PROGRESS_INTERVAL = 300L

        @Volatile
        private var instance: AppInstaller? = null

        fun getInstance(context: Context): AppInstaller {
            return instance ?: synchronized(this) {
                instance ?: AppInstaller(context.applicationContext).also { instance = it }
            }
        }
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var currentDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressRunnable: Runnable? = null
    private var isDownloading = false
    private var stateCallback: ((InstallState) -> Unit)? = null

    /**
     * Installation states
     */
    sealed class InstallState {
        object Idle : InstallState()
        data class Downloading(
            val progress: Int,
            val downloaded: String,
            val total: String
        ) : InstallState()
        object Installing : InstallState()
        object Success : InstallState()
        data class Error(val message: String) : InstallState()
    }

    /**
     * Start download and install APK
     */
    fun download(
        url: String,
        fileName: String,
        title: String,
        onStateChanged: (InstallState) -> Unit
    ) {
        // Prevent multiple downloads
        if (isDownloading) {
            mainHandler.post {
                onStateChanged(InstallState.Error("Download already in progress"))
            }
            return
        }

        // Store callback
        stateCallback = onStateChanged
        isDownloading = true

        // Clean old file
        deleteFile(fileName)

        try {
            // Create request
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription("Downloading APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setMimeType("application/vnd.android.package-archive")
            }

            // Enqueue download
            currentDownloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started: $currentDownloadId, URL: $url")

            // Send initial downloading state
            mainHandler.post {
                onStateChanged(InstallState.Downloading(0, "0 B", "..."))
            }

            // Register receiver
            registerReceiver(fileName, onStateChanged)

            // Start progress tracking
            startProgressTracking(onStateChanged)

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            isDownloading = false
            mainHandler.post {
                onStateChanged(InstallState.Error(e.message ?: "Download failed"))
            }
        }
    }

    private fun registerReceiver(fileName: String, onStateChanged: (InstallState) -> Unit) {
        unregisterReceiver()

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                Log.d(TAG, "Download complete received: id=$id, currentId=$currentDownloadId")
                if (id == currentDownloadId) {
                    stopProgressTracking()
                    checkDownloadStatus(fileName, onStateChanged)
                }
            }
        }

        // For Android 13+ (TIRAMISU), use RECEIVER_EXPORTED for system broadcasts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun checkDownloadStatus(fileName: String, onStateChanged: (InstallState) -> Unit) {
        val query = DownloadManager.Query().setFilterById(currentDownloadId)
        var cursor: Cursor? = null

        try {
            cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d(TAG, "Download successful")
                        mainHandler.post {
                            onStateChanged(InstallState.Installing)
                        }
                        
                        val file = getDownloadFile(fileName)
                        if (file != null && file.exists()) {
                            val installStarted = installApk(file)
                            mainHandler.post {
                                if (installStarted) {
                                    onStateChanged(InstallState.Success)
                                } else {
                                    onStateChanged(InstallState.Error("Failed to start installation"))
                                }
                                cleanup()
                            }
                        } else {
                            Log.e(TAG, "Downloaded file not found: $fileName")
                            mainHandler.post {
                                onStateChanged(InstallState.Error("Downloaded file not found"))
                                cleanup()
                            }
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val errorMsg = getErrorMessage(reason)
                        Log.e(TAG, "Download failed: $errorMsg")
                        mainHandler.post {
                            onStateChanged(InstallState.Error(errorMsg))
                            cleanup()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Status check error", e)
            mainHandler.post {
                onStateChanged(InstallState.Error("Status check failed: ${e.message}"))
                cleanup()
            }
        } finally {
            cursor?.close()
        }
    }

    private fun startProgressTracking(onStateChanged: (InstallState) -> Unit) {
        stopProgressTracking()

        progressRunnable = object : Runnable {
            override fun run() {
                if (!isDownloading || currentDownloadId == -1L) return

                val query = DownloadManager.Query().setFilterById(currentDownloadId)
                var cursor: Cursor? = null

                try {
                    cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            val bytesDownloaded = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            )
                            val bytesTotal = cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            )

                            val progress = if (bytesTotal > 0) {
                                ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }

                            val downloadedStr = formatSize(bytesDownloaded)
                            val totalStr = if (bytesTotal > 0) formatSize(bytesTotal) else "..."

                            mainHandler.post {
                                onStateChanged(InstallState.Downloading(progress, downloadedStr, totalStr))
                            }

                            // Continue tracking
                            mainHandler.postDelayed(this, PROGRESS_INTERVAL)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Progress error", e)
                } finally {
                    cursor?.close()
                }
            }
        }

        mainHandler.postDelayed(progressRunnable!!, PROGRESS_INTERVAL)
    }

    private fun stopProgressTracking() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun installApk(file: File): Boolean {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
            
            Log.d(TAG, "Installing APK from: ${file.absolutePath}, URI: $uri")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Install error: ${e.message}", e)
            false
        }
    }

    /**
     * Cancel current download
     */
    fun cancel() {
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Cancel error", e)
            }
        }
        val callback = stateCallback
        cleanup()
        mainHandler.post {
            callback?.invoke(InstallState.Idle)
        }
    }

    private fun cleanup() {
        stopProgressTracking()
        unregisterReceiver()
        currentDownloadId = -1
        isDownloading = false
        stateCallback = null
    }

    private fun unregisterReceiver() {
        try {
            downloadReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore
        }
        downloadReceiver = null
    }

    private fun getDownloadFile(fileName: String): File? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        return if (file.exists()) file else null
    }

    private fun deleteFile(fileName: String) {
        try {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Delete error", e)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "No space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
            else -> "Download failed"
        }
    }

    // ==================== App Detection ====================

    /**
     * Check if package is installed
     */
    fun isInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launch app by package name
     */
    fun launch(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Launch error", e)
            false
        }
    }

    /**
     * Find installed package by repo/owner name
     */
    fun findPackage(repoName: String, ownerName: String): String? {
        val pm = context.packageManager
        
        // For Android 11+ (API 30+), we need QUERY_ALL_PACKAGES permission
        // or use specific package queries
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        val repo = repoName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val owner = ownerName.lowercase().replace(Regex("[^a-z0-9]"), "")

        // Search patterns
        val patterns = listOf(
            "com.$owner.$repo",
            "com.$repo",
            "org.$owner.$repo",
            "io.$owner.$repo",
            repo
        )

        for (app in apps) {
            val pkg = app.packageName.lowercase()
            for (pattern in patterns) {
                if (pattern.length >= 3 && pkg.contains(pattern)) {
                    return app.packageName
                }
            }
        }

        return null
    }
}
