package com.smsfraud.detector.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Unit test verifying ONNX Runner output node name configuration.
 *
 * Ensures that [OnnxRunner] uses OUTPUT_NAME = "logits" matching the exact output tensor
 * name exported in distilbert_fraud_dynamic_int8.onnx and distilbert_fraud.onnx.
 *
 * Prevents regressions where OUTPUT_NAME was mistakenly set to "output", causing
 * outputs["output"].get() on OrtSession.Result to return an empty Optional and throw
 * java.util.NoSuchElementException on physical devices during real-time SMS detection.
 */
class OnnxRunnerOutputTest {

    @Test
    fun `OUTPUT_NAME constant is logits`() {
        val field = OnnxRunner::class.java.getDeclaredField("OUTPUT_NAME")
            .also { it.isAccessible = true }
        val outputName = field.get(null) as String

        assertEquals(
            "ONNX model output node name MUST be 'logits' matching DistilBERT export",
            "logits",
            outputName
        )
    }

    @Test
    fun `ONNX asset files exist in expected source-set assets folders`() {
        val projectRoot = File(System.getProperty("user.dir") ?: ".")

        // INT8 model lives in the shared main source-set (packaged in ALL flavors).
        val mainAssetsDir = sequenceOf(
            File(projectRoot, "src/main/assets"),
            File(projectRoot, "app/src/main/assets")
        ).firstOrNull { it.exists() }
        assertNotNull("main/assets directory must exist", mainAssetsDir)

        val int8File = File(mainAssetsDir, OnnxModelVariant.INT8.assetFileName)
        assert(int8File.exists()) { "INT8 model asset must exist: ${int8File.absolutePath}" }

        // FP32 model lives in the research flavor source-set (packaged only in the
        // 'research' flavor). It is intentionally excluded from the 'device' APK
        // to reduce its size from ~400 MB to ~83 MB.
        val researchAssetsDir = sequenceOf(
            File(projectRoot, "src/research/assets"),
            File(projectRoot, "app/src/research/assets")
        ).firstOrNull { it.exists() }
        assertNotNull("research/assets directory must exist for parity builds", researchAssetsDir)

        val fp32File = File(researchAssetsDir, OnnxModelVariant.FP32.assetFileName)
        assert(fp32File.exists()) {
            "FP32 model must exist in research flavor source-set: ${fp32File.absolutePath}\n" +
            "This model is intentionally excluded from the 'device' production APK.\n" +
            "For parity testing, build with the 'research' flavor."
        }
    }
}
