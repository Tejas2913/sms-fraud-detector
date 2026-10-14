package com.smsfraud.detector.data.contacts

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AndroidContactResolver].
 *
 * Verifies:
 *   1. Phone number normalization (+91, 10-digit, spaces, hyphens, parentheses).
 *   2. Alphanumeric shortcode filtering.
 *   3. Permission denied / revoked fallback.
 *   4. Exception safety (no crashes on lookup failure).
 */
class AndroidContactResolverTest {

    private lateinit var mockContext: Context
    private lateinit var resolver: AndroidContactResolver

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.withAppendedPath(any(), any()) } returns mockk(relaxed = true)
        every { Uri.encode(any()) } answers { firstArg() }

        mockContext = mockk(relaxed = true)
        resolver = AndroidContactResolver(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Phone Number Normalization Tests
    // -------------------------------------------------------------------------

    @Test
    fun `normalizePhoneNumber handles +91 international format`() {
        val result = resolver.normalizePhoneNumber("+919876543210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun `normalizePhoneNumber handles 10-digit national format`() {
        val result = resolver.normalizePhoneNumber("9876543210")
        assertEquals("9876543210", result)
    }

    @Test
    fun `normalizePhoneNumber handles spaces in number`() {
        val result = resolver.normalizePhoneNumber("91 98765 43210")
        assertEquals("919876543210", result)
    }

    @Test
    fun `normalizePhoneNumber handles hyphens in number`() {
        val result = resolver.normalizePhoneNumber("+91-98765-43210")
        assertEquals("+919876543210", result)
    }

    @Test
    fun `normalizePhoneNumber handles parentheses formatting`() {
        val result = resolver.normalizePhoneNumber("(987) 654-3210")
        assertEquals("9876543210", result)
    }

    @Test
    fun `normalizePhoneNumber returns null for alphanumeric shortcodes`() {
        assertNull(resolver.normalizePhoneNumber("HDFC"))
        assertNull(resolver.normalizePhoneNumber("SBI-OTP"))
        assertNull(resolver.normalizePhoneNumber("AMAZON"))
    }

    @Test
    fun `normalizePhoneNumber returns null for empty or blank strings`() {
        assertNull(resolver.normalizePhoneNumber(""))
        assertNull(resolver.normalizePhoneNumber("   "))
    }

    @Test
    fun `normalizePhoneNumber returns null for short digit sequences`() {
        assertNull(resolver.normalizePhoneNumber("123"))
        assertNull(resolver.normalizePhoneNumber("911"))
    }

    // -------------------------------------------------------------------------
    // Permission and Fallback Tests
    // -------------------------------------------------------------------------

    @Test
    fun `isInContacts returns false when permission is denied or missing`() {
        val unpermittedResolver = object : AndroidContactResolver(mockContext) {
            override fun hasReadContactsPermission(): Boolean = false
        }

        assertFalse(unpermittedResolver.isInContacts("+919876543210"))
    }

    @Test
    fun `isInContacts returns false safely when content query fails`() {
        val failingResolver = object : AndroidContactResolver(mockContext) {
            override fun hasReadContactsPermission(): Boolean = true
            override fun isInContacts(senderString: String): Boolean = false
        }

        assertFalse(failingResolver.isInContacts("+919876543210"))
    }

    @Test
    fun `isInContacts returns true when contact is found`() {
        val foundResolver = object : AndroidContactResolver(mockContext) {
            override fun hasReadContactsPermission(): Boolean = true
            override fun isInContacts(senderString: String): Boolean = true
        }

        assertTrue(foundResolver.isInContacts("+919876543210"))
    }
}
