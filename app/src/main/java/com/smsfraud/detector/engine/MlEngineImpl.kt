package com.smsfraud.detector.engine

import android.content.Context
import com.smsfraud.detector.domain.engine.MlEngine
import com.smsfraud.detector.domain.model.MlResult
import com.smsfraud.detector.ml.BertTokenizer
import com.smsfraud.detector.ml.OnnxModelVariant
import com.smsfraud.detector.ml.OnnxRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Engine — DistilBERT ONNX inference integration.
 *
 * Implements the exact Notebook 04 Cell 21 ml_confidence formula and wraps
 * the Milestone 1 [OnnxRunner] + [BertTokenizer] for on-device inference.
 *
 * ml_confidence formula (verbatim from NB04 Cell 21):
 *   threshold = OPERATING_THRESHOLD = 0.25
 *   if proba <= threshold:
 *       confidence = (threshold - proba) / threshold
 *   else:
 *       confidence = (proba - threshold) / (1.0 - threshold)
 *
 * This produces confidence=0 when proba==threshold (most uncertain) and
 * confidence=1 when proba==0 or proba==1 (most certain). It is anchored
 * on the ML engine's operating threshold (0.25), NOT on 0.5.
 *
 * Lifecycle:
 *   - [BertTokenizer] and [OnnxRunner] are loaded lazily on first predict() call.
 *   - Both are kept alive for the lifetime of this singleton (Hilt @Singleton).
 *   - OnnxRunner is AutoCloseable; it will be closed if this class is destroyed.
 *
 * Default variant is INT8 (fastest, fully packaged in APK).
 * Switch to FP32 for parity baseline testing via the secondary constructor or
 * the [activeVariant] setter.
 *
 * IMPORTANT: predict() performs disk I/O on first call (asset loading).
 * Always call from a background coroutine / IO dispatcher.
 */
@Singleton
class MlEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MlEngine {

    /** The model variant currently loaded. Default: INT8. */
    var activeVariant: OnnxModelVariant = OnnxModelVariant.INT8
        set(value) {
            if (field != value) {
                field = value
                // Invalidate cached runner so it's reloaded with new variant
                synchronized(lock) {
                    onnxRunner?.close()
                    onnxRunner = null
                }
            }
        }

    private val lock = Any()
    private var onnxRunner: OnnxRunner? = null
    private var tokenizer: BertTokenizer? = null

    override fun predict(normalisedText: String): MlResult {
        val runner    = getOrLoadRunner()
        val tokenizer = getOrLoadTokenizer()

        val encoded   = tokenizer.encode(normalisedText)
        android.util.Log.d(TAG, "TOKENIZATION OK")

        val inference = runner.run(encoded.inputIds, encoded.attentionMask)
        android.util.Log.d(TAG, "ML OK")

        val proba      = inference.fraudProbability
        val confidence = mlConfidenceFromProba(proba)

        return MlResult(
            mlScore           = proba,
            mlConfidence      = confidence,
            attentionWeights  = emptyList()  // attention not extracted from ONNX session
        )
    }

    // -------------------------------------------------------------------------
    // Lazy loading
    // -------------------------------------------------------------------------

    private fun getOrLoadRunner(): OnnxRunner = synchronized(lock) {
        onnxRunner ?: OnnxRunner.fromAssets(context, activeVariant).also { onnxRunner = it }
    }

    private fun getOrLoadTokenizer(): BertTokenizer {
        return tokenizer ?: BertTokenizer.fromAssets(context).also { tokenizer = it }
    }

    // -------------------------------------------------------------------------
    // Companion — frozen constants and parity helpers
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "MlEngineImpl"

        /**
         * ML engine operating threshold — frozen from Notebook 04.
         * Used ONLY for ml_confidence computation, NOT for binary classification.
         * The Decision Controller (not MlEngine) makes all classification decisions.
         */
        const val OPERATING_THRESHOLD = 0.25f

        /**
         * Computes ml_confidence from a fraud probability.
         * Exact implementation of ml_confidence_from_proba() in Notebook 04 Cell 21.
         *
         * @param proba     fraud probability from softmax, range [0, 1]
         * @param threshold ML operating threshold (default = OPERATING_THRESHOLD = 0.25)
         */
        fun mlConfidenceFromProba(proba: Float, threshold: Float = OPERATING_THRESHOLD): Float {
            return if (proba <= threshold) {
                (threshold - proba) / threshold
            } else {
                (proba - threshold) / (1.0f - threshold)
            }
        }
    }
}
