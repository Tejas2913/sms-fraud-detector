package com.smsfraud.detector.ml

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * WordPiece tokenizer loaded from the exact HuggingFace artifacts shipped with the research system.
 *
 * Asset source: tokenizer.json / tokenizer_config.json / vocab.txt (deployment_package/tokenizer/)
 * Tokenizer class: DistilBertTokenizer (confirmed from tokenizer_config.json)
 * do_lower_case: true   (confirmed from tokenizer_config.json)
 * Special tokens (confirmed from special_tokens_map.json / tokenizer_config.json):
 *   [PAD]  = 0
 *   [UNK]  = 100
 *   [CLS]  = 101
 *   [SEP]  = 102
 *
 * Max sequence length used for research inference: 128 tokens (inclusive of [CLS] and [SEP]).
 * model_max_length in tokenizer_config.json is 512, but research experiments use 128.
 *
 * -----------------------------------------------------------------------
 * Parity note
 * -----------------------------------------------------------------------
 * The Python reference uses:
 *   tokenizer(text, max_length=128, padding='max_length',
 *              truncation=True, return_tensors='pt')
 *
 * This class replicates:
 *   1. Lowercasing  (do_lower_case = true)
 *   2. Basic tokenisation (whitespace split + punctuation splitting)
 *   3. WordPiece subword splitting against vocab.txt
 *   4. [CLS] prefix, [SEP] suffix
 *   5. Truncation to (maxLength - 2) content tokens
 *   6. Padding to maxLength with [PAD] (id = 0)
 *   7. Attention mask: 1 for real tokens, 0 for padding
 *
 * -----------------------------------------------------------------------
 * IMPORTANT: this tokenizer is loaded from vocab.txt only.
 * Do NOT substitute a third-party tokenizer or change normalisation rules.
 * -----------------------------------------------------------------------
 */
class BertTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val doLowerCase: Boolean
) {
    // Special token IDs — verified from tokenizer artifacts
    private val padId  = vocab["[PAD]"]  ?: 0
    private val unkId  = vocab["[UNK]"]  ?: 100
    private val clsId  = vocab["[CLS]"]  ?: 101
    private val sepId  = vocab["[SEP]"]  ?: 102

    /**
     * Tokenizes [text] and returns (input_ids, attention_mask), each of length [maxLength].
     *
     * This is the only public entry-point; it replicates the Python call:
     *   tokenizer(text, max_length=128, padding='max_length', truncation=True)
     */
    fun encode(text: String, maxLength: Int = MAX_LENGTH): TokenizerOutput {
        val normalised = if (doLowerCase) text.lowercase() else text
        val wordPieceTokens = tokenize(normalised)

        // Reserve 2 positions for [CLS] and [SEP]
        val contentMaxLen = maxLength - 2
        val truncated = if (wordPieceTokens.size > contentMaxLen)
            wordPieceTokens.subList(0, contentMaxLen)
        else
            wordPieceTokens

        val ids = LongArray(maxLength)
        val mask = LongArray(maxLength)

        // [CLS]
        ids[0] = clsId.toLong()
        mask[0] = 1L

        // Content tokens
        for (i in truncated.indices) {
            ids[i + 1] = (vocab[truncated[i]] ?: unkId).toLong()
            mask[i + 1] = 1L
        }

        // [SEP]
        val sepPos = truncated.size + 1
        ids[sepPos] = sepId.toLong()
        mask[sepPos] = 1L

        // Padding: ids already zero-initialised ([PAD]=0); mask stays 0
        // (Only needed if padId != 0, but DistilBert uses padId=0)
        if (padId != 0) {
            for (i in (sepPos + 1) until maxLength) ids[i] = padId.toLong()
        }

        return TokenizerOutput(inputIds = ids, attentionMask = mask)
    }

    // -------------------------------------------------------------------------
    // WordPiece implementation
    // -------------------------------------------------------------------------

    /**
     * Produces a flat list of WordPiece tokens for the full input string.
     * Replicates HuggingFace BasicTokenizer + WordpieceTokenizer.
     */
    internal fun tokenize(text: String): List<String> {
        val result = mutableListOf<String>()
        for (word in basicTokenize(text)) {
            result.addAll(wordPiece(word))
        }
        return result
    }

    /** Whitespace split + punctuation isolation (mirrors HuggingFace BasicTokenizer). */
    private fun basicTokenize(text: String): List<String> {
        val spaced = buildString {
            for (ch in text) {
                if (isPunctuation(ch) || isCjk(ch)) { append(' '); append(ch); append(' ') }
                else append(ch)
            }
        }
        return spaced.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    /** Greedy WordPiece subword algorithm against [vocab]. */
    private fun wordPiece(word: String, maxCharsPerToken: Int = 100): List<String> {
        if (word.length > maxCharsPerToken) return listOf("[UNK]")
        val subTokens = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var curSubStr: String? = null
            while (start < end) {
                val substr = (if (start == 0) "" else "##") + word.substring(start, end)
                if (vocab.containsKey(substr)) { curSubStr = substr; break }
                end--
            }
            if (curSubStr == null) return listOf("[UNK]")
            subTokens.add(curSubStr)
            start = end
        }
        return subTokens
    }

    private fun isPunctuation(ch: Char): Boolean {
        val cp = ch.code
        return (cp in 33..47) || (cp in 58..64) || (cp in 91..96) || (cp in 123..126) ||
            ch.category in setOf(
                CharCategory.CONNECTOR_PUNCTUATION,
                CharCategory.DASH_PUNCTUATION,
                CharCategory.END_PUNCTUATION,
                CharCategory.FINAL_QUOTE_PUNCTUATION,
                CharCategory.INITIAL_QUOTE_PUNCTUATION,
                CharCategory.OTHER_PUNCTUATION,
                CharCategory.START_PUNCTUATION
            )
    }

    private fun isCjk(ch: Char): Boolean {
        val cp = ch.code
        return (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF) ||
            (cp in 0x20000..0x2A6DF) || (cp in 0x2A700..0x2B73F) ||
            (cp in 0x2B740..0x2B81F) || (cp in 0x2B820..0x2CEAF) ||
            (cp in 0xF900..0xFAFF) || (cp in 0x2F800..0x2FA1F)
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        /** Research inference sequence length — must match Python reference. */
        const val MAX_LENGTH = 128

        private val jsonParser = Json { ignoreUnknownKeys = true }

        /**
         * Loads [BertTokenizer] from the bundled `vocab.txt` and `tokenizer_config.json` assets.
         * Must be called off the main thread.
         */
        fun fromAssets(context: Context): BertTokenizer {
            val vocab = loadVocab(context)
            val doLowerCase = loadDoLowerCase(context)
            return BertTokenizer(vocab, doLowerCase)
        }

        private fun loadVocab(context: Context): Map<String, Int> {
            return context.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                lines.mapIndexed { index, token -> token.trim() to index }
                    .toMap()
            }
        }

        private fun loadDoLowerCase(context: Context): Boolean {
            val text = context.assets.open("tokenizer_config.json").bufferedReader().readText()
            val obj = jsonParser.parseToJsonElement(text).jsonObject
            return obj["do_lower_case"]?.jsonPrimitive?.content?.toBoolean() ?: true
        }
    }

    /**
     * Output of a single tokenization call.
     * Both arrays are length == maxLength (default 128).
     */
    data class TokenizerOutput(
        val inputIds: LongArray,
        val attentionMask: LongArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TokenizerOutput) return false
            return inputIds.contentEquals(other.inputIds) &&
                attentionMask.contentEquals(other.attentionMask)
        }
        override fun hashCode(): Int =
            31 * inputIds.contentHashCode() + attentionMask.contentHashCode()
    }
}
