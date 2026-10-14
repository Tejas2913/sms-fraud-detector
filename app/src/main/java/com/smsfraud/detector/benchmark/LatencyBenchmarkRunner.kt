package com.smsfraud.detector.benchmark

import android.content.Context
import android.util.Log
import com.smsfraud.detector.engine.MlEngineImpl
import com.smsfraud.detector.ml.BertTokenizer
import com.smsfraud.detector.ml.OnnxModelVariant
import com.smsfraud.detector.ml.OnnxRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * On-Device Physical Latency Benchmark Runner for DistilBERT INT8.
 *
 * Target Device: Xiaomi POCO X5 Pro 5G (SM7325 / Snapdragon 778G / Android 14 / API 34).
 * Deployment Configuration: INT8-only arm64-v8a ONNX Runtime Mobile.
 *
 * Protocol:
 * 1. Warm-up Phase: 10 unmeasured inference runs to prime ART JIT, ONNX session buffers, and memory caches.
 * 2. Measurement Phase: 100 repeated measured inference runs across 10 diverse SMS test samples.
 * 3. Timing: Monotonic high-resolution timer (System.nanoTime()) converted to fractional milliseconds.
 * 4. Metrics separated:
 *    - Model Inference Latency (T_model): pure ONNX Runtime INT8 C++ graph execution (session.run)
 *    - Tokenization Latency (T_token): WordPiece encoding + padding to 128 tokens
 *    - Complete ML Engine Processing Latency (T_ml_engine): Tokenization + ONNX inference + Softmax + Confidence
 */
object LatencyBenchmarkRunner {
    private const val TAG = "BENCHMARK_RESULT"
    const val WARMUP_RUNS = 10
    const val MEASURED_RUNS = 100

    val BENCHMARK_SAMPLES = listOf(
        "URGENT: Your State Bank account 4829 has been suspended due to suspicious activity. Verify KYC immediately at http://sbi-kyc-update.top to prevent permanent block.",
        "Your OTP for transaction of INR 2,499.00 at Flipkart is 739201. Valid for 5 mins. Do not share OTP with anyone, including bank staff.",
        "Your package with tracking number IN892341 could not be delivered due to incorrect address. Update delivery details within 24h at http://indiapost-parcel-reschedule.com",
        "Hi Dad, I reached the office safely. Will be home by 7 PM today.",
        "Dear customer, your electricity bill of Rs 1,850 is overdue for account 1092834. Pay before 15th to avoid power disconnection.",
        "CONGRATULATIONS! You have won a cash prize of Rs 25,00,000 in Kaun Banega Crorepati lucky draw. Claim your reward now: http://kbc-winner-prize.in",
        "Your Airtel broadband bill for account 011482910 is generated: Rs 943. Due date: 20-Oct. Pay via Airtel Thanks app.",
        "Hey, are you free for lunch tomorrow around 1pm? Let me know.",
        "ALERT: Suspicious login attempt detected from IP 192.168.1.105. If this was not you, change your password immediately at https://security-login-verify.net",
        "Your food delivery from Swiggy order #492810 is on the way. Delivery partner Rahul will reach in 15 mins."
    )

    data class RunMeasurement(
        val runIndex: Int,
        val sampleIndex: Int,
        val textLength: Int,
        val tokenizationLatencyMs: Double,
        val modelInferenceLatencyMs: Double,
        val totalMlEngineLatencyMs: Double,
        val fraudProbability: Float
    )

    data class LatencyStats(
        val count: Int,
        val mean: Double,
        val median: Double,
        val min: Double,
        val max: Double,
        val stdDev: Double,
        val p90: Double,
        val p95: Double,
        val p99: Double
    )

