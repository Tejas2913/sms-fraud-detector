package com.smsfraud.detector.domain.model

/**
 * Extracted URL entry per Architecture v2 Layer 2C.
 * Represents a single URL found during text preprocessing.
 * UrlEngine receives a list of these rather than raw strings
 * to allow structured analysis (domain, TLD, path, shortener detection).
 */
data class UrlEntry(
    val rawUrl: String,
    val domain: String,
    val tld: String,
    val path: String,
    val isShortener: Boolean = false
)
