package com.ultimateguard.core

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Admin PIN stored as a salted PBKDF2 hash. The PIN itself is never written
 * anywhere, and the app UI (and therefore every "unblock" path) sits behind it.
 */
object PinStore {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun isSet(prefs: Prefs): Boolean = !prefs.pinHash.isNullOrEmpty() && !prefs.pinSalt.isNullOrEmpty()

    fun setPin(prefs: Prefs, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.pinSalt = Base64.getEncoder().encodeToString(salt)
        prefs.pinHash = hash(pin, salt)
    }

    fun verify(prefs: Prefs, pin: String): Boolean {
        val saltB64 = prefs.pinSalt ?: return false
        val expected = prefs.pinHash ?: return false
        val salt = runCatching { Base64.getDecoder().decode(saltB64) }.getOrNull() ?: return false
        return constantTimeEquals(hash(pin, salt), expected)
    }

    fun clear(prefs: Prefs) {
        prefs.pinHash = null
        prefs.pinSalt = null
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val bytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
