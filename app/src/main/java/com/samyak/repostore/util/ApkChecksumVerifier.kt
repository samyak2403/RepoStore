package com.samyak.repostore.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Verifies downloaded APK artifacts against a published SHA-256 checksum
 * before triggering installation.
 */
class ApkChecksumVerifier(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    sealed class ChecksumVerificationResult {
        data class Verified(val computedSha256: String) : ChecksumVerificationResult()
        data object SkippedNoDigest : ChecksumVerificationResult()
        data class Mismatch(val expectedSha256: String, val actualSha256: String) : ChecksumVerificationResult()
        data class Error(val message: String) : ChecksumVerificationResult()
    }

    private sealed class NormalizedDigest {
        data object Missing : NormalizedDigest()
        data object UnsupportedAlgorithm : NormalizedDigest()
        data class InvalidFormat(val raw: String) : NormalizedDigest()
        data class Valid(val hex: String) : NormalizedDigest()
    }

    suspend fun verifyFile(file: File, expectedDigest: String?): ChecksumVerificationResult =
        withContext(ioDispatcher) {
            val expected = when (val parsed = normalizeExpectedDigest(expectedDigest)) {
                NormalizedDigest.Missing,
                NormalizedDigest.UnsupportedAlgorithm -> return@withContext ChecksumVerificationResult.SkippedNoDigest
                is NormalizedDigest.InvalidFormat -> return@withContext ChecksumVerificationResult.Error(
                    "Invalid SHA-256 digest format"
                )
                is NormalizedDigest.Valid -> parsed.hex
            }

            if (!file.exists() || !file.isFile || file.length() == 0L) {
                return@withContext ChecksumVerificationResult.Error("Downloaded APK file not found for verification")
            }

            try {
                val actual = FileInputStream(file).use { input -> computeSha256(input) }
                if (actual.equals(expected, ignoreCase = true)) {
                    ChecksumVerificationResult.Verified(actual)
                } else {
                    ChecksumVerificationResult.Mismatch(expectedSha256 = expected, actualSha256 = actual)
                }
            } catch (e: Exception) {
                ChecksumVerificationResult.Error(e.message ?: "Failed to compute APK checksum")
            }
        }

    suspend fun verifyUri(
        context: Context,
        uri: Uri,
        expectedDigest: String?
    ): ChecksumVerificationResult = withContext(ioDispatcher) {
        val expected = when (val parsed = normalizeExpectedDigest(expectedDigest)) {
            NormalizedDigest.Missing,
            NormalizedDigest.UnsupportedAlgorithm -> return@withContext ChecksumVerificationResult.SkippedNoDigest
            is NormalizedDigest.InvalidFormat -> return@withContext ChecksumVerificationResult.Error(
                "Invalid SHA-256 digest format"
            )
            is NormalizedDigest.Valid -> parsed.hex
        }

        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ChecksumVerificationResult.Error("Unable to open downloaded APK stream for verification")
            val actual = stream.use { input -> computeSha256(input) }
            if (actual.equals(expected, ignoreCase = true)) {
                ChecksumVerificationResult.Verified(actual)
            } else {
                ChecksumVerificationResult.Mismatch(expectedSha256 = expected, actualSha256 = actual)
            }
        } catch (e: Exception) {
            ChecksumVerificationResult.Error(e.message ?: "Failed to compute APK checksum")
        }
    }

    fun computeSha256(file: File): String = FileInputStream(file).use { computeSha256(it) }

    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeExpectedDigest(rawDigest: String?): NormalizedDigest {
        if (rawDigest.isNullOrBlank()) return NormalizedDigest.Missing
        val trimmed = rawDigest.trim()
        val hexCandidate = if (trimmed.contains(':')) {
            val algorithm = trimmed.substringBefore(':').trim().lowercase()
            if (algorithm != "sha256") {
                return NormalizedDigest.UnsupportedAlgorithm
            }
            trimmed.substringAfter(':').trim().lowercase()
        } else {
            trimmed.lowercase()
        }

        return if (SHA256_HEX_REGEX.matches(hexCandidate)) {
            NormalizedDigest.Valid(hexCandidate)
        } else {
            NormalizedDigest.InvalidFormat(rawDigest)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
