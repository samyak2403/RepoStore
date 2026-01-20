package com.samyak.repostore.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure token storage using Android Keystore for encryption.
 * 
 * Security features:
 * - AES-256-GCM encryption (authenticated encryption)
 * - Keys stored in Android Keystore (hardware-backed on supported devices)
 * - IV stored alongside encrypted data
 * - Graceful fallback for edge cases
 * 
 * F-Droid compatible - uses only Android platform APIs, no external dependencies.
 */
object SecureTokenStorage {

    private const val TAG = "SecureTokenStorage"
    private const val PREFS_NAME = "github_auth_storage"
    private const val KEYSTORE_ALIAS = "repostore_token_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    
    // Keys for encrypted token storage
    private const val KEY_ENCRYPTED_TOKEN = "encrypted_token_v2"
    private const val KEY_TOKEN_IV = "token_iv_v2"
    
    // Legacy key (for migration)
    private const val KEY_TOKEN_LEGACY = "at_v1"
    
    // User info keys (not sensitive, no encryption needed)
    private const val KEY_USER_LOGIN = "user_login"
    private const val KEY_USER_AVATAR = "user_avatar"
    private const val KEY_USER_NAME = "user_name"
    
    // Cipher specs
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get or create the secret key from Android Keystore
     */
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Check if key already exists
            val existingKey = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingKey != null) {
                return existingKey.secretKey
            }

            // Generate new key
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
                .setUserAuthenticationRequired(false) // Don't require biometric
                .build()

            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get/create secret key", e)
            null
        }
    }

    /**
     * Encrypt a string value using AES-GCM
     */
    private fun encrypt(value: String): Pair<ByteArray, ByteArray>? {
        return try {
            val secretKey = getOrCreateSecretKey() ?: return null
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            
            Pair(encryptedBytes, iv)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }

    /**
     * Decrypt a value using AES-GCM
     */
    private fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): String? {
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

    /**
     * Save token with encryption
     */
    fun saveToken(context: Context, token: String) {
        val encrypted = encrypt(token)
        
        if (encrypted != null) {
            val (encryptedBytes, iv) = encrypted
            getPrefs(context).edit()
                .putString(KEY_ENCRYPTED_TOKEN, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_TOKEN_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove(KEY_TOKEN_LEGACY) // Remove legacy token if exists
                .apply()
            Log.d(TAG, "Token saved with encryption")
        } else {
            // Fallback: This shouldn't normally happen, but log it
            Log.w(TAG, "Encryption failed, token not saved")
        }
    }

    /**
     * Get token with decryption
     */
    fun getToken(context: Context): String? {
        val prefs = getPrefs(context)
        
        // Try to get encrypted token first
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_TOKEN, null)
        val ivBase64 = prefs.getString(KEY_TOKEN_IV, null)
        
        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                
                val decrypted = decrypt(encryptedBytes, iv)
                if (decrypted != null) {
                    return decrypted
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt token", e)
            }
        }
        
        // Try legacy token (migration path)
        val legacyToken = prefs.getString(KEY_TOKEN_LEGACY, null)
        if (legacyToken != null) {
            try {
                // Old tokens were Base64 encoded
                val decoded = String(Base64.decode(legacyToken, Base64.NO_WRAP), Charsets.UTF_8)
                // Migrate to encrypted storage
                saveToken(context, decoded)
                Log.d(TAG, "Migrated legacy token to encrypted storage")
                return decoded
            } catch (e: Exception) {
                // Might be a plain token (very old), try as-is
                saveToken(context, legacyToken)
                return legacyToken
            }
        }
        
        return null
    }

    fun isSignedIn(context: Context): Boolean {
        return getToken(context) != null
    }

    // User info operations (not sensitive, no encryption needed)
    fun saveUser(context: Context, login: String, avatarUrl: String?, name: String?) {
        getPrefs(context).edit().apply {
            putString(KEY_USER_LOGIN, login)
            putString(KEY_USER_AVATAR, avatarUrl)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun getUserLogin(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_LOGIN, null)
    }

    fun getUserAvatar(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_AVATAR, null)
    }

    fun getUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NAME, null)
    }

    fun signOut(context: Context) {
        getPrefs(context).edit().apply {
            remove(KEY_ENCRYPTED_TOKEN)
            remove(KEY_TOKEN_IV)
            remove(KEY_TOKEN_LEGACY)
            remove(KEY_USER_LOGIN)
            remove(KEY_USER_AVATAR)
            remove(KEY_USER_NAME)
            apply()
        }
        
        // Optionally delete the key from keystore on sign out
        // (keeping it allows faster re-encryption on next sign-in)
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
        
        // Also remove key from keystore
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete keystore entry", e)
        }
    }
}
