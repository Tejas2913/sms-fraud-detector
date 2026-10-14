package com.smsfraud.detector.preprocessing

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.regex.Pattern

/**
 * Text Preprocessor — loads and applies the exact pipeline from preprocessing_config.json.
 *
 * The config file is the AUTHORITATIVE source for all substitution patterns and ordering.
 * No regex patterns are hard-coded in this class.
 *
 * Pipeline (as defined in preprocessing_config.json):
 *   1. lowercase
 *   2. URL      → [URL]
 *   3. PHONE    → [PHONE]
 *   4. AMOUNT   → [AMOUNT]
 *   5. whitespace normalisation (collapse + strip)
 *
 * This matches the Python function:
 *   `preprocess_for_classical(text: str) -> str`   (Notebook 04, Notebook 05)
 */
class TextPreprocessor private constructor(
    private val config: PreprocessingConfig,
    private val compiledRules: List<Pair<Pattern, String>>
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Applies the frozen preprocessing pipeline to [rawText] and returns the
     * preprocessed string suitable for both the Rule Engine and the ML Tokenizer.
     */
    fun preprocess(rawText: String): String {
        var text = rawText

        // Step 1: lowercase  (config: "other_steps" contains "lowercase")
        if ("lowercase" in config.otherSteps) {
            text = text.lowercase()
        }

        // Step 2–4: apply substitution rules in declaration order
        for ((pattern, replacement) in compiledRules) {
            text = pattern.matcher(text).replaceAll(replacement)
        }

        // Step 5: whitespace normalisation  (config: "whitespace_normalisation")
        if ("whitespace_normalisation" in config.otherSteps) {
            text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim()
        }

        return text
    }

    // -------------------------------------------------------------------------
    // Internal Kotlin serialization data classes (config schema)
    // -------------------------------------------------------------------------

    @Serializable
    data class SubstitutionRule(val name: String, val replacement: String, val pattern: String)

    @Serializable
    data class PreprocessingConfig(
        val functionName: String,
        val substitutionRules: List<SubstitutionRule>,
        val otherSteps: List<String>
    )

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        private val WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")

        /**
         * Loads [TextPreprocessor] from the app's bundled asset
         * `preprocessing_config.json`.
         *
         * Must be called off the main thread (reads from assets).
         * Throws [IllegalStateException] if the config is missing or malformed.
         */
        fun fromAssets(context: Context): TextPreprocessor {
            val configJson = context.assets
                .open("preprocessing_config.json")
                .bufferedReader()
                .use { it.readText() }

            val config = parseConfig(configJson)
            val compiledRules = compileRules(config.substitutionRules)
            return TextPreprocessor(config, compiledRules)
        }

        /**
         * Test-only factory: builds a [TextPreprocessor] directly from an already-parsed
         * [PreprocessingConfig] without requiring an Android [Context].
         * Used exclusively by unit tests in the `test/` source set.
         */
        @JvmStatic
        internal fun fromConfigForTesting(config: PreprocessingConfig): TextPreprocessor {
            val compiledRules = compileRules(config.substitutionRules)
            return TextPreprocessor(config, compiledRules)
        }

        /**
         * Compiles substitution rules using Android-compatible regex flags.
         * Uses [Pattern.CASE_INSENSITIVE] (0x02) for robust case-insensitive matching.
         *
         * Avoids passing bitwise NOT flags like [Pattern.CASE_INSENSITIVE.inv] (-3),
         * which sets bit 0x80 ([Pattern.CANON_EQ]) and causes Android libcore/ICU
         * to throw `IllegalArgumentException: CANON_EQ flag isn't supported`.
         */
        internal fun compileRules(rules: List<SubstitutionRule>): List<Pair<Pattern, String>> {
            return rules.map { rule ->
                val pattern = Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE)
                Pair(pattern, rule.replacement)
            }
        }

        /**
         * Parses the raw JSON config. The JSON uses snake_case keys which we map
         * via the @SerialName annotations in a manual transform because the config
         * ships with Python snake_case field names.
         */
        internal fun parseConfig(jsonText: String): PreprocessingConfig {
            // The JSON uses snake_case (function_name, substitution_rules, other_steps).
            // kotlinx.serialization expects camelCase by default, so we use a lenient
            // parser with explicit field mapping via a surrogate data class.
            val raw = jsonSurrogate.decodeFromString(RawConfig.serializer(), jsonText)
            return PreprocessingConfig(
                functionName = raw.function_name,
                substitutionRules = raw.substitution_rules.map { r ->
                    SubstitutionRule(name = r.name, replacement = r.replacement, pattern = r.pattern)
                },
                otherSteps = raw.other_steps
            )
        }

        private val jsonSurrogate = Json { ignoreUnknownKeys = true }

        // Surrogate classes that directly match the snake_case JSON schema
        @Serializable
        private data class RawSubstitutionRule(val name: String, val replacement: String, val pattern: String)

        @Serializable
        private data class RawConfig(
            val function_name: String,
            val substitution_rules: List<RawSubstitutionRule>,
            val other_steps: List<String>
        )
    }
}
