package com.samyak.repostore.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Reason an existing authentication session was cleared automatically.
 */
enum class SessionResetReason {
    /** Keystore key was missing or mismatched after uninstall/reinstall/backup restore (Issue #52). */
    CORRUPTED_OR_RESTORED,
    /** GitHub returned 401 Unauthorized because the token expired or was revoked. */
    TOKEN_REVOKED
}

/**
 * Short-lived pending GitHub OAuth Device Flow session so switching to an external
 * browser (Chrome/Edge) and returning to RepoStore resumes polling the same code
 * instead of discarding it and generating a new code (Issue #52).
 */
data class PendingDeviceFlow(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresAtEpochMs: Long
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= expiresAtEpochMs
}

/**
 * Secure token storage using Android Keystore for encryption.
 *
 * Security & reliability features:
 * - AES-256-GCM authenticated encryption
 * - Keys stored in Android Keystore (hardware-backed on supported devices)
 * - Self-healing recovery when restored backup data mismatches a fresh Keystore key
 * - Dedicated non-backed-up pending-flow persistence for browser handoff
 */
object SecureTokenStorage {

    private const val TAG = "SecureTokenStorage"
    const val PREFS_NAME = "github_auth_storage"
    const val PENDING_PREFS_NAME = "github_auth_pending"
    private const val KEYSTORE_ALIAS = "repostore_token_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Keys for encrypted token storage
    internal const val KEY_ENCRYPTED_TOKEN = "encrypted_token_v2"
    internal const val KEY_TOKEN_IV = "token_iv_v2"

    // Legacy key (for migration)
    internal const val KEY_TOKEN_LEGACY = "at_v1"

    // User info keys (not sensitive, cleared alongside token)
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_USER_NAME = "user_name"

    // Pending Device Flow & reset-reason keys (stored in PENDING_PREFS_NAME)
    private const val KEY_PENDING_DEVICE_CODE = "pending_device_code"
    private const val KEY_PENDING_USER_CODE = "pending_user_code"
    private const val KEY_PENDING_VERIFICATION_URI = "pending_verification_uri"
    private const val KEY_PENDING_INTERVAL = "pending_interval"
    private const val KEY_PENDING_EXPIRES_AT = "pending_expires_at"
    private const val KEY_RESET_REASON = "session_reset_reason"

