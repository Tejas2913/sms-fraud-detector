package com.smsfraud.detector.ml

/**
 * Raw output from one ONNX inference pass.
 *
 * [variant]         — which model produced this result (FP32 / INT8)
 * [logits]          — raw output tensor, shape [2]:  [logit_ham, logit_fraud]
 * [fraudProbability]— softmax(logits)[1], i.e. P(fraud).
 *                     Index 1 is the fraud class (confirmed from FP32 binary header scan).
 * [inferenceMs]     — wall-clock inference duration (model session.run only, excludes tokenisation)
 *
 * Do NOT apply a threshold or emit a decision from this class.
 * That is the responsibility of the Decision Controller (Milestone 3).
 */
data class OnnxInferenceResult(
    val variant: OnnxModelVariant,
    val logits: FloatArray,          // size == 2: [ham_logit, fraud_logit]
    val fraudProbability: Float,     // softmax applied, range [0, 1]
    val inferenceMs: Long
) {
    init {
        require(logits.size == 2) { "Expected 2 logits [ham, fraud], got ${logits.size}" }
        require(fraudProbability in 0f..1f) { "fraudProbability out of [0,1]: $fraudProbability" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OnnxInferenceResult) return false
        return variant == other.variant &&
            logits.contentEquals(other.logits) &&
            fraudProbability == other.fraudProbability &&
            inferenceMs == other.inferenceMs
    }

    override fun hashCode(): Int {
        var result = variant.hashCode()
        result = 31 * result + logits.contentHashCode()
        result = 31 * result + fraudProbability.hashCode()
        result = 31 * result + inferenceMs.hashCode()
        return result
    }
}
