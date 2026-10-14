package com.smsfraud.detector.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * JVM unit tests for BertTokenizer.
 *
 * Loads vocab.txt directly from the workspace deployment_package/tokenizer/ folder
 * so these tests run without Android context.
 *
 * The vocab path is resolved relative to the Gradle project root via the
 * system property "user.dir" (set automatically during ./gradlew test).
 *
 * Verified special token IDs from tokenizer_config.json / special_tokens_map.json:
 *   [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102
 */
class BertTokenizerTest {

    private lateinit var tokenizer: BertTokenizer

    @Before
    fun setUp() {
        // Resolve vocab.txt from the deployment package (no Android context needed)
        val projectRoot = File(System.getProperty("user.dir") ?: ".")
        // Gradle runs tests with cwd = app/ module directory, so go up one level
        val vocabFile = sequenceOf(
            File(projectRoot, "deployment_package/tokenizer/vocab.txt"),
            File(projectRoot, "../deployment_package/tokenizer/vocab.txt"),
            File(projectRoot, "../../deployment_package/tokenizer/vocab.txt")
        ).firstOrNull { it.exists() }
            ?: error("vocab.txt not found; searched from $projectRoot. Ensure deployment_package is present.")

        val vocab = vocabFile.bufferedReader().useLines { lines ->
            lines.mapIndexed { idx, token -> token.trim() to idx }.toMap()
        }
        tokenizer = BertTokenizer::class.java
            .getDeclaredConstructor(Map::class.java, Boolean::class.javaPrimitiveType)
            .also { it.isAccessible = true }
            .newInstance(vocab, true) as BertTokenizer
    }

    // -------------------------------------------------------------------------
    // 1. Structural / length checks
    // -------------------------------------------------------------------------

    @Test
    fun `encode always returns arrays of length 128`() {
        val out = tokenizer.encode("hello world")
        assertEquals(128, out.inputIds.size)
        assertEquals(128, out.attentionMask.size)
    }

    @Test
    fun `empty string produces CLS SEP then all PAD`() {
        val out = tokenizer.encode("")
        assertEquals(101L, out.inputIds[0])  // [CLS]
        assertEquals(102L, out.inputIds[1])  // [SEP]
        assertEquals(0L,   out.inputIds[2])  // [PAD]
        assertEquals(1L,   out.attentionMask[0])
        assertEquals(1L,   out.attentionMask[1])
        assertEquals(0L,   out.attentionMask[2])
    }

    @Test
    fun `CLS token is always at position 0`() {
        val out = tokenizer.encode("click now to claim your prize")
        assertEquals("inputIds[0] must be [CLS]=101", 101L, out.inputIds[0])
        assertEquals("attentionMask[0] must be 1",    1L,   out.attentionMask[0])
    }

    @Test
    fun `SEP token appears immediately after last real token`() {
        val out = tokenizer.encode("win")
        // Expect: [CLS] "win" [SEP] [PAD]...
        assertEquals(101L, out.inputIds[0])  // [CLS]
        assertEquals(102L, out.inputIds[2])  // [SEP] after 1 token
        assertEquals(0L,   out.inputIds[3])  // [PAD]
        assertEquals(1L,   out.attentionMask[2])
        assertEquals(0L,   out.attentionMask[3])
    }

    @Test
    fun `attention mask has 1 for real tokens and 0 for padding`() {
        val out = tokenizer.encode("hello")
        // 1 real token + [CLS] + [SEP] = 3 non-padding positions
        assertEquals(1L, out.attentionMask[0])
        assertEquals(1L, out.attentionMask[1])
        assertEquals(1L, out.attentionMask[2])
        assertEquals(0L, out.attentionMask[3])
    }

    @Test
    fun `long text is truncated to 128 tokens total`() {
        val longText = "word ".repeat(200)
        val out = tokenizer.encode(longText)
        assertEquals(128, out.inputIds.size)
        // Last token must be [SEP]
        assertEquals(102L, out.inputIds[127])
    }

    // -------------------------------------------------------------------------
    // 2. Special token IDs (verified against tokenizer artifacts)
    // -------------------------------------------------------------------------

    @Test
    fun `PAD token ID is 0`() {
        val out = tokenizer.encode("")
        // Position 2 onward should be PAD=0
        assertEquals(0L, out.inputIds[2])
    }

    @Test
    fun `known word hello tokenizes to known ID`() {
        // "hello" is in DistilBERT vocab as a single token
        val tokens = tokenizer.tokenize("hello")
        assertEquals(listOf("hello"), tokens)
    }

    @Test
    fun `lowercasing is applied before tokenization`() {
        val out1 = tokenizer.encode("HELLO WORLD")
        val out2 = tokenizer.encode("hello world")
        assertTrue("UPPERCASE and lowercase should produce identical token IDs",
            out1.inputIds.contentEquals(out2.inputIds))
    }

    // -------------------------------------------------------------------------
    // 3. Tokenize edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `unknown words produce UNK token`() {
        // A 101-character string with no spaces should exceed maxCharsPerToken
        val gibberish = "x".repeat(101)
        val tokens = tokenizer.tokenize(gibberish)
        assertEquals(listOf("[UNK]"), tokens)
    }

    @Test
    fun `punctuation is split from adjacent words`() {
        val tokens = tokenizer.tokenize("hello,world")
        // "hello" "," "world" should each be separate tokens
        assertTrue("comma should be a separate token; got $tokens",
            tokens.contains(","))
    }
}
