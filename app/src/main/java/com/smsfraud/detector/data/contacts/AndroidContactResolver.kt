package com.smsfraud.detector.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves whether an incoming SMS sender phone number is saved in Android Contacts.
 *
 * Safe & privacy-respecting contract:
 *   - Uses [ContactsContract.PhoneLookup] for optimized phone-number query.
 *   - Checks [Manifest.permission.READ_CONTACTS] before querying.
 *   - Normalizes phone numbers (stripping formatting, absorbing country codes).
 *   - Never logs, stores, or leaks raw phone numbers or contact names.
 *   - Catches all security/query exceptions and safely returns false (unknown sender fallback).
 */
@Singleton
open class AndroidContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Checks whether [senderString] matches a saved contact in Android Contacts.
     *
     * @param senderString incoming SMS sender ID (e.g. "+919876543210", "9876543210", "HDFC")
     * @return true if sender is a phone number present in contacts and permission is granted; false otherwise.
     */
    open fun isInContacts(senderString: String): Boolean {
        if (senderString.isBlank()) return false

        // 1. Permission check — safe fallback if not granted
        if (!hasReadContactsPermission()) {
            safeLogD(TAG, "READ_CONTACTS permission not granted — defaulting to unknown sender.")
            return false
        }

        // 2. Normalize and check if valid phone number
        val normalized = normalizePhoneNumber(senderString) ?: return false

        // 3. Query ContactsContract.PhoneLookup
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val matchFound = cursor.count > 0
                safeLogD(TAG, "Contact lookup completed: matchFound=$matchFound")
                matchFound
            } ?: false
        } catch (e: SecurityException) {
            safeLogW(TAG, "SecurityException during contact lookup: ${e.message}")
            false
        } catch (e: Throwable) {
            safeLogW(TAG, "Unexpected error during contact lookup: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * Returns true if [Manifest.permission.READ_CONTACTS] is granted.
     */
    open fun hasReadContactsPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Phone-number normalizer.
     *
     * Handles:
     *   - "+919876543210"
     *   - "9876543210"
     *   - "91 98765 43210"
     *   - "+91-98765-43210"
     *   - "(987) 654-3210"
     *
     * Returns cleaned phone number string or null if input is an alphanumeric shortcode (e.g. "HDFC-OTP").
     */
    fun normalizePhoneNumber(senderString: String): String? {
        val trimmed = senderString.trim()
        if (trimmed.isEmpty()) return null

        // Check if alphanumeric shortcode (contains letters without phone structure)
        val hasAlpha = trimmed.any { it.isLetter() }
        if (hasAlpha) {
            return null // Shortcodes like "HDFC", "SBI-OTP" are not numeric phone numbers
        }

        // Retain leading '+' if present, strip all non-digit characters
        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }

        if (digitsOnly.length < 6) {
            return null // Too short to be a valid phone number
        }

        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    private fun safeLogD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) {}
    }

    private fun safeLogW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) {}
    }

    companion object {
        private const val TAG = "AndroidContactResolver"
    }
}
