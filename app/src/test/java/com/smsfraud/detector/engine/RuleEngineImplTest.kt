package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.model.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for RuleEngineImpl — verifies exact Notebook 04 Cell 18 behavior.
 *
 * Tests cover:
 *   - All 8 rule patterns fire correctly
 *   - First-match priority (FORCE_BLOCK takes precedence)
 *   - FORCE_BLOCK rules: exact confidence=0.95, score=1.0
 *   - FORCE_PASS rules: exact confidence=0.85, score=0.0
 *   - SCORE rules: exact confidence=0.75, score=0.80
 *   - No match: label="normal", confidence=0.60, score=0.50
 */
class RuleEngineImplTest {

    private lateinit var engine: RuleEngineImpl

    @Before fun setUp() { engine = RuleEngineImpl() }

    // -------------------------------------------------------------------------
    // FORCE_BLOCK rules
    // -------------------------------------------------------------------------

    @Test fun `CredentialHarvest fires on verify account`() {
        val r = engine.score("please verify your account immediately")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("CredentialHarvest", r.ruleLabel)
        assertEquals(0.95f, r.ruleConfidence, 0.0001f)
        assertEquals(1.0f,  r.ruleScore,      0.0001f)
    }

    @Test fun `CredentialHarvest fires on confirm kyc`() {
        val r = engine.score("confirm your kyc details to avoid suspension")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("CredentialHarvest", r.ruleLabel)
    }

    @Test fun `CredentialHarvest fires on update password`() {
        val r = engine.score("update your password now for security")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("CredentialHarvest", r.ruleLabel)
    }

    @Test fun `KnownPhishingTemplate fires on kyc expire`() {
        // Use "expire" not "expired" — pattern ends with \b so suffix-free word required
        val r = engine.score("your kyc expire soon please update")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("KnownPhishingTemplate", r.ruleLabel)
        assertEquals(0.95f, r.ruleConfidence, 0.0001f)
        assertEquals(1.0f,  r.ruleScore,      0.0001f)
    }

    @Test fun `KnownPhishingTemplate fires on kyc pending`() {
        val r = engine.score("your kyc is pending, update now")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("KnownPhishingTemplate", r.ruleLabel)
    }

    // -------------------------------------------------------------------------
    // FORCE_PASS rules
    // -------------------------------------------------------------------------

    @Test fun `OTPDetect fires on OTP message`() {
        val r = engine.score("your otp is 123456")
        assertEquals(RuleAction.FORCE_PASS, r.action)
        assertEquals("OTPDetect", r.ruleLabel)
        assertEquals(0.85f, r.ruleConfidence, 0.0001f)
        assertEquals(0.0f,  r.ruleScore,      0.0001f)
    }

    @Test fun `BankTransactionTemplate fires on debited message`() {
        // Pattern: (debited|credited|a/c|acct).{0,20}(rs|inr|[AMOUNT]) — amount comes AFTER identifier
        val r = engine.score("amount debited inr 500 from your account")
        assertEquals(RuleAction.FORCE_PASS, r.action)
        assertEquals("BankTransactionTemplate", r.ruleLabel)
        assertEquals(0.85f, r.ruleConfidence, 0.0001f)
        assertEquals(0.0f,  r.ruleScore,      0.0001f)
    }

    @Test fun `DeliveryNotification fires on parcel message`() {
        val r = engine.score("your parcel is out for delivery today")
        assertEquals(RuleAction.FORCE_PASS, r.action)
        assertEquals("DeliveryNotification", r.ruleLabel)
        assertEquals(0.85f, r.ruleConfidence, 0.0001f)
    }

    // -------------------------------------------------------------------------
    // SCORE rules
    // -------------------------------------------------------------------------

    @Test fun `RewardScam fires with SCORE action`() {
        val r = engine.score("congratulations you are our lucky winner")
        assertEquals(RuleAction.SCORE, r.action)
        assertEquals("RewardScam", r.ruleLabel)
        assertEquals(0.75f, r.ruleConfidence, 0.0001f)
        assertEquals(0.80f, r.ruleScore,      0.0001f)
    }

    @Test fun `GovtImpersonation fires with SCORE action`() {
        val r = engine.score("income tax fine due pay immediately")
        assertEquals(RuleAction.SCORE, r.action)
        assertEquals("GovtImpersonation", r.ruleLabel)
        assertEquals(0.75f, r.ruleConfidence, 0.0001f)
        assertEquals(0.80f, r.ruleScore,      0.0001f)
    }

    @Test fun `UrgencyPhrase fires with SCORE action`() {
        val r = engine.score("act now your offer expires today")
        assertEquals(RuleAction.SCORE, r.action)
        assertEquals("UrgencyPhrase", r.ruleLabel)
        assertEquals(0.75f, r.ruleConfidence, 0.0001f)
        assertEquals(0.80f, r.ruleScore,      0.0001f)
    }

    // -------------------------------------------------------------------------
    // No match — "normal"
    // -------------------------------------------------------------------------

    @Test fun `normal message returns SCORE normal`() {
        val r = engine.score("see you at the office tomorrow morning")
        assertEquals(RuleAction.SCORE, r.action)
        assertEquals("normal", r.ruleLabel)
        assertEquals(0.60f, r.ruleConfidence, 0.0001f)
        assertEquals(0.50f, r.ruleScore,      0.0001f)
    }

    @Test fun `empty string returns normal`() {
        val r = engine.score("")
        assertEquals("normal", r.ruleLabel)
    }

    // -------------------------------------------------------------------------
    // Priority: FORCE_BLOCK takes precedence over FORCE_PASS
    // -------------------------------------------------------------------------

    @Test fun `FORCE_BLOCK wins over FORCE_PASS when both patterns present`() {
        // "verify your account" → CredentialHarvest (FORCE_BLOCK)
        // also contains "otp" → OTPDetect (FORCE_PASS)
        // CredentialHarvest comes first in the ordered list → FORCE_BLOCK wins
        val r = engine.score("verify your account otp 123456")
        assertEquals(RuleAction.FORCE_BLOCK, r.action)
        assertEquals("CredentialHarvest", r.ruleLabel)
    }

    // -------------------------------------------------------------------------
    // Notebook 04 Cell 22 exact test cases
    // -------------------------------------------------------------------------

    @Test fun `NB04 unit test - kyc suspend - FORCE_BLOCK or SCORE`() {
        val r = engine.score("your account has been suspended, verify kyc now")
        assertTrue(r.action == RuleAction.FORCE_BLOCK || r.action == RuleAction.SCORE)
    }

    @Test fun `NB04 unit test - parcel delivery - FORCE_PASS`() {
        val r = engine.score("your parcel is out for delivery")
        assertEquals(RuleAction.FORCE_PASS, r.action)
    }

    @Test fun `NB04 unit test - lunch message - normal`() {
        val r = engine.score("hey are we still on for lunch tomorrow?")
        assertEquals("normal", r.ruleLabel)
    }
}
