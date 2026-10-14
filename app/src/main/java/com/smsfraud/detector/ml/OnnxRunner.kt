package com.smsfraud.detector.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlin.math.exp

/**
 * On-device DistilBERT ONNX inference runner.
 *
 * Supports both FP32 and dynamic-INT8 model variants, both packaged as APK assets.
 *
 * Verified model interface (from binary header scan of deployment artifacts):
 *   Input  "input_ids"     : INT64  [batch_size, sequence_length]
 *   Input  "attention_mask": INT64  [batch_size, sequence_length]
 *   Output "output"        : FLOAT  [batch_size, 2]
 *                            Index 0 = ham logit
 *                            Index 1 = fraud logit
 *
 * Usage:
 *   val runner = OnnxRunner.fromAssets(context, OnnxModelVariant.FP32)
 *   val result = runner.run(inputIds, attentionMask)
 *   runner.close()
 *
 * Thread-safety: A single OrtSession is NOT thread-safe for concurrent calls.
 * Use one runner per coroutine scope / thread, or synchronise externally.
 *
 * CPU-only execution with single-threaded ops for reproducible research measurements.
 * Lifecycle: call [close] when done. The [OrtEnvironment] is a singleton managed by
 * ONNX Runtime and must not be closed manually.
 */
class OnnxRunner private constructor(
    private val session: OrtSession,
    val variant: OnnxModelVariant
) : AutoCloseable {

    /**
     * Runs inference for a single pre-tokenised sequence.
     *
     * @param inputIds      LongArray of length [BertTokenizer.MAX_LENGTH] (128)
     * @param attentionMask LongArray of length [BertTokenizer.MAX_LENGTH] (128)
     * @return [OnnxInferenceResult] with raw logits, fraud probability, and latency
     */
    fun run(inputIds: LongArray, attentionMask: LongArray): OnnxInferenceResult {
        val env = OrtEnvironment.getEnvironment()

        // Reshape 1D arrays to [1, seqLen] tensors required by the model
        val batchInputIds = Array(1) { inputIds }
        val batchMask    = Array(1) { attentionMask }

        val tensorIds  = OnnxTensor.createTensor(env, batchInputIds)
        val tensorMask = OnnxTensor.createTensor(env, batchMask)

        val inputs = mapOf(
            INPUT_IDS      to tensorIds,
            ATTENTION_MASK to tensorMask
        )

        val startNs = System.nanoTime()
        val outputs = session.run(inputs)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        // Extract output tensor ("logits" in DistilBERT ONNX model) → shape [1, 2] → FloatArray
        val optionalValue = outputs.get(OUTPUT_NAME)
        val onnxValue = if (optionalValue.isPresent) {
            optionalValue.get()
        } else {
            // Fallback to first available output tensor if output name differs
            outputs.get(0)
        }
        @Suppress("UNCHECKED_CAST")
        val logits2D = (onnxValue.value as Array<FloatArray>)[0]
        val logits   = floatArrayOf(logits2D[0], logits2D[1])

        tensorIds.close()
        tensorMask.close()
        outputs.close()

        return OnnxInferenceResult(
            variant          = variant,
            logits           = logits,
            fraudProbability = softmaxFraud(logits[0], logits[1]),
            inferenceMs      = elapsedMs
        )
    }

    /** Releases the native ONNX Runtime session. */
    override fun close() {
        session.close()
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        private const val INPUT_IDS      = "input_ids"
        private const val ATTENTION_MASK = "attention_mask"
        private const val OUTPUT_NAME    = "logits"

        /**
         * Opens an [OnnxRunner] for [variant] by reading the model asset into memory.
         *
         * ⚠  Large file note: FP32 model is ~268 MB. This reads the entire model into
         * a byte array. Always call on a background thread / coroutine and handle
         * [OutOfMemoryError] gracefully on low-memory devices.
         *
         * Must be called off the main thread.
         */
        fun fromAssets(context: Context, variant: OnnxModelVariant): OnnxRunner {
            val modelBytes = context.assets
                .open(variant.assetFileName)
                .use { it.readBytes() }
            val env  = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                // CPU execution provider only — no NNAPI/GPU for reproducible results
                setInterOpNumThreads(1)
                setIntraOpNumThreads(1)
            }
            val session = env.createSession(modelBytes, opts)
            return OnnxRunner(session, variant)
        }
    }
}

// -------------------------------------------------------------------------
// Softmax utility — package-internal
// -------------------------------------------------------------------------

/**
 * Numerically-stable softmax over two logits; returns P(fraud) = softmax[1].
 *
 * Matches Python reference:
 *   torch.softmax(torch.tensor([logit_ham, logit_fraud]), dim=0)[1].item()
 * or equivalently:
 *   scipy.special.softmax([logit_ham, logit_fraud])[1]
 */
internal fun softmaxFraud(logitHam: Float, logitFraud: Float): Float {
    val maxLogit = maxOf(logitHam, logitFraud)
    val eHam     = exp((logitHam   - maxLogit).toDouble())
    val eFraud   = exp((logitFraud - maxLogit).toDouble())
    return (eFraud / (eHam + eFraud)).toFloat()
}