    fun calculateStats(values: List<Double>): LatencyStats {
        val sorted = values.sorted()
        val count = sorted.size
        val mean = sorted.average()
        val median = if (count % 2 == 0) {
            (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
        } else {
            sorted[count / 2]
        }
        val min = sorted.first()
        val max = sorted.last()
        val variance = sorted.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        fun percentile(p: Double): Double {
            val rank = p * (count - 1)
            val lower = rank.toInt()
            val upper = lower + 1
            val weight = rank - lower
            return if (upper < count) {
                sorted[lower] * (1.0 - weight) + sorted[upper] * weight
            } else {
                sorted[lower]
            }
        }

        return LatencyStats(
            count = count,
            mean = mean,
            median = median,
            min = min,
            max = max,
            stdDev = stdDev,
            p90 = percentile(0.90),
            p95 = percentile(0.95),
            p99 = percentile(0.99)
        )
    }

    suspend fun runBenchmark(context: Context): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING ON-DEVICE PHYSICAL DISTILBERT INT8 BENCHMARK")
        Log.i(TAG, "==================================================")
        Log.i(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.PRODUCT})")
        Log.i(TAG, "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        Log.i(TAG, "Hardware / Board: ${android.os.Build.HARDWARE} / ${android.os.Build.BOARD}")
        Log.i(TAG, "Supported ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")

        val tokenizer = BertTokenizer.fromAssets(context)
        val runner = OnnxRunner.fromAssets(context, OnnxModelVariant.INT8)
        val mlEngine = MlEngineImpl(context)

        // 1. Warm-up Phase
        Log.i(TAG, "--- WARM-UP PHASE ($WARMUP_RUNS runs) ---")
        val warmupSample = BENCHMARK_SAMPLES[0]
        for (i in 1..WARMUP_RUNS) {
            val encoded = tokenizer.encode(warmupSample)
            val result = runner.run(encoded.inputIds, encoded.attentionMask)
            val fullResult = mlEngine.predict(warmupSample)
            Log.i(TAG, "Warmup #$i completed: model=${result.inferenceMs}ms, score=${fullResult.mlScore}")
        }
        Log.i(TAG, "Warm-up completed.")

        // 2. Measurement Phase
        Log.i(TAG, "--- MEASUREMENT PHASE ($MEASURED_RUNS runs) ---")
        val measurements = mutableListOf<RunMeasurement>()

        for (runIdx in 0 until MEASURED_RUNS) {
            val sampleIdx = runIdx % BENCHMARK_SAMPLES.size
            val text = BENCHMARK_SAMPLES[sampleIdx]

            // Tokenization timing
            val tokenStartNs = System.nanoTime()
            val encoded = tokenizer.encode(text)
            val tokenEndNs = System.nanoTime()
            val tokenizationLatencyMs = (tokenEndNs - tokenStartNs) / 1_000_000.0

            // Exact Pure ONNX Model Inference timing (sub-millisecond precision)
            val modelStartNs = System.nanoTime()
            val outputs = runner.run(encoded.inputIds, encoded.attentionMask)
            val modelEndNs = System.nanoTime()
            val modelLatencyMs = (modelEndNs - modelStartNs) / 1_000_000.0

            // Complete ML-Engine predict flow timing (tokenization + ONNX inference + softmax + confidence)
            val fullFlowStartNs = System.nanoTime()
            val mlResult = mlEngine.predict(text)
            val fullFlowEndNs = System.nanoTime()
            val totalMlEngineLatencyMs = (fullFlowEndNs - fullFlowStartNs) / 1_000_000.0

            val measurement = RunMeasurement(
                runIndex = runIdx + 1,
                sampleIndex = sampleIdx,
                textLength = text.length,
                tokenizationLatencyMs = tokenizationLatencyMs,
                modelInferenceLatencyMs = modelLatencyMs,
                totalMlEngineLatencyMs = totalMlEngineLatencyMs,
                fraudProbability = mlResult.mlScore
            )
            measurements.add(measurement)

            Log.i(TAG, "RUN [${runIdx + 1}/$MEASURED_RUNS] sample=$sampleIdx len=${text.length} model=${String.format("%.3f", modelLatencyMs)}ms token=${String.format("%.3f", tokenizationLatencyMs)}ms total_engine=${String.format("%.3f", totalMlEngineLatencyMs)}ms score=${String.format("%.4f", mlResult.mlScore)}")
        }

        // 3. Calculate Statistics
        val modelStats = calculateStats(measurements.map { it.modelInferenceLatencyMs })
        val tokenStats = calculateStats(measurements.map { it.tokenizationLatencyMs })
        val totalStats = calculateStats(measurements.map { it.totalMlEngineLatencyMs })

        Log.i(TAG, "==================================================")
        Log.i(TAG, "BENCHMARK RESULTS SUMMARY (N = $MEASURED_RUNS)")
        Log.i(TAG, "==================================================")
        Log.i(TAG, "A. PURE ONNX MODEL INFERENCE LATENCY (T_model):")
        Log.i(TAG, "   Mean:       ${String.format("%.3f", modelStats.mean)} ms")
        Log.i(TAG, "   Median:     ${String.format("%.3f", modelStats.median)} ms")
        Log.i(TAG, "   Min:        ${String.format("%.3f", modelStats.min)} ms")
        Log.i(TAG, "   Max:        ${String.format("%.3f", modelStats.max)} ms")
        Log.i(TAG, "   StdDev:     ${String.format("%.3f", modelStats.stdDev)} ms")
        Log.i(TAG, "   P90:        ${String.format("%.3f", modelStats.p90)} ms")
        Log.i(TAG, "   P95:        ${String.format("%.3f", modelStats.p95)} ms")
        Log.i(TAG, "   P99:        ${String.format("%.3f", modelStats.p99)} ms")

        Log.i(TAG, "B. WORDPIECE TOKENIZATION LATENCY (T_token):")
        Log.i(TAG, "   Mean:       ${String.format("%.3f", tokenStats.mean)} ms")
        Log.i(TAG, "   Median:     ${String.format("%.3f", tokenStats.median)} ms")
        Log.i(TAG, "   Min:        ${String.format("%.3f", tokenStats.min)} ms")
        Log.i(TAG, "   Max:        ${String.format("%.3f", tokenStats.max)} ms")
        Log.i(TAG, "   StdDev:     ${String.format("%.3f", tokenStats.stdDev)} ms")

        Log.i(TAG, "C. COMPLETE ML-ENGINE PROCESSING LATENCY (T_ml_engine):")
        Log.i(TAG, "   Mean:       ${String.format("%.3f", totalStats.mean)} ms")
        Log.i(TAG, "   Median:     ${String.format("%.3f", totalStats.median)} ms")
        Log.i(TAG, "   Min:        ${String.format("%.3f", totalStats.min)} ms")
        Log.i(TAG, "   Max:        ${String.format("%.3f", totalStats.max)} ms")
        Log.i(TAG, "   StdDev:     ${String.format("%.3f", totalStats.stdDev)} ms")
        Log.i(TAG, "   P90:        ${String.format("%.3f", totalStats.p90)} ms")
        Log.i(TAG, "   P95:        ${String.format("%.3f", totalStats.p95)} ms")
        Log.i(TAG, "   P99:        ${String.format("%.3f", totalStats.p99)} ms")
        Log.i(TAG, "==================================================")

        // 4. JSON output
        val jsonOutput = JSONObject().apply {
            put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("device_product", android.os.Build.PRODUCT)
            put("android_version", android.os.Build.VERSION.RELEASE)
            put("api_level", android.os.Build.VERSION.SDK_INT)
            put("soc_hardware", android.os.Build.HARDWARE)
            put("board", android.os.Build.BOARD)
            put("abi", "arm64-v8a")
            put("model_variant", "INT8")
            put("model_asset", OnnxModelVariant.INT8.assetFileName)
            put("warmup_runs", WARMUP_RUNS)
            put("measured_runs", MEASURED_RUNS)
            put("model_inference_latency_ms", JSONObject().apply {
                put("mean", modelStats.mean)
                put("median", modelStats.median)
                put("min", modelStats.min)
                put("max", modelStats.max)
                put("std_dev", modelStats.stdDev)
                put("p90", modelStats.p90)
                put("p95", modelStats.p95)
                put("p99", modelStats.p99)
            })
            put("tokenization_latency_ms", JSONObject().apply {
                put("mean", tokenStats.mean)
                put("median", tokenStats.median)
                put("min", tokenStats.min)
                put("max", tokenStats.max)
                put("std_dev", tokenStats.stdDev)
            })
            put("total_ml_engine_latency_ms", JSONObject().apply {
                put("mean", totalStats.mean)
                put("median", totalStats.median)
                put("min", totalStats.min)
                put("max", totalStats.max)
                put("std_dev", totalStats.stdDev)
                put("p90", totalStats.p90)
                put("p95", totalStats.p95)
                put("p99", totalStats.p99)
            })
            val rawArray = JSONArray()
            measurements.forEach { m ->
                rawArray.put(JSONObject().apply {
                    put("run", m.runIndex)
                    put("sample", m.sampleIndex)
                    put("text_length", m.textLength)
                    put("tokenization_ms", m.tokenizationLatencyMs)
                    put("model_inference_ms", m.modelInferenceLatencyMs)
                    put("total_ml_engine_ms", m.totalMlEngineLatencyMs)
                    put("fraud_probability", m.fraudProbability)
                })
            }
            put("raw_measurements", rawArray)
        }

        val jsonString = jsonOutput.toString(2)
        val benchmarkFile = File(context.filesDir, "physical_latency_benchmark_result.json")
        benchmarkFile.writeText(jsonString)
        Log.i(TAG, "RESULTS_JSON_OUTPUT_BEGIN>>>${jsonOutput.toString()}<<<RESULTS_JSON_OUTPUT_END")

        runner.close()
        jsonString
    }
}