    // Cipher specs
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    /**
     * Abstraction over Keystore encryption so JVM/Robolectric tests can simulate
     * uninstall/reinstall Keystore key rotation and AEADBadTagException cleanly.
     */
    internal interface CryptoBackend {
        fun encrypt(plainText: String): Pair<ByteArray, ByteArray>?
        fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): String?
        fun resetKey()
    }

    private object AndroidKeystoreCryptoBackend : CryptoBackend {
        private fun getOrCreateSecretKey(): SecretKey? {
            return try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)

                val existingKey = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existingKey != null) {
                    return existingKey.secretKey
                }

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )

                val keySpec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()

                keyGenerator.init(keySpec)
                keyGenerator.generateKey()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get/create secret key", e)
                null
            }
        }

        override fun encrypt(plainText: String): Pair<ByteArray, ByteArray>? {
            return try {
                val secretKey = getOrCreateSecretKey() ?: return null
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                Pair(encryptedBytes, cipher.iv)
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed", e)
                null
            }
        }

        override fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): String? {
            return try {
                val secretKey = getOrCreateSecretKey() ?: return null
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                String(decryptedBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed", e)
                null
            }
        }

        override fun resetKey() {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
                keyStore.load(null)
                if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                    keyStore.deleteEntry(KEYSTORE_ALIAS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete keystore entry", e)
            }
        }
    }

    @Volatile
    private var cryptoBackend: CryptoBackend = AndroidKeystoreCryptoBackend

    @VisibleForTesting
    internal fun setCryptoBackendForTesting(backend: CryptoBackend?) {
        cryptoBackend = backend ?: AndroidKeystoreCryptoBackend
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getPendingPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PENDING_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save token with AES-256-GCM encryption. If the Keystore key was invalidated,
     * resets the Keystore alias and retries once automatically.
     */
    @Synchronized
    fun saveToken(context: Context, token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return false

        var encrypted = cryptoBackend.encrypt(trimmed)
        if (encrypted == null) {
            Log.w(TAG, "Initial encryption failed; resetting Keystore entry and retrying")
            cryptoBackend.resetKey()
            encrypted = cryptoBackend.encrypt(trimmed)
        }

        return if (encrypted != null) {
            val (encryptedBytes, iv) = encrypted
            getPrefs(context).edit()
                .putString(KEY_ENCRYPTED_TOKEN, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_TOKEN_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove(KEY_TOKEN_LEGACY)
                .commit()
            clearSessionResetReason(context)
            clearPendingDeviceFlow(context)
            Log.d(TAG, "Token saved with encryption")
            true
        } else {
            Log.w(TAG, "Encryption failed after retry, token not saved")
            false
        }
    }

    /**
     * Retrieve decrypted token. If stored ciphertext cannot be decrypted (for example after
     * uninstall/reinstall restored an old backup against a new Keystore), purges the corrupted
     * state and records [SessionResetReason.CORRUPTED_OR_RESTORED] so the user can sign in cleanly.
     */
    @Synchronized
    fun getToken(context: Context): String? {
        val prefs = getPrefs(context)

        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_TOKEN, null)
        val ivBase64 = prefs.getString(KEY_TOKEN_IV, null)

        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val decrypted = cryptoBackend.decrypt(encryptedBytes, iv)
                if (!decrypted.isNullOrBlank()) {
                    return decrypted
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode/decrypt token", e)
            }

            // Self-heal: purge corrupted ciphertext, stale user metadata, and broken key alias.
            Log.w(TAG, "Purging undecryptable GitHub session (Keystore/backup mismatch)")
            purgeCorruptedSession(context, SessionResetReason.CORRUPTED_OR_RESTORED)
            return null
        } else if (encryptedBase64 != null || ivBase64 != null) {
            // Partial / truncated prefs state
            purgeCorruptedSession(context, SessionResetReason.CORRUPTED_OR_RESTORED)
            return null
        }

        // Legacy token migration path
        val legacyToken = prefs.getString(KEY_TOKEN_LEGACY, null)
        if (!legacyToken.isNullOrBlank()) {
            val decoded = try {
                val bytes = Base64.decode(legacyToken, Base64.NO_WRAP)
                String(bytes, Charsets.UTF_8).takeIf { it.isNotBlank() } ?: legacyToken
            } catch (_: Exception) {
                legacyToken
            }
            if (saveToken(context, decoded)) {
                Log.d(TAG, "Migrated legacy token to encrypted storage")
                return decoded
            }
            return null
        }

        return null
    }

    private fun purgeCorruptedSession(context: Context, reason: SessionResetReason) {
        getPrefs(context).edit()
            .remove(KEY_ENCRYPTED_TOKEN)
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_LEGACY)
            .remove(KEY_USER_LOGIN)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_USER_NAME)
            .commit()
        cryptoBackend.resetKey()
        setSessionResetReason(context, reason)
    }

    /**
     * Called when GitHub API returns 401 Unauthorized for the stored token.
     */
    @Synchronized
    fun onTokenUnauthorized(context: Context) {
        val hadToken = getPrefs(context).contains(KEY_ENCRYPTED_TOKEN) ||
            getPrefs(context).contains(KEY_TOKEN_LEGACY)
        signOut(context)
        if (hadToken) {
            setSessionResetReason(context, SessionResetReason.TOKEN_REVOKED)
        }
    }

    fun isSignedIn(context: Context): Boolean {
        return !getToken(context).isNullOrBlank()
    }

    fun saveUser(context: Context, login: String, avatarUrl: String?, name: String?) {
        getPrefs(context).edit().apply {
            putString(KEY_USER_LOGIN, login)
            putString(KEY_USER_AVATAR, avatarUrl)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun getUserLogin(context: Context): String? {
        if (!isSignedIn(context)) return null
        return getPrefs(context).getString(KEY_USER_LOGIN, null)
    }

    fun getUserAvatar(context: Context): String? {
        if (!isSignedIn(context)) return null
        return getPrefs(context).getString(KEY_USER_AVATAR, null)
    }

    fun getUserName(context: Context): String? {
        if (!isSignedIn(context)) return null
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }

    fun setSessionResetReason(context: Context, reason: SessionResetReason) {
        getPendingPrefs(context).edit()
            .putString(KEY_RESET_REASON, reason.name)
            .apply()
    }

    fun getSessionResetReason(context: Context): SessionResetReason? {
        val raw = getPendingPrefs(context).getString(KEY_RESET_REASON, null) ?: return null
        return try {
            SessionResetReason.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun clearSessionResetReason(context: Context) {
        getPendingPrefs(context).edit()
            .remove(KEY_RESET_REASON)
            .apply()
    }

    fun savePendingDeviceFlow(context: Context, pending: PendingDeviceFlow) {
        getPendingPrefs(context).edit()
            .putString(KEY_PENDING_DEVICE_CODE, pending.deviceCode)
            .putString(KEY_PENDING_USER_CODE, pending.userCode)
            .putString(KEY_PENDING_VERIFICATION_URI, pending.verificationUri)
            .putInt(KEY_PENDING_INTERVAL, pending.intervalSeconds)
            .putLong(KEY_PENDING_EXPIRES_AT, pending.expiresAtEpochMs)
            .apply()
    }

    fun getPendingDeviceFlow(context: Context, nowMs: Long = System.currentTimeMillis()): PendingDeviceFlow? {
        val prefs = getPendingPrefs(context)
        val deviceCode = prefs.getString(KEY_PENDING_DEVICE_CODE, null) ?: return null
        val userCode = prefs.getString(KEY_PENDING_USER_CODE, null) ?: return null
        val verificationUri = prefs.getString(KEY_PENDING_VERIFICATION_URI, null) ?: return null
        val interval = prefs.getInt(KEY_PENDING_INTERVAL, 5)
        val expiresAt = prefs.getLong(KEY_PENDING_EXPIRES_AT, 0L)

        val pending = PendingDeviceFlow(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            intervalSeconds = interval,
            expiresAtEpochMs = expiresAt
        )
        if (pending.isExpired(nowMs)) {
            clearPendingDeviceFlow(context)
            return null
        }
        return pending
    }

    fun clearPendingDeviceFlow(context: Context) {
        getPendingPrefs(context).edit()
            .remove(KEY_PENDING_DEVICE_CODE)
            .remove(KEY_PENDING_USER_CODE)
            .remove(KEY_PENDING_VERIFICATION_URI)
            .remove(KEY_PENDING_INTERVAL)
            .remove(KEY_PENDING_EXPIRES_AT)
            .apply()
    }

    @Synchronized
    fun signOut(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ENCRYPTED_TOKEN)
            .remove(KEY_TOKEN_IV)
            .remove(KEY_TOKEN_LEGACY)
            .remove(KEY_USER_LOGIN)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_USER_NAME)
            .commit()
        clearPendingDeviceFlow(context)
        clearSessionResetReason(context)
    }

    @Synchronized
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().commit()
        getPendingPrefs(context).edit().clear().commit()
        cryptoBackend.resetKey()
    }
}
