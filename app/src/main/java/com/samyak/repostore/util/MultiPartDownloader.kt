package com.samyak.repostore.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.samyak.repostore.data.prefs.DownloadPreferences
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-Part Downloader - Downloads files using parallel connections
 * Uses HTTP Range requests to split downloads into chunks with reliable single-part fallback
 */
class MultiPartDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "MultiPartDownloader"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
        private const val HEAD_TIMEOUT = 10L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 150L
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                requestBuilder.header("User-Agent", "GitHubAppStore-Android")
            }
            if (original.header("Accept") == null) {
                requestBuilder.header("Accept", "*/*")
            }
            chain.proceed(requestBuilder.build())
        }
        .build()

    // Faster timeout client for pre-flight probes (HEAD / Range test)
    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(HEAD_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(HEAD_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    private var downloadJob: Job? = null
    private var isCancelled = false
    
    /**
     * Download state for progress tracking
     */
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(
            val progress: Int,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val speedBytesPerSec: Long
        ) : DownloadState()
        data class Completed(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /**
     * Get a safe, writable destination file for downloads.
     * Uses app-specific external files dir (no storage permissions required on Android 10+)
     * with fallback to app cache directory.
     */
    fun getDownloadTargetFile(fileName: String): File {
        val appDownloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (appDownloadsDir != null && (appDownloadsDir.exists() || appDownloadsDir.mkdirs())) {
            return File(appDownloadsDir, fileName)
        }
        val cacheDownloadsDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        return File(cacheDownloadsDir, fileName)
    }
    
    /**
     * Download a file using multi-part downloading with automatic fallback
     * @param url The URL to download from
     * @param fileName The name for the downloaded file
     * @param onStateChanged Callback for download progress updates
     */
    suspend fun download(
        url: String,
        fileName: String,
        onStateChanged: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        
        val targetFile = getDownloadTargetFile(fileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        // Also clean legacy public file if present and writable
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val legacyFile = File(publicDir, fileName)
            if (legacyFile.exists()) legacyFile.delete()
        } catch (_: Exception) {}

        // Determine URLs to try: proxy first (if enabled and differs), then direct URL fallback
        val urlsToTry = mutableListOf<String>()
        val proxyUrl = DownloadPreferences.transformUrl(context, url)
        if (proxyUrl != url && !proxyUrl.isNullOrBlank()) {
            urlsToTry.add(proxyUrl)
        }
        urlsToTry.add(url)

        var lastException: Exception? = null

        for (downloadUrl in urlsToTry) {
            if (isCancelled) break
            try {
                Log.d(TAG, "Starting download attempt: $downloadUrl")
                
                // Probe file size and Range support
                val fileInfo = getFileInfo(downloadUrl)
                val totalSize = fileInfo?.first ?: -1L
                val supportsRange = fileInfo?.second ?: false
                
                Log.d(TAG, "Probe result -> size: $totalSize, supportsRange: $supportsRange")
                
                val threadCount = if (supportsRange && totalSize > 0 && DownloadPreferences.isMultiPartEnabled(context)) {
                    DownloadPreferences.getThreadCount(context)
                } else {
                    1
                }
                
                Log.d(TAG, "Using $threadCount threads for download")
                
                if (threadCount <= 1 || !supportsRange || totalSize <= 0) {
                    // Single-threaded download
                    downloadSinglePart(downloadUrl, targetFile, totalSize, onStateChanged)
                } else {
                    // Multi-part download with fallback to single-part if chunking fails
                    try {
                        downloadMultiPart(downloadUrl, targetFile, totalSize, threadCount, onStateChanged)
                    } catch (e: Exception) {
                        if (isCancelled) throw e
                        Log.w(TAG, "Multi-part download failed ($e), falling back to single-part GET")
                        if (targetFile.exists()) targetFile.delete()
                        downloadSinglePart(downloadUrl, targetFile, totalSize, onStateChanged)
                    }
                }

                // If completed without throwing, return successfully
                return@withContext
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled")
                if (targetFile.exists()) targetFile.delete()
                onStateChanged(DownloadState.Idle)
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed for $downloadUrl: ${e.message}")
                lastException = e
                if (targetFile.exists()) targetFile.delete()
            }
        }
        
        Log.e(TAG, "All download attempts failed", lastException)
        onStateChanged(DownloadState.Error(lastException?.message ?: "Download failed"))
    }
    
    /**
     * Get file info via HEAD request, with fallback to Range GET
     * @return Pair of (fileSize, supportsRange) or null if neither works
     */
    private fun getFileInfo(url: String): Pair<Long, Boolean>? {
        // Step 1: Try lightweight HEAD request
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            
            probeClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = response.header("Accept-Ranges")
                    val supportsRange = acceptRanges?.equals("bytes", ignoreCase = true) == true
                    
                    if (contentLength > 0) {
                        return Pair(contentLength, supportsRange)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD request failed for $url: ${e.message}")
        }

        // Step 2: Fallback to Range GET request (bytes=0-0)
        return checkRangeWithGet(url)
    }
    
    /**
     * Fallback: Check Range support and size with GET Range request
     */
    private fun checkRangeWithGet(url: String): Pair<Long, Boolean>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()
            
            probeClient.newCall(request).execute().use { response ->
                if (response.code == 206) {
                    val contentRange = response.header("Content-Range")
                    val totalSize = contentRange?.substringAfter("/")?.toLongOrNull() ?: -1L
                    if (totalSize > 0) {
                        Pair(totalSize, true)
                    } else {
                        Pair(-1L, true)
                    }
                } else if (response.isSuccessful) {
                    val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    Pair(contentLength, false)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Range GET probe failed for $url: ${e.message}")
            null
        }
    }
    
    /**
     * Single-threaded download
     */
    private suspend fun downloadSinglePart(
        url: String,
        targetFile: File,
        totalSize: Long,
        onStateChanged: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        
        val startTime = System.currentTimeMillis()
        var lastUpdateTime = 0L
        var downloadedBytes = 0L
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            val actualTotalSize = response.body?.contentLength()?.takeIf { it > 0 } ?: totalSize
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) throw CancellationException()
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS) {
                            lastUpdateTime = now
                            val progress = if (actualTotalSize > 0) {
                                ((downloadedBytes * 100) / actualTotalSize).toInt().coerceIn(0, 100)
                            } else 0
                            
                            val elapsedSec = (now - startTime) / 1000.0
                            val speed = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                            
                            onStateChanged(DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = actualTotalSize,
                                speedBytesPerSec = speed
                            ))
                        }
                    }
                }
            }
        }
        
        onStateChanged(DownloadState.Completed(targetFile))
    }
    
    /**
     * Multi-part parallel download
     */
    private suspend fun downloadMultiPart(
        url: String,
        targetFile: File,
        totalSize: Long,
        threadCount: Int,
        onStateChanged: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Create target file with full size
        RandomAccessFile(targetFile, "rw").use { raf ->
            raf.setLength(totalSize)
        }
        
        // Calculate chunk sizes
        val chunkSize = totalSize / threadCount
        val chunks = mutableListOf<LongRange>()
        
        for (i in 0 until threadCount) {
            val start = i * chunkSize
            val end = if (i == threadCount - 1) totalSize - 1 else (start + chunkSize - 1)
            chunks.add(start..end)
        }
        
        // Track progress
        val downloadedBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        var lastUpdateTime = 0L
        
        // Download all chunks in parallel
        val jobs = chunks.mapIndexed { index, range ->
            async {
                downloadChunk(url, targetFile, range, index) { bytesChunkRead ->
                    val totalDownloaded = downloadedBytes.addAndGet(bytesChunkRead)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL_MS) {
                        lastUpdateTime = now
                        val progress = ((totalDownloaded * 100) / totalSize).toInt().coerceIn(0, 100)
                        val elapsedSec = (now - startTime) / 1000.0
                        val speed = if (elapsedSec > 0) (totalDownloaded / elapsedSec).toLong() else 0L
                        
                        onStateChanged(DownloadState.Downloading(
                            progress = progress,
                            downloadedBytes = totalDownloaded,
                            totalBytes = totalSize,
                            speedBytesPerSec = speed
                        ))
                    }
                }
            }
        }
        
        // Wait for all chunks to complete
        jobs.awaitAll()
        
        // Final progress update
        onStateChanged(DownloadState.Completed(targetFile))
    }
    
    /**
     * Download a single chunk using Range header
     */
    private suspend fun downloadChunk(
        url: String,
        targetFile: File,
        range: LongRange,
        chunkIndex: Int,
        onProgress: (Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=${range.first}-${range.last}")
            .build()
        
        Log.d(TAG, "Chunk $chunkIndex: bytes=${range.first}-${range.last}")
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("Chunk $chunkIndex failed: HTTP ${response.code}")
            }
            
            response.body?.byteStream()?.use { input ->
                RandomAccessFile(targetFile, "rw").use { raf ->
                    raf.seek(range.first)
                    
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalChunkBytes = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) throw CancellationException()
                        
                        raf.write(buffer, 0, bytesRead)
                        totalChunkBytes += bytesRead
                        onProgress(bytesRead.toLong())
                    }
                    
                    Log.d(TAG, "Chunk $chunkIndex completed: $totalChunkBytes bytes")
                }
            }
        }
    }
    
    /**
     * Cancel ongoing download
     */
    fun cancel() {
        isCancelled = true
        downloadJob?.cancel()
    }
}
