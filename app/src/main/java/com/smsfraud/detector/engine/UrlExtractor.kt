package com.smsfraud.detector.engine

import java.util.regex.Pattern

/**
 * Extracts raw URL strings from a message.
 *
 * The URL list produced here is passed to [UrlEngineImpl] and is also used to
 * set url_present in the pipeline (url_present = url_list.isNotEmpty()).
 *
 * Pattern matches http/https URLs and www-prefixed URLs, consistent with
 * what the preprocessing_config.json URL substitution pattern targets.
 *
 * IMPORTANT: This extractor must run on the RAW message (before preprocessing),
 * because preprocessing replaces URLs with the [URL] token.
 */
object UrlExtractor {

    private val URL_PATTERN: Pattern = Pattern.compile(
        """https?://\S+|www\.\S+|\b\S+\.(?:xyz|top|tk|ml|ga|click|info|ly|gl)(?:/\S*)?""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Returns the list of raw URL strings found in [rawMessage].
     * Returns an empty list if no URLs are found.
     */
    fun extract(rawMessage: String): List<String> {
        val matcher = URL_PATTERN.matcher(rawMessage)
        val urls = mutableListOf<String>()
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        return urls
    }
}
