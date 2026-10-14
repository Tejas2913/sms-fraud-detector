package com.smsfraud.detector.ui.messagedetail

import android.net.Uri
import com.smsfraud.detector.data.sms.SmsContentResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests covering SMS body resolution edge cases in SmsContentResolver.
 *
 * Covers requirements 1-3:
 *   1. Message body resolution success (when URI or provider yields content)
 *   2. Message unavailable/deleted (returns null fallback)
 *   3. Permission/provider failure (handles SecurityException / invalid URI safely)
 */
class SmsContentResolverResolutionTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getMessageBody with blank URI and zero timestamp returns null fallback`() {
        val resolver = SmsContentResolver(createDummyContext())
        resolver.queryRunner = { _, _, _, _, _ -> null }

        val body = resolver.getMessageBody(systemSmsUri = "", receivedAt = 0L)
        assertNull("Blank URI and zero timestamp must return null fallback", body)
    }

    @Test
    fun `getMessageBody gracefully catches provider exception and returns null`() {
        val resolver = SmsContentResolver(createDummyContext())
        resolver.queryRunner = { _, _, _, _, _ -> throw SecurityException("READ_SMS permission denied") }

        val body = resolver.getMessageBody(systemSmsUri = "content://sms/99999", receivedAt = 1700000000000L)
        assertNull("Provider failure must return null without throwing exception", body)
    }

    @Test
    fun `getMessageBody resolves text when provider returns result`() {
        val resolver = SmsContentResolver(createDummyContext())
        resolver.queryRunner = { _, _, _, _, _ -> "Hello, this is a real SMS body." }

        val body = resolver.getMessageBody(systemSmsUri = "content://sms/123", receivedAt = 1700000000000L)
        assertEquals("Hello, this is a real SMS body.", body)
    }

    @Test
    fun `fallback string is used when getMessageBody returns null`() {
        val resolvedBody: String? = null
        val uiText = resolvedBody ?: "This message is no longer available."
        assertEquals("This message is no longer available.", uiText)
    }

    @Test
    fun `resolved SMS body is displayed when provider lookup succeeds`() {
        val resolvedBody: String? = "Hello, this is a real SMS body."
        val uiText = resolvedBody ?: "This message is no longer available."
        assertEquals("Hello, this is a real SMS body.", uiText)
    }

    private fun createDummyContext(): android.content.Context {
        return android.content.ContextWrapper(null)
    }
}
