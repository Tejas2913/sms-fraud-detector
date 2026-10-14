package com.smsfraud.detector.util

import java.security.MessageDigest

/**
 * SHA-256 hashing for sender_hash values.
 * Used to pseudonymise sender identity in the database.
 */
object HashUtil {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
