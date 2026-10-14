package com.smsfraud.detector.preprocessing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TextPreprocessor config parsing and preprocessing pipeline.
 *
 * These tests use the INLINE config JSON (matching the actual preprocessing_config.json content)
 * so they run as JVM unit tests without requiring Android context.
 *
 * The inline config is copied verbatim from models/baselines/preprocessing_config.json
 * to keep the tests honest against the frozen artifact.
 */
class TextPreprocessorTest {

    // Inline version of models/baselines/preprocessing_config.json
    // Copy of the frozen artifact — do NOT alter patterns here
    private val frozenConfigJson = """
        {
          "function_name": "preprocess_for_classical",
          "substitution_rules": [
            {
              "name": "URL",
              "replacement": "[URL]",
              "pattern": "https?://\\S+|www\\.\\S+|\\b\\S+\\.(?:xyz|top|tk|ml|ga|click|info|ly|gl)(?:/\\S*)?"
            },
            {
              "name": "PHONE",
              "replacement": "[PHONE]",
              "pattern": "(?:\\+?\\d[\\d\\-\\s]{7,}\\d)"
            },
            {
              "name": "AMOUNT",
              "replacement": "[AMOUNT]",
              "pattern": "(?:rs\\.?|inr|\u20b9|\\$|\u00a3|\u20ac)\\s*[\\d,]+(?:\\.\\d{1,2})?|[\\d,]+(?:\\.\\d{1,2})?\\s*(?:rs\\.?|inr|rupees?)"
            }
          ],
          "other_steps": [
            "lowercase",
            "whitespace_normalisation"
          ],
          "source_code": "..."
        }
    """.trimIndent()

    private fun buildPreprocessor(): TextPreprocessor {
        val config = TextPreprocessor.parseConfig(frozenConfigJson)
        // Build a preprocessor using the same factory logic but with in-memory config
        return TextPreprocessor.fromConfigForTesting(config)
    }

    // -------------------------------------------------------------------------
    // 1. Config parsing
    // -------------------------------------------------------------------------

    @Test
    fun `config parses successfully with correct field count`() {
        val config = TextPreprocessor.parseConfig(frozenConfigJson)
        assertNotNull(config)
        assertEquals("preprocess_for_classical", config.functionName)
        assertEquals(3, config.substitutionRules.size)
        assertEquals(2, config.otherSteps.size)
    }

    @Test
    fun `config substitution rule names are URL PHONE AMOUNT in order`() {
        val config = TextPreprocessor.parseConfig(frozenConfigJson)
        assertEquals("URL",    config.substitutionRules[0].name)
        assertEquals("PHONE",  config.substitutionRules[1].name)
        assertEquals("AMOUNT", config.substitutionRules[2].name)
    }

    @Test
    fun `config other steps contain lowercase and whitespace_normalisation`() {
        val config = TextPreprocessor.parseConfig(frozenConfigJson)
        assertTrue(config.otherSteps.contains("lowercase"))
        assertTrue(config.otherSteps.contains("whitespace_normalisation"))
    }

    // -------------------------------------------------------------------------
    // 2. Preprocessing pipeline
    // -------------------------------------------------------------------------

    @Test
    fun `plain text is lowercased`() {
        val p = buildPreprocessor()
        assertEquals("hello world", p.preprocess("HELLO WORLD"))
    }

    @Test
    fun `http url is replaced with URL token`() {
        val p = buildPreprocessor()
        val result = p.preprocess("Click here: https://malicious-site.xyz/reward")
        assertTrue("Expected [URL] in result but got: $result", result.contains("[URL]"))
    }

    @Test
    fun `www url is replaced with URL token`() {
        val p = buildPreprocessor()
        val result = p.preprocess("Visit www.win-prize.top now")
        assertTrue("Expected [URL] in result but got: $result", result.contains("[URL]"))
    }

    @Test
    fun `phone number is replaced with PHONE token`() {
        val p = buildPreprocessor()
        val result = p.preprocess("Call us at +91-9876543210 now")
        assertTrue("Expected [PHONE] in result but got: $result", result.contains("[PHONE]"))
    }

    @Test
    fun `rupee amount is replaced with AMOUNT token`() {
        val p = buildPreprocessor()
        val result = p.preprocess("You won Rs. 50,000 prize!")
        assertTrue("Expected [AMOUNT] in result but got: $result", result.contains("[AMOUNT]"))
    }

    @Test
    fun `multiple whitespace collapsed to single space`() {
        val p = buildPreprocessor()
        val result = p.preprocess("hello   world\t\t test")
        assertEquals("hello world test", result)
    }

    @Test
    fun `full fraud message preprocesses to expected token pattern`() {
        val p = buildPreprocessor()
        val input = "CONGRATULATIONS! You've WON Rs. 1,00,000! Click https://claim-prize.tk/win to claim. Call +91 9876543210"
        val result = p.preprocess(input)
        assertTrue(result.contains("[URL]"))
        assertTrue(result.contains("[PHONE]"))
        assertTrue(result.contains("[AMOUNT]"))
        // Should be fully lowercased (except substitution tokens)
        assertTrue("Non-substitution uppercase found: $result",
            result.replace("[URL]", "").replace("[PHONE]", "").replace("[AMOUNT]", "")
                .none { it.isUpperCase() })
    }

    @Test
    fun `empty string returns empty string`() {
        val p = buildPreprocessor()
        assertEquals("", p.preprocess(""))
    }

    @Test
    fun `ham message is unchanged except for lowercasing`() {
        val p = buildPreprocessor()
        val result = p.preprocess("See you at the office tomorrow morning")
        assertEquals("see you at the office tomorrow morning", result)
    }

    @Test
    fun `compiled rules do not set unsupported Android flags such as CANON_EQ`() {
        val config = TextPreprocessor.parseConfig(frozenConfigJson)
        val compiledRules = TextPreprocessor.compileRules(config.substitutionRules)

        for ((pattern, _) in compiledRules) {
            val flags = pattern.flags()
            // CANON_EQ (0x80) is not supported on Android libcore/ICU
            assertEquals("CANON_EQ (0x80) flag must NOT be set", 0, flags and java.util.regex.Pattern.CANON_EQ)
            assertEquals("Rule pattern must compile with CASE_INSENSITIVE (0x02)", java.util.regex.Pattern.CASE_INSENSITIVE, flags)
        }
    }
}
