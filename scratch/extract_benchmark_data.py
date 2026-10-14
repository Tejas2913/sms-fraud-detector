import subprocess
import json
import re
import math

adb_path = r"C:\Users\hp\AppData\Local\Android\Sdk\platform-tools\adb.exe"
proc = subprocess.run([adb_path, "logcat", "-d", "-s", "BENCHMARK_RESULT"], capture_output=True, text=True, encoding="utf-8", errors="ignore")

lines = proc.stdout.splitlines()

runs = []
pattern = re.compile(r"RUN \[(\d+)/100\] sample=(\d+) len=(\d+) model=([\d\.]+)ms token=([\d\.]+)ms total_engine=([\d\.]+)ms score=([\d\.]+)")

for line in lines:
    m = pattern.search(line)
    if m:
        run_idx = int(m.group(1))
        sample_idx = int(m.group(2))
        length = int(m.group(3))
        model_ms = float(m.group(4))
        token_ms = float(m.group(5))
        engine_ms = float(m.group(6))
        score = float(m.group(7))
        runs.append({
            "run": run_idx,
            "sample_index": sample_idx,
            "text_length": length,
            "tokenization_ms": token_ms,
            "model_inference_ms": model_ms,
            "total_ml_engine_ms": engine_ms,
            "fraud_probability": score
        })

print(f"Parsed {len(runs)} individual run measurements from logcat.")

def compute_stats(vals):
    s = sorted(vals)
    n = len(s)
    mean = sum(s) / n
    median = (s[n//2 - 1] + s[n//2]) / 2.0 if n % 2 == 0 else s[n//2]
    min_val = s[0]
    max_val = s[-1]
    variance = sum((x - mean) ** 2 for x in s) / n
    std_dev = math.sqrt(variance)
    return {
        "count": n,
        "mean": round(mean, 3),
        "median": round(median, 3),
        "min": round(min_val, 3),
        "max": round(max_val, 3),
        "std_dev": round(std_dev, 3),
        "p90": round(s[int(0.90 * (n - 1))], 3),
        "p95": round(s[int(0.95 * (n - 1))], 3),
        "p99": round(s[int(0.99 * (n - 1))], 3)
    }

model_vals = [r["model_inference_ms"] for r in runs]
token_vals = [r["tokenization_ms"] for r in runs]
engine_vals = [r["total_ml_engine_ms"] for r in runs]

benchmark_result = {
    "device": "Xiaomi POCO X5 Pro 5G (22101320I / redwoodin)",
    "soc": "Qualcomm Snapdragon 778G (SM7325)",
    "cpu_architecture": "arm64-v8a",
    "android_version": "14",
    "api_level": 34,
    "model_variant": "INT8",
    "model_asset": "distilbert_fraud_dynamic_int8.onnx",
    "warmup_runs": 10,
    "measured_runs": len(runs),
    "model_inference_latency_ms": compute_stats(model_vals),
    "tokenization_latency_ms": compute_stats(token_vals),
    "complete_ml_engine_processing_latency_ms": compute_stats(engine_vals),
    "raw_measurements": runs
}

with open(r"c:\Users\hp\Desktop\SMS_Fraud\scratch\latency_benchmark_results.json", "w", encoding="utf-8") as f:
    json.dump(benchmark_result, f, indent=2)

print("\n--- BENCHMARK STATISTICS SUMMARY ---")
print("Model Inference Latency (ms):", benchmark_result["model_inference_latency_ms"])
print("Tokenization Latency (ms):", benchmark_result["tokenization_latency_ms"])
print("Complete ML Engine Processing Latency (ms):", benchmark_result["complete_ml_engine_processing_latency_ms"])
