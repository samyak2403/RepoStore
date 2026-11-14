package com.samyak.repostore.data.auth

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import com.samyak.repostore.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecureTokenStorageIssue52Test {

    private lateinit var context: Context
    private lateinit var fakeCrypto: FakeKeystoreCryptoBackend

    /**
     * Simulates Android Keystore behavior across app install/uninstall:
     * rotating [keyEpoch] invalidates previously encrypted blobs so decrypt returns null
     * (matching AEADBadTagException / KeyPermanentlyInvalidatedException).
     */
    private class FakeKeystoreCryptoBackend : SecureTokenStorage.CryptoBackend {
        var keyEpoch = 1
        var failNextEncryptCount = 0
        var resetCount = 0

        override fun encrypt(plainText: String): Pair<ByteArray, ByteArray>? {
            if (failNextEncryptCount > 0) {
                failNextEncryptCount--
                return null
            }
            val iv = byteArrayOf(keyEpoch.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            val bytes = plainText.toByteArray(Charsets.UTF_8).map { (it.toInt() xor (keyEpoch + 42)).toByte() }.toByteArray()
            return bytes to iv
        }

        override fun decrypt(encryptedBytes: ByteArray, iv: ByteArray): String? {
            if (iv.isEmpty() || iv[0].toInt() != keyEpoch) {
                return null
            }
            val decoded = encryptedBytes.map { (it.toInt() xor (keyEpoch + 42)).toByte() }.toByteArray()
            return String(decoded, Charsets.UTF_8)
        }

        override fun resetKey() {
            resetCount++
            keyEpoch++
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeCrypto = FakeKeystoreCryptoBackend()
        SecureTokenStorage.setCryptoBackendForTesting(fakeCrypto)
        SecureTokenStorage.clearAll(context)
        fakeCrypto.keyEpoch = 1
        fakeCrypto.resetCount = 0
    }

    @After
    fun tearDown() {
        SecureTokenStorage.clearAll(context)
        SecureTokenStorage.setCryptoBackendForTesting(null)
    }

    @Test
    fun `reinstall with restored backup ciphertext self-heals and allows signing in with existing account`() {
        // 1. User signed in before uninstalling the app
        assertTrue(SecureTokenStorage.saveToken(context, "gho_before_uninstall_12345"))
        SecureTokenStorage.saveUser(context, "existingUser", "https://avatars/1", "Existing User")
        assertTrue(SecureTokenStorage.isSignedIn(context))
        assertEquals("existingUser", SecureTokenStorage.getUserLogin(context))

        // 2. Simulate uninstall + reinstall where Android Auto Backup restored SharedPreferences XML
        //    but Android Keystore lost the old key (new keyEpoch != iv[0])
        fakeCrypto.keyEpoch = 2

        // Reading token/signed-in state must NOT crash and must self-heal the broken session
        assertFalse(SecureTokenStorage.isSignedIn(context))
        assertNull(SecureTokenStorage.getToken(context))
        assertNull(SecureTokenStorage.getUserLogin(context))
        assertNull(SecureTokenStorage.getUserAvatar(context))
        assertNull(SecureTokenStorage.getUserName(context))
        assertEquals(
            SessionResetReason.CORRUPTED_OR_RESTORED,
            SecureTokenStorage.getSessionResetReason(context)
        )
        assertTrue("Expected Keystore alias reset during self-heal", fakeCrypto.resetCount > 0)

        // 3. User signs back in with their existing GitHub account -> must succeed cleanly
        assertTrue(SecureTokenStorage.saveToken(context, "gho_reauthed_existing_67890"))
        SecureTokenStorage.saveUser(context, "existingUser", "https://avatars/1", "Existing User")

        assertTrue(SecureTokenStorage.isSignedIn(context))
        assertEquals("gho_reauthed_existing_67890", SecureTokenStorage.getToken(context))
        assertEquals("existingUser", SecureTokenStorage.getUserLogin(context))
        assertNull(
            "Reset notice reason should clear after successful sign-in",
            SecureTokenStorage.getSessionResetReason(context)
        )
    }

    @Test
    fun `saveToken resets Keystore alias and retries when initial encryption fails`() {
        fakeCrypto.failNextEncryptCount = 1

        val saved = SecureTokenStorage.saveToken(context, "gho_retry_success_abc")

        assertTrue(saved)
        assertEquals(1, fakeCrypto.resetCount)
        assertEquals("gho_retry_success_abc", SecureTokenStorage.getToken(context))
    }

    @Test
    fun `onTokenUnauthorized clears session and records TOKEN_REVOKED`() {
        assertTrue(SecureTokenStorage.saveToken(context, "gho_revoked_token"))
        SecureTokenStorage.saveUser(context, "octocat", null, "Octocat")

        SecureTokenStorage.onTokenUnauthorized(context)

        assertFalse(SecureTokenStorage.isSignedIn(context))
        assertNull(SecureTokenStorage.getToken(context))
        assertNull(SecureTokenStorage.getUserLogin(context))
        assertEquals(SessionResetReason.TOKEN_REVOKED, SecureTokenStorage.getSessionResetReason(context))
    }

    @Test
    fun `pending device flow persists across browser handoff and auto-expires`() {
        val now = 1_000_000L
        val pending = PendingDeviceFlow(
            deviceCode = "dc_keep_me",
            userCode = "ABCD-5678",
            verificationUri = "https://github.com/login/device",
            intervalSeconds = 5,
            expiresAtEpochMs = now + 60_000L
        )
        SecureTokenStorage.savePendingDeviceFlow(context, pending)

        val restored = SecureTokenStorage.getPendingDeviceFlow(context, nowMs = now + 10_000L)
        assertNotNull(restored)
        assertEquals("dc_keep_me", restored!!.deviceCode)
        assertEquals("ABCD-5678", restored.userCode)

        val expired = SecureTokenStorage.getPendingDeviceFlow(context, nowMs = now + 60_001L)
        assertNull(expired)
    }

    @Test
    fun `backup rules exclude auth storage and pending prefs on all Android versions`() {
        val legacyExclusions = readBackupExclusions(R.xml.backup_rules)
        assertTrue(legacyExclusions.contains("github_auth_storage.xml"))
        assertTrue(legacyExclusions.contains("github_auth_pending.xml"))

        val api31Exclusions = readBackupExclusions(R.xml.data_extraction_rules)
        assertTrue(api31Exclusions.count { it == "github_auth_storage.xml" } >= 2)
        assertTrue(api31Exclusions.count { it == "github_auth_pending.xml" } >= 2)
    }

    private fun readBackupExclusions(xmlResId: Int): List<String> {
        val paths = mutableListOf<String>()
        val parser: XmlResourceParser = context.resources.getXml(xmlResId)
        var event = parser.eventType
        while (event != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.START_TAG && parser.name == "exclude") {
                val path = parser.getAttributeValue(null, "path")
                if (path != null) paths.add(path)
            }
            event = parser.next()
        }
        parser.close()
        return paths
    }
}
