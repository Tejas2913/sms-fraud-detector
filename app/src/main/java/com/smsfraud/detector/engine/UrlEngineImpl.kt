package com.smsfraud.detector.engine

import com.smsfraud.detector.domain.engine.UrlEngine
import com.smsfraud.detector.domain.model.UrlEntry
import com.smsfraud.detector.domain.model.UrlResult
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * URL Intelligence Engine — exact implementation of Notebook 04 Cell 19.
 *
 * FROZEN. No live URL lookups, no Bloom filter, no internet access.
 * Fully offline, deterministic pattern scoring.
 *
 * Scoring logic (verbatim from NB04):
 *   No URL present → risk=0.0, confidence=1.0, label="no_url"
 *   Any URL present → baseline risk=0.20, label="unclassified_url"
 *     Known malicious patterns → risk = max(risk, 0.95), label="known_malicious_pattern"
 *     High-risk TLDs           → risk = max(risk, 0.75), label="high_risk_tld" (if still unclassified)
 *     URL shorteners           → risk = max(risk, 0.60), label="shortener" (if still unclassified)
 *   confidence = 0.90 if label != "unclassified_url" else 0.65
 *
 * NOTE: Label assignment for TLD and shortener uses the SAME conditional as NB04:
 *   label is only updated if it is still "unclassified_url" at that check.
 *   This means "known_malicious_pattern" takes precedence over "high_risk_tld"
 *   and "shortener" — the first assignment wins for label (not risk).
 */
class UrlEngineImpl @Inject constructor() : UrlEngine {

    override fun evaluate(urlList: List<UrlEntry>): UrlResult {
        val rawUrls = urlList.map { it.rawUrl }
        val urlPresent = urlList.isNotEmpty()
        return score(rawUrls, urlPresent)
    }

    /**
     * Research-parity method: accepts raw URL strings exactly as NB04 does.
     * The [evaluate] interface adapter calls this internally.
     */
    fun score(urlList: List<String>, urlPresent: Boolean): UrlResult {
        if (!urlPresent) {
            return UrlResult(
                urlRisk       = 0.0f,
                urlConfidence = 1.0f,
                urlLabel      = "no_url",
                urlPresent    = false
            )
        }

        var risk  = 0.20f           // baseline for any URL present
        var label = "unclassified_url"

        for (url in urlList) {
            // Check 1: known malicious patterns (highest priority — overrides all)
            if (KNOWN_MALICIOUS_PATTERNS.any { it.matcher(url).find() }) {
                risk  = maxOf(risk, 0.95f)
                label = "known_malicious_pattern"
            }
            // Check 2: high-risk TLDs (only update label if still unclassified)
            if (HIGH_RISK_TLDS.matcher(url).find()) {
                risk  = maxOf(risk, 0.75f)
                if (label == "unclassified_url") label = "high_risk_tld"
            }
            // Check 3: URL shorteners (only update label if still unclassified)
            if (URL_SHORTENERS.matcher(url).find()) {
                risk  = maxOf(risk, 0.60f)
                if (label == "unclassified_url") label = "shortener"
            }
        }

        val confidence = if (label != "unclassified_url") 0.90f else 0.65f

        return UrlResult(
            urlRisk       = risk,
            urlConfidence = confidence,
            urlLabel      = label,
            urlPresent    = true
        )
    }

    companion object {
        // -----------------------------------------------------------------------
        // Frozen patterns — verbatim from Notebook 04 Cell 19
        // -----------------------------------------------------------------------

        /** Known malicious domain/path patterns */
        val KNOWN_MALICIOUS_PATTERNS: List<Pattern> = listOf(
            Pattern.compile("""secure-?(login|verify|update)""",           Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(bank|kyc|paytm|upi)-?(verify|update|confirm)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\.(tk|ml|ga|cf|gq)(/|$)""",                Pattern.CASE_INSENSITIVE)
        )

        /** High-risk free TLDs — superset of the malicious TLD list, includes .xyz and .top */
        val HIGH_RISK_TLDS: Pattern = Pattern.compile(
            """\.(tk|ml|ga|cf|gq|xyz|top)(/|$)""",
            Pattern.CASE_INSENSITIVE
        )

        /** Common URL shortener domains */
        val URL_SHORTENERS: Pattern = Pattern.compile(
            """(bit\.ly|tinyurl|t\.co|goo\.gl|is\.gd|cutt\.ly)""",
            Pattern.CASE_INSENSITIVE
        )
    }
}
