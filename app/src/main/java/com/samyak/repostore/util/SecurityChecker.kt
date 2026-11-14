package com.samyak.repostore.util

import android.util.Log
import com.samyak.repostore.data.api.VirusTotalApi
import com.samyak.repostore.data.api.VirusTotalClient
import com.samyak.repostore.data.api.VirusTotalKeyManager
import com.samyak.repostore.data.model.ReleaseAsset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Result of a VirusTotal security lookup for an APK asset.
 */
sealed class SecurityStatus {
    /** Lookup in progress. */
    data object Checking : SecurityStatus()

    /** Scanned and no engine flagged the file as malicious. */
    data class Safe(val totalEngines: Int) : SecurityStatus()

    /** One or more engines flagged the file. */
    data class Flagged(val malicious: Int, val suspicious: Int, val totalEngines: Int) : SecurityStatus()

    /**
     * No antivirus scan was available, but GitHub publishes a SHA-256 checksum for the
     * asset, so its integrity can be verified against the official release. Shown to every
     * user regardless of whether a VirusTotal API key is configured.
     */
    data object Verified : SecurityStatus()

    /**
     * No scan or checksum available, but the asset still comes directly from an official
     * GitHub release. This is the baseline trust signal shown to all users.
     */
    data object Unknown : SecurityStatus()
}

/**
 * Constructor-injectable security verifier using the VirusTotal API by looking up the
 * asset's SHA-256 hash without uploading or downloading the binary.
 */
class ApkSecurityChecker(
    private val virusTotalApiProvider: () -> VirusTotalApi = { VirusTotalClient.api },
    private val isConfiguredProvider: () -> Boolean = { VirusTotalClient.isConfigured },
    private val ensureKeysLoaded: suspend () -> Unit = { VirusTotalKeyManager.ensureLoaded() },
    private val rotateKey: () -> Boolean = { VirusTotalKeyManager.rotate() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun check(asset: ReleaseAsset?): SecurityStatus = withContext(ioDispatcher) {
        if (asset == null) return@withContext SecurityStatus.Unknown

        val hash = asset.sha256

        if (!hash.isNullOrBlank()) {
            ensureKeysLoaded()

            if (isConfiguredProvider()) {
                while (true) {
                    try {
                        val response = virusTotalApiProvider().getFileReport(hash)
                        val stats = response.data?.attributes?.lastAnalysisStats
                        if (stats != null) {
                            val malicious = stats.malicious
                            val suspicious = stats.suspicious
                            return@withContext if (malicious > 0 || suspicious > 0) {
                                SecurityStatus.Flagged(malicious, suspicious, stats.totalEngines)
                            } else {
                                SecurityStatus.Safe(stats.totalEngines)
                            }
                        }
                        break
                    } catch (e: HttpException) {
                        Log.d(TAG, "VirusTotal lookup failed (${e.code()}) for $hash")
                        val recoverable = e.code() == 429 || e.code() == 401
                        if (recoverable && rotateKey()) {
                            continue
                        }
                        break
                    } catch (e: Exception) {
                        Log.d(TAG, "VirusTotal lookup error: ${e.message}")
                        break
                    }
                }
            }
        }

        return@withContext if (!hash.isNullOrBlank()) {
            SecurityStatus.Verified
        } else {
            SecurityStatus.Unknown
        }
    }

    companion object {
        private const val TAG = "SecurityChecker"
    }
}

/**
 * Compatibility facade preserving static [SecurityChecker.check] access.
 */
object SecurityChecker {
    private val defaultChecker = ApkSecurityChecker()

    suspend fun check(asset: ReleaseAsset?): SecurityStatus = defaultChecker.check(asset)
}
