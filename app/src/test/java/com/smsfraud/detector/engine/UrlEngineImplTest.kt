package com.smsfraud.detector.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for UrlEngineImpl — verifies exact Notebook 04 Cell 19 behavior.
 */
class UrlEngineImplTest {

    private lateinit var engine: UrlEngineImpl

    @Before fun setUp() { engine = UrlEngineImpl() }

    // -------------------------------------------------------------------------
    // No URL present
    // -------------------------------------------------------------------------

    @Test fun `no URL returns risk 0 confidence 1 label no_url`() {
        val r = engine.score(emptyList(), urlPresent = false)
        assertEquals(0.0f, r.urlRisk,       0.0001f)
        assertEquals(1.0f, r.urlConfidence, 0.0001f)
        assertEquals("no_url", r.urlLabel)
        assertFalse(r.urlPresent)
    }

    @Test fun `NB04 unit test - no URL - risk 0 present false`() {
        val r = engine.score(emptyList(), urlPresent = false)
        assertEquals(0.0f, r.urlRisk, 0.0001f)
        assertFalse(r.urlPresent)
    }

    // -------------------------------------------------------------------------
    // Any URL present — baseline
    // -------------------------------------------------------------------------

    @Test fun `unclassified URL has baseline risk 0_20 confidence 0_65`() {
        val r = engine.score(listOf("https://example.com"), urlPresent = true)
        assertEquals(0.20f, r.urlRisk,       0.0001f)
        assertEquals(0.65f, r.urlConfidence, 0.0001f)
        assertEquals("unclassified_url", r.urlLabel)
        assertTrue(r.urlPresent)
    }

    // -------------------------------------------------------------------------
    // Known malicious patterns → risk=0.95
    // -------------------------------------------------------------------------

    @Test fun `secure-verify pattern gives risk 0_95`() {
        val r = engine.score(listOf("http://secure-verify-bank.tk/login"), urlPresent = true)
        assertEquals(0.95f, r.urlRisk,       0.0001f)
        assertEquals("known_malicious_pattern", r.urlLabel)
        assertEquals(0.90f, r.urlConfidence, 0.0001f)
    }

    @Test fun `NB04 unit test - secure-verify bank dot tk - risk ge 0_9`() {
        val r = engine.score(listOf("http://secure-verify-bank.tk/login"), urlPresent = true)
        assertTrue("Expected risk >= 0.9 but got ${r.urlRisk}", r.urlRisk >= 0.9f)
    }

    @Test fun `kyc-verify pattern gives malicious label`() {
        val r = engine.score(listOf("https://kyc-verify.fraudsite.com"), urlPresent = true)
        assertEquals("known_malicious_pattern", r.urlLabel)
        assertEquals(0.95f, r.urlRisk, 0.0001f)
    }

    @Test fun `bank-update pattern gives malicious label`() {
        val r = engine.score(listOf("http://bank-update.phish.ml"), urlPresent = true)
        assertEquals("known_malicious_pattern", r.urlLabel)
    }

    // -------------------------------------------------------------------------
    // High-risk TLDs → risk=0.75, label=high_risk_tld (if not already classified)
    // -------------------------------------------------------------------------

    @Test fun `xyz TLD gives risk 0_75 label high_risk_tld`() {
        val r = engine.score(listOf("http://freeprize.xyz/claim"), urlPresent = true)
        assertEquals(0.75f, r.urlRisk,      0.0001f)
        assertEquals("high_risk_tld", r.urlLabel)
        assertEquals(0.90f, r.urlConfidence, 0.0001f)
    }

    @Test fun `top TLD gives risk 0_75`() {
        val r = engine.score(listOf("http://lottery.top"), urlPresent = true)
        assertEquals(0.75f, r.urlRisk, 0.0001f)
        assertEquals("high_risk_tld", r.urlLabel)
    }

    @Test fun `malicious pattern wins over high_risk_tld label`() {
        // URL matches both known_malicious_pattern (.tk) AND high_risk_tld
        // known_malicious_pattern label should win (set first, not overridden)
        val r = engine.score(listOf("http://secure-login.tk"), urlPresent = true)
        assertEquals("known_malicious_pattern", r.urlLabel)
        assertEquals(0.95f, r.urlRisk, 0.0001f)
    }

    // -------------------------------------------------------------------------
    // URL shorteners → risk=0.60, label=shortener (if not already classified)
    // -------------------------------------------------------------------------

    @Test fun `bit_ly shortener gives risk 0_60 label shortener`() {
        val r = engine.score(listOf("https://bit.ly/abc123"), urlPresent = true)
        assertEquals(0.60f, r.urlRisk,       0.0001f)
        assertEquals("shortener", r.urlLabel)
        assertEquals(0.90f, r.urlConfidence, 0.0001f)
    }

    @Test fun `tinyurl shortener gives risk 0_60`() {
        val r = engine.score(listOf("http://tinyurl.com/xyz789"), urlPresent = true)
        assertEquals(0.60f, r.urlRisk, 0.0001f)
        assertEquals("shortener", r.urlLabel)
    }

    @Test fun `malicious pattern wins over shortener label`() {
        // URL is a shortener but also matches malicious pattern
        // malicious label should win — it is set first
        val r = engine.score(listOf("http://bit.ly/secure-verify"), urlPresent = true)
        // The malicious pattern check runs first and sets label to known_malicious_pattern
        // Shortener check then sees label != unclassified_url, does not update label
        assertEquals("known_malicious_pattern", r.urlLabel)
        assertEquals(0.95f, r.urlRisk, 0.0001f)
    }

    // -------------------------------------------------------------------------
    // Multiple URLs — risk accumulates with max()
    // -------------------------------------------------------------------------

    @Test fun `multiple URLs take max risk`() {
        val r = engine.score(
            listOf("https://example.com", "http://lottery.xyz/claim"),
            urlPresent = true
        )
        // first URL → 0.20 (baseline), second → 0.75 (xyz TLD)
        assertEquals(0.75f, r.urlRisk, 0.0001f)
        assertEquals("high_risk_tld", r.urlLabel)
    }
}
