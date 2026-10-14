import json
import statistics
import numpy as np

with open('scratch/live_sms_records.json', 'r') as f:
    records = json.load(f)

latencies = [r['latency_ms'] for r in records]
n = len(latencies)

mean_val = statistics.mean(latencies)
median_val = statistics.median(latencies)
min_val = min(latencies)
max_val = max(latencies)
stdev_val = statistics.stdev(latencies)  # N-1 sample standard deviation

# Numpy percentiles (linear interpolation, standard default method)
p90 = np.percentile(latencies, 90)
p95 = np.percentile(latencies, 95)
p99 = np.percentile(latencies, 99)

print(f"=== FULL LIVE SMS PROCESSING LATENCY BENCHMARK (N={n}) ===")
print(f"N: {n}")
print(f"Mean: {mean_val:.2f} ms")
print(f"Median: {median_val:.2f} ms")
print(f"Min: {min_val:.2f} ms (Run {records[latencies.index(min_val)]['run']})")
print(f"Max: {max_val:.2f} ms (Run {records[latencies.index(max_val)]['run']})")
print(f"Sample Stdev (N-1): {stdev_val:.2f} ms")
print(f"P90: {p90:.2f} ms")
print(f"P95: {p95:.2f} ms")
print(f"P99: {p99:.2f} ms")

print("\n=== BREAKDOWN BY DECISION ===")
by_dec = {}
for r in records:
    by_dec.setdefault(r['decision'], []).append(r['latency_ms'])
for dec, lats in by_dec.items():
    print(f"{dec} (count={len(lats)}): mean={statistics.mean(lats):.2f} ms, median={statistics.median(lats):.2f} ms")

print("\n=== BREAKDOWN BY URL PRESENCE ===")
by_url = {}
for r in records:
    by_url.setdefault(r['url_present'], []).append(r['latency_ms'])
for url_p, lats in by_url.items():
    print(f"URL={url_p} (count={len(lats)}): mean={statistics.mean(lats):.2f} ms, median={statistics.median(lats):.2f} ms")

# Warm state (Runs 2 to 18)
warm = latencies[1:]
print("\n=== WARM-STATE ONLY (Runs 2-18, N=17, excluding initial cold-start Run 1) ===")
print(f"Warm N: {len(warm)}")
print(f"Warm Mean: {statistics.mean(warm):.2f} ms")
print(f"Warm Median: {statistics.median(warm):.2f} ms")
print(f"Warm Min: {min(warm):.2f} ms")
print(f"Warm Max: {max(warm):.2f} ms")
print(f"Warm Sample Stdev (N-1): {statistics.stdev(warm):.2f} ms")
print(f"Warm P90: {np.percentile(warm, 90):.2f} ms")
print(f"Warm P95: {np.percentile(warm, 95):.2f} ms")
print(f"Warm P99: {np.percentile(warm, 99):.2f} ms")
