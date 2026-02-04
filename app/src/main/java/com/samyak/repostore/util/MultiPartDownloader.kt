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
 * Uses HTTP Range requests to split downloads into chunks
 */
class MultiPartDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "MultiPartDownloader"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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
     * Download a file using multi-part downloading
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
        
        try {
            // Apply mirror proxy if enabled
            val downloadUrl = DownloadPreferences.transformUrl(context, url)
            Log.d(TAG, "Starting download: $downloadUrl")
            
            // Delete existing file
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadDir, fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            // Get file size and check Range support
            val fileInfo = getFileInfo(downloadUrl)
            if (fileInfo == null) {
                withContext(Dispatchers.Main) {
                    onStateChanged(DownloadState.Error("Failed to get file info"))
                }
                return@withContext
            }
            
            val (totalSize, supportsRange) = fileInfo
            Log.d(TAG, "File size: $totalSize, Supports Range: $supportsRange")
            
            // Determine thread count
            val threadCount = if (supportsRange && DownloadPreferences.isMultiPartEnabled(context)) {
                DownloadPreferences.getThreadCount(context)
            } else {
                1
            }
            
            Log.d(TAG, "Using $threadCount threads for download")
            
            if (threadCount == 1 || !supportsRange) {
                // Single-threaded download
                downloadSinglePart(downloadUrl, targetFile, totalSize, onStateChanged)
            } else {
                // Multi-part download
                downloadMultiPart(downloadUrl, targetFile, totalSize, threadCount, onStateChanged)
            }
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled")
            withContext(Dispatchers.Main) {
                onStateChanged(DownloadState.Idle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            withContext(Dispatchers.Main) {
                onStateChanged(DownloadState.Error(e.message ?: "Download failed"))
            }
        }
    }
    
    /**
     * Get file info via HEAD request
     * @return Pair of (fileSize, supportsRange) or null on error
     */
    private fun getFileInfo(url: String): Pair<Long, Boolean>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HEAD request failed: ${response.code}")
                    return null
                }
                
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = response.header("Accept-Ranges")
                val supportsRange = acceptRanges?.equals("bytes", ignoreCase = true) == true
                
                if (contentLength <= 0) {
                    // Try GET request with Range header to check support
                    return checkRangeWithGet(url)
                }
                
                Pair(contentLength, supportsRange)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
            null
        }
    }
    
    /**
     * Fallback: Check Range support with GET request
     */
    private fun checkRangeWithGet(url: String): Pair<Long, Boolean>? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                val supportsRange = response.code == 206
                val contentRange = response.header("Content-Range")
                val totalSize = contentRange?.substringAfter("/")?.toLongOrNull() ?: -1L
                
                if (totalSize > 0) {
                    Pair(totalSize, supportsRange)
                } else {
                    // Can't determine size, fallback to single-part
                    Pair(-1L, false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Range support", e)
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
        var downloadedBytes = 0L
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            
            val actualTotalSize = response.body?.contentLength() ?: totalSize
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) throw CancellationException()
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Calculate progress
                        val progress = if (actualTotalSize > 0) {
                            ((downloadedBytes * 100) / actualTotalSize).toInt().coerceIn(0, 100)
                        } else 0
                        
                        // Calculate speed
                        val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                        val speed = if (elapsedSec > 0) (downloadedBytes / elapsedSec).toLong() else 0L
                        
                        withContext(Dispatchers.Main) {
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
        
        withContext(Dispatchers.Main) {
            onStateChanged(DownloadState.Completed(targetFile))
        }
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
        
        // Download all chunks in parallel
        val jobs = chunks.mapIndexed { index, range ->
            async {
                downloadChunk(url, targetFile, range, index) { bytesDownloaded ->
                    downloadedBytes.addAndGet(bytesDownloaded)
                    
                    val progress = ((downloadedBytes.get() * 100) / totalSize).toInt().coerceIn(0, 100)
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsedSec > 0) (downloadedBytes.get() / elapsedSec).toLong() else 0L
                    
                    // Throttle UI updates (every ~100ms worth of data)
                    if (bytesDownloaded > BUFFER_SIZE * 10) {
                        launch(Dispatchers.Main) {
                            onStateChanged(DownloadState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes.get(),
                                totalBytes = totalSize,
                                speedBytesPerSec = speed
                            ))
                        }
                    }
                }
            }
        }
        
        // Wait for all chunks to complete
        jobs.awaitAll()
        
        // Final progress update
        withContext(Dispatchers.Main) {
            onStateChanged(DownloadState.Completed(targetFile))
        }
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
