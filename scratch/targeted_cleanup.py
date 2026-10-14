import os
import hashlib
import shutil
import json

ROOT = r"c:\Users\hp\Desktop\SMS_Fraud"
ARCHIVE_APK = os.path.join(ROOT, "release_archive", "app-device-release.apk")
EXPECTED_HASH = "7F43C9332900E7004EA7F8BEC9A402BA6512BFD04784896C344D8C2A10066C5C".lower()

APP_BUILD = os.path.join(ROOT, "app", "build")
INT8_MODEL = os.path.join(ROOT, "app", "src", "main", "assets", "distilbert_fraud_dynamic_int8.onnx")
FP32_MODEL = os.path.join(ROOT, "app", "src", "research", "assets", "distilbert_fraud.onnx")

print("=== STEP 1: PRE-CLEANUP VERIFICATION ===")

if not os.path.exists(ARCHIVE_APK):
    raise FileNotFoundError(f"Archive APK not found at: {ARCHIVE_APK}")

with open(ARCHIVE_APK, "rb") as f:
    apk_hash = hashlib.sha256(f.read()).hexdigest().lower()

apk_size = os.path.getsize(ARCHIVE_APK)
print(f"Archive APK Path: {ARCHIVE_APK}")
print(f"Archive APK Size: {apk_size} bytes ({apk_size/(1024*1024):.2f} MB)")
print(f"Archive APK SHA-256: {apk_hash.upper()}")

if apk_hash != EXPECTED_HASH:
    raise ValueError(f"Hash mismatch! Expected {EXPECTED_HASH.upper()}, got {apk_hash.upper()}")
print("Pre-verification PASSED: Archive APK exists and hash matches exactly.")

# Measure app/build size
build_size = 0
build_files = 0
build_dirs = 0
for root, dirs, files in os.walk(APP_BUILD):
    build_dirs += len(dirs)
    for file in files:
        fp = os.path.join(root, file)
        try:
            build_size += os.path.getsize(fp)
            build_files += 1
        except Exception:
            pass

print(f"\nTarget directory for deletion: {APP_BUILD}")
print(f"Size of app/build before deletion: {build_size:,} bytes ({build_size/(1024*1024*1024):.2f} GB / {build_size/(1024*1024):.2f} MB)")
print(f"Files inside app/build: {build_files:,} in {build_dirs:,} folders")

# Check source models pre-deletion
if not os.path.exists(INT8_MODEL) or not os.path.exists(FP32_MODEL):
    raise FileNotFoundError("Canonical ONNX models missing before cleanup!")

with open(INT8_MODEL, "rb") as f:
    int8_pre_hash = hashlib.sha256(f.read()).hexdigest()
with open(FP32_MODEL, "rb") as f:
    fp32_pre_hash = hashlib.sha256(f.read()).hexdigest()

print(f"\nCanonical INT8 Model: {INT8_MODEL} ({os.path.getsize(INT8_MODEL)/(1024*1024):.2f} MB) SHA256={int8_pre_hash[:16]}...")
print(f"Canonical FP32 Model: {FP32_MODEL} ({os.path.getsize(FP32_MODEL)/(1024*1024):.2f} MB) SHA256={fp32_pre_hash[:16]}...")

# Perform deletion
print("\n=== STEP 2: DELETING ONLY app/build ===")
shutil.rmtree(APP_BUILD)
print(f"Deleted {APP_BUILD} successfully.")

# Post-deletion verification
print("\n=== STEP 3: POST-CLEANUP VERIFICATION ===")
build_exists = os.path.exists(APP_BUILD)
print(f"Does app/build exist? {build_exists} (Expected: False)")

archive_exists = os.path.exists(ARCHIVE_APK)
print(f"Does release_archive/app-device-release.apk exist? {archive_exists} (Expected: True)")

with open(ARCHIVE_APK, "rb") as f:
    apk_post_hash = hashlib.sha256(f.read()).hexdigest().lower()

print(f"Archive APK SHA-256 after cleanup: {apk_post_hash.upper()}")
assert apk_post_hash == EXPECTED_HASH, "Post-cleanup APK hash altered!"

# Verify source models intact
assert os.path.exists(INT8_MODEL), "INT8 model missing after cleanup!"
assert os.path.exists(FP32_MODEL), "FP32 model missing after cleanup!"

with open(INT8_MODEL, "rb") as f:
    int8_post_hash = hashlib.sha256(f.read()).hexdigest()
with open(FP32_MODEL, "rb") as f:
    fp32_post_hash = hashlib.sha256(f.read()).hexdigest()

assert int8_post_hash == int8_pre_hash, "INT8 model modified!"
assert fp32_post_hash == fp32_pre_hash, "FP32 model modified!"

print("Canonical INT8 & FP32 models verified intact and identical.")
print(f"Total space reclaimed: {build_size/(1024*1024*1024):.2f} GB ({build_size:,} bytes).")
