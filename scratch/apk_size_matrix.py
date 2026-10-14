import os, zipfile

apks = {
    'Original Universal Debug (Before)': r'app/build/outputs/apk/debug/app-debug.apk',
    'Original Universal Release (Before)': r'app/build/outputs/apk/release/app-release-unsigned.apk',
    'New Research Debug (Parity)': r'app/build/outputs/apk/research/debug/app-research-debug.apk',
    'New Device Debug': r'app/build/outputs/apk/device/debug/app-device-debug.apk',
    'New Device Release (Production)': r'app/build/outputs/apk/device/release/app-device-release-unsigned.apk'
}

print('=' * 92)
print(f"| {'Variant':<35} | {'APK Size':<10} | {'FP32 Model':<11} | {'INT8 Model':<11} | {'Native Libs':<11} | {'DEX':<8} |")
print('=' * 92)
for name, path in apks.items():
    if not os.path.exists(path):
        continue
    size = os.path.getsize(path) / (1024 * 1024)
    with zipfile.ZipFile(path, 'r') as z:
        files = {i.filename: (i.file_size, i.compress_size) for i in z.infolist()}
        has_fp32 = 'assets/distilbert_fraud.onnx' in files
        has_int8 = 'assets/distilbert_fraud_dynamic_int8.onnx' in files
        fp32_str = '255.54 MB' if has_fp32 else 'ABSENT'
        int8_str = '64.21 MB' if has_int8 else 'ABSENT'
        
        lib_comp = sum(files[f][1] for f in files if f.startswith('lib/')) / (1024*1024)
        dex_comp = sum(files[f][1] for f in files if f.startswith('classes') and f.endswith('.dex')) / (1024*1024)
        
        print(f"| {name:<35} | {size:>7.2f} MB | {fp32_str:<11} | {int8_str:<11} | {lib_comp:>8.2f} MB | {dex_comp:>5.2f} MB |")
print('=' * 92)
