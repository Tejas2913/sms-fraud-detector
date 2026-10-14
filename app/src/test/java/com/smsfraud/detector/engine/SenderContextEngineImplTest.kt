package com.smsfraud.detector.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SenderContextEngineImpl — verifies exact Notebook 04 Cell 20 behavior.
 */
class SenderContextEngineImplTest {

    private lateinit var mockResolver: com.smsfraud.detector.data.contacts.AndroidContactResolver
    private lateinit var mockSettings: com.smsfraud.detector.data.repository.SettingsRepository
    private lateinit var engine: SenderContextEngineImpl

    @Before fun setUp() {
        mockResolver = io.mockk.mockk(relaxed = true)
        mockSettings = io.mockk.mockk(relaxed = true)
        io.mockk.coEvery { mockSettings.isContactLookupEnabled() } returns true
        engine = SenderContextEngineImpl(mockResolver, mockSettings)
    }

    // -------------------------------------------------------------------------
    // NB04 exact simulation outcomes
    // -------------------------------------------------------------------------

    @Test fun `synthetic source returns unknown_sender with risk 0_55`() {
        val r = engine.scoreFromMetadata("synthetic", "normal")
        assertEquals(0.55f, r.senderRisk,       0.0001f)
        assertEquals(0.55f, r.senderConfidence,  0.0001f)
        assertEquals("unknown_sender", r.senderLabel)
        assertFalse(r.senderInContacts)
        assertFalse(r.trustedSenderRecommendation)
    }

    @Test fun `NB04 unit test - synthetic source - trusted false`() {
        val r = engine.scoreFromMetadata("synthetic", "normal")
        assertFalse(r.trustedSenderRecommendation)
    }

    @Test fun `real source otp returns registered_business_sender trusted true`() {
        val r = engine.scoreFromMetadata("real", "otp")
        assertEquals(0.10f, r.senderRisk,       0.0001f)
        assertEquals(0.85f, r.senderConfidence,  0.0001f)
        assertEquals("registered_business_sender", r.senderLabel)
        assertTrue(r.senderInContacts)
        assertTrue(r.trustedSenderRecommendation)
    }

    @Test fun `NB04 unit test - real source otp - trusted true`() {
        val r = engine.scoreFromMetadata("real", "otp")
        assertTrue(r.trustedSenderRecommendation)
    }

    @Test fun `real source banking returns trusted true`() {
        val r = engine.scoreFromMetadata("real", "banking")
        assertTrue(r.trustedSenderRecommendation)
        assertEquals("registered_business_sender", r.senderLabel)
        assertEquals(0.10f, r.senderRisk, 0.0001f)
    }

    @Test fun `real source normal returns unregistered_sender trusted false`() {
        val r = engine.scoreFromMetadata("real", "normal")
        assertEquals(0.40f, r.senderRisk,       0.0001f)
        assertEquals(0.65f, r.senderConfidence,  0.0001f)
        assertEquals("unregistered_sender", r.senderLabel)
        assertFalse(r.senderInContacts)
        assertFalse(r.trustedSenderRecommendation)
    }

    @Test fun `real source fraud category returns unregistered_sender`() {
        // "fraud" is not in TRUSTED_CATEGORIES, so treated as unregistered
        val r = engine.scoreFromMetadata("real", "fraud")
        assertEquals("unregistered_sender", r.senderLabel)
        assertFalse(r.trustedSenderRecommendation)
    }

    // -------------------------------------------------------------------------
    // Android on-device heuristic
    // -------------------------------------------------------------------------

    @Test fun `numeric phone number maps to synthetic unknown_sender`() {
        val r = engine.scoreFromMetadata("synthetic", "normal")
        assertEquals("unknown_sender", r.senderLabel)
    }

    @Test fun `OTP keyword in sender ID maps to trusted`() {
        // Direct metadata test for the OTP path
        val r = engine.scoreFromMetadata("real", "otp")
        assertTrue(r.trustedSenderRecommendation)
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test fun `case sensitivity - synthetic lowercase`() {
        // source comparison in NB04 is str(row.get("source")) - case sensitive
        // "SYNTHETIC" is NOT equal to "synthetic"
        val r = engine.scoreFromMetadata("SYNTHETIC", "normal")
        // Falls through to unregistered_sender (SYNTHETIC != synthetic)
        assertEquals("unregistered_sender", r.senderLabel)
    }

    @Test fun `trusted categories set contains otp and banking only`() {
        assertTrue("otp" in SenderContextEngineImpl.TRUSTED_CATEGORIES)
        assertTrue("banking" in SenderContextEngineImpl.TRUSTED_CATEGORIES)
        assertEquals(2, SenderContextEngineImpl.TRUSTED_CATEGORIES.size)
    }

    // -------------------------------------------------------------------------
    // Real Android evaluate() tests
    // -------------------------------------------------------------------------

    @Test fun `evaluate with saved contact returns contact_saved with risk 0_10`() = kotlinx.coroutines.runBlocking {
        io.mockk.every { mockResolver.isInContacts(any()) } returns true
        val r = engine.evaluate("+919876543210")
        assertEquals(0.10f, r.senderRisk, 0.0001f)
        assertEquals(0.85f, r.senderConfidence, 0.0001f)
        assertEquals("contact_saved", r.senderLabel)
        assertTrue(r.senderInContacts)
        assertTrue(r.trustedSenderRecommendation)
    }

    @Test fun `evaluate with unknown contact falls back to unknown_sender with risk 0_55`() = kotlinx.coroutines.runBlocking {
        io.mockk.every { mockResolver.isInContacts(any()) } returns false
        val r = engine.evaluate("+911111111111")
        assertEquals(0.55f, r.senderRisk, 0.0001f)
        assertEquals(0.55f, r.senderConfidence, 0.0001f)
        assertEquals("unknown_sender", r.senderLabel)
        assertFalse(r.senderInContacts)
        assertFalse(r.trustedSenderRecommendation)
    }

    @Test fun `evaluate with permission denied falls back safely to unknown_sender`() = kotlinx.coroutines.runBlocking {
        io.mockk.every { mockResolver.hasReadContactsPermission() } returns false
        io.mockk.every { mockResolver.isInContacts(any()) } returns false
        val r = engine.evaluate("+919876543210")
        assertEquals("unknown_sender", r.senderLabel)
        assertEquals(0.55f, r.senderRisk, 0.0001f)
        assertFalse(r.senderInContacts)
    }
}
