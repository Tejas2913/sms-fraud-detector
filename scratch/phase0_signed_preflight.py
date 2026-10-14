import os, hashlib, zipfile

apk_path = r'app/build/outputs/apk/device/release/app-device-release.apk'
size = os.path.getsize(apk_path)

with open(apk_path, 'rb') as f:
    sha256 = hashlib.sha256(f.read()).hexdigest()

print(f"APK Path: {apk_path}")
print(f"File Size: {size} bytes ({size/(1024*1024):.2f} MB)")
print(f"SHA-256: {sha256}")

with zipfile.ZipFile(apk_path, 'r') as z:
    infolist = z.infolist()
    files = {i.filename: (i.file_size, i.compress_size) for i in infolist}
    
    int8_present = 'assets/distilbert_fraud_dynamic_int8.onnx' in files
    fp32_present = 'assets/distilbert_fraud.onnx' in files
    int8_mb = files['assets/distilbert_fraud_dynamic_int8.onnx'][0]/(1024*1024) if int8_present else 0
    print(f"assets/distilbert_fraud_dynamic_int8.onnx: {'PRESENT (' + f'{int8_mb:.2f} MB)' if int8_present else 'ABSENT'}")
    print(f"assets/distilbert_fraud.onnx: {'PRESENT' if fp32_present else 'ABSENT (Confirmed)'}")
    
    abis = set()
    for fn in files:
        if fn.startswith('lib/'):
            abis.add(fn.split('/')[1])
    print(f"Native Lib ABIs: {sorted(list(abis))}")
    for abi in ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']:
        print(f"  lib/{abi}/: {'PRESENT' if abi in abis else 'ABSENT (Confirmed)'}")
        
    dex_files = [f for f in files if f.startswith('classes') and f.endswith('.dex')]
    dex_uncomp = sum(files[f][0] for f in dex_files) / (1024*1024)
    dex_comp = sum(files[f][1] for f in dex_files) / (1024*1024)
    print(f"DEX count: {len(dex_files)}")
    print(f"DEX uncompressed: {dex_uncomp:.2f} MB, compressed: {dex_comp:.2f} MB")
