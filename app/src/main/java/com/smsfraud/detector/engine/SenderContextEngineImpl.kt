package com.smsfraud.detector.engine

import com.smsfraud.detector.data.contacts.AndroidContactResolver
import com.smsfraud.detector.data.repository.SettingsRepository
import com.smsfraud.detector.domain.engine.SenderContextEngine
import com.smsfraud.detector.domain.model.SenderResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sender Context Engine — Dual Mode (Real Android + Reproducibility Parity).
 *
 * Real Android Mode (`evaluate`):
 *   Checks actual device contacts via [AndroidContactResolver] if enabled in settings
 *   and permission granted. Saved contacts return:
 *     - risk = 0.10, confidence = 0.85, label = "contact_saved",
 *       inContacts = true, trusted = true
 *   If not in contacts / permission missing / disabled: falls back to research simulation.
 *
 * Research Reproducibility Mode (`scoreFromMetadata`):
 *   Implements the EXACT Notebook 04 Cell 20 simulation logic.
 *   MUST NOT be modified or connected to Android ContactsContract.
 */
@Singleton
class SenderContextEngineImpl @Inject constructor(
    private val contactResolver: AndroidContactResolver,
    private val settingsRepository: SettingsRepository
) : SenderContextEngine {

    /** Interface-conforming method for the on-device pipeline (Android senderString heuristic). */
    override suspend fun evaluate(senderString: String): SenderResult {
        val trimmed = senderString.trim()

        // 1. Check if contact lookup is enabled in user settings (default: enabled)
        val isLookupEnabled = settingsRepository.isContactLookupEnabled()

        // 2. Query real Android ContactsContract via resolver
        if (isLookupEnabled && contactResolver.isInContacts(trimmed)) {
            return SenderResult(
                senderRisk                    = 0.10f,
                senderConfidence              = 0.85f,
                senderLabel                   = "contact_saved",
                senderInContacts              = true,
                trustedSenderRecommendation   = true
            )
        }

        // 3. Fallback to existing NB04 simulation heuristic
        val (source, ruleCategory) = classifySender(senderString)
        return scoreFromMetadata(source, ruleCategory)
    }

    /**
     * Parity method — accepts explicit NB04 metadata fields.
     * Use this for Python-parity testing and the debug pipeline interface.
     *
     * @param source        "synthetic" or "real"
     * @param ruleCategory  e.g. "otp", "banking", "normal", "fraud", etc.
     */
    fun scoreFromMetadata(source: String, ruleCategory: String): SenderResult {
        if (source == "synthetic") {
            return SenderResult(
                senderRisk                    = 0.55f,
                senderConfidence              = 0.55f,
                senderLabel                   = "unknown_sender",
                senderInContacts              = false,
                trustedSenderRecommendation   = false
            )
        }
        val normCat = ruleCategory.lowercase()
        if (normCat in TRUSTED_CATEGORIES || normCat == "otpdetect" || normCat == "banktransactiontemplate") {
            return SenderResult(
                senderRisk                    = 0.10f,
                senderConfidence              = 0.85f,
                senderLabel                   = "registered_business_sender",
                senderInContacts              = true,
                trustedSenderRecommendation   = true
            )
        }
        return SenderResult(
            senderRisk                    = 0.40f,
            senderConfidence              = 0.65f,
            senderLabel                   = "unregistered_sender",
            senderInContacts              = false,
            trustedSenderRecommendation   = false
        )
    }

    // -------------------------------------------------------------------------
    // Android sender-ID heuristic (on-device mapping)
    // -------------------------------------------------------------------------

    private fun classifySender(senderString: String): Pair<String, String> {
        val trimmed = senderString.trim()

        // Pure numeric or international phone numbers → unknown/synthetic
        if (trimmed.matches(Regex("""^\+?\d[\d\s\-]{6,}"""))) {
            return "synthetic" to "normal"
        }

        // Alphanumeric sender IDs containing OTP/banking keywords → trusted simulation
        val upper = trimmed.uppercase()
        if (OTP_BANK_KEYWORDS.any { upper.contains(it) }) {
            return "real" to "otp"
        }

        // All other alphanumeric sender IDs (e.g. "AMAZON", "MYNTRA") → unregistered
        return "real" to "normal"
    }

    companion object {
        // Trusted categories — verbatim from Notebook 04
        val TRUSTED_CATEGORIES: Set<String> = setOf("otp", "banking")

        // Heuristic keywords for on-device OTP/bank sender ID detection
        private val OTP_BANK_KEYWORDS = setOf(
            "OTP", "BANK", "SBI", "HDFC", "ICICI", "AXIS", "KOTAK",
            "UPI", "PAYTM", "GPAY", "PHONEPE", "KYC", "NEFT", "IMPS"
        )
    }
}
