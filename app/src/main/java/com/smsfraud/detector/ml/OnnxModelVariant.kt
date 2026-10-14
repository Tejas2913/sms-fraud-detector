package com.smsfraud.detector.ml

/**
 * Selects which ONNX model variant to use for inference.
 *
 * Both variants are packaged inside the APK as Android assets.
 *
 * FP32  — Full-precision DistilBERT.  Asset: distilbert_fraud.onnx              (~268 MB)
 *          Use case: Python ↔ Android FP32 exact parity baseline.
 *
 * INT8  — Dynamic-quantised DistilBERT. Asset: distilbert_fraud_dynamic_int8.onnx (~64 MB)
 *          Use case: Python ↔ Android INT8 parity + device latency benchmarking.
 *
 * Both variants share the same verified ONNX model interface:
 *   Inputs : input_ids      (INT64, [1, 128])
 *            attention_mask  (INT64, [1, 128])
 *   Output : output          (FLOAT, [1, 2])
 *                             Index 0 = ham logit
 *                             Index 1 = fraud logit
 *
 * Load via: OnnxRunner.fromAssets(context, OnnxModelVariant.FP32)
 *           OnnxRunner.fromAssets(context, OnnxModelVariant.INT8)
 */
enum class OnnxModelVariant(
    /** Asset filename as deployed to app/src/main/assets/. */
    val assetFileName: String,
    /** Human-readable label matching Python reference output labels. */
    val label: String
) {
    FP32(
        assetFileName = "distilbert_fraud.onnx",
        label = "FP32"
    ),
    INT8(
        assetFileName = "distilbert_fraud_dynamic_int8.onnx",
        label = "INT8"
    );
}
