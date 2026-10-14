import os
import hashlib
import json
import collections

ROOT = r"c:\Users\hp\Desktop\SMS_Fraud"

def get_dir_size(path):
    total_size = 0
    total_files = 0
    total_dirs = 0
    for root, dirs, files in os.walk(path):
        total_dirs += len(dirs)
        for f in files:
            fp = os.path.join(root, f)
            try:
                if not os.path.islink(fp):
                    total_size += os.path.getsize(fp)
                    total_files += 1
            except Exception:
                pass
    return total_size, total_files, total_dirs

def format_size(bytes_val):
    if bytes_val >= 1024 * 1024 * 1024:
        return f"{bytes_val / (1024 * 1024 * 1024):.2f} GB"
    elif bytes_val >= 1024 * 1024:
        return f"{bytes_val / (1024 * 1024):.2f} MB"
    elif bytes_val >= 1024:
        return f"{bytes_val / 1024:.2f} KB"
    else:
        return f"{bytes_val} B"

print("Starting scan of:", ROOT)

# 1. Total scan
total_bytes = 0
all_files = []
all_dirs = []
dir_sizes = collections.defaultdict(int)
dir_file_counts = collections.defaultdict(int)

for root, dirs, files in os.walk(ROOT):
    for d in dirs:
        all_dirs.append(os.path.join(root, d))
    for f in files:
        fp = os.path.join(root, f)
        try:
            sz = os.path.getsize(fp)
            total_bytes += sz
            all_files.append((fp, sz))
            
            # accumulate to parent dirs
            curr = root
            while True:
                dir_sizes[curr] += sz
                dir_file_counts[curr] += 1
                if curr == ROOT or curr == os.path.dirname(curr):
                    break
                curr = os.path.dirname(curr)
        except Exception:
            pass

print(f"Total size: {format_size(total_bytes)} ({total_bytes:,} bytes)")
print(f"Total files: {len(all_files):,}")
print(f"Total directories: {len(all_dirs):,}")

# Top-level breakdown
print("\n--- TOP-LEVEL BREAKDOWN ---")
top_entries = []
for item in os.listdir(ROOT):
    p = os.path.join(ROOT, item)
    if os.path.isdir(p):
        sz = dir_sizes.get(p, 0)
        fc = dir_file_counts.get(p, 0)
        top_entries.append((item, sz, fc, "Directory"))
    else:
        try:
            sz = os.path.getsize(p)
            top_entries.append((item, sz, 1, "File"))
        except Exception:
            pass

top_entries.sort(key=lambda x: x[1], reverse=True)
for item, sz, fc, typ in top_entries:
    pct = (sz / total_bytes * 100) if total_bytes > 0 else 0
    print(f"{item:<30} {format_size(sz):>12} {pct:>6.2f}% ({fc} files, {typ})")

# Top 20 largest directories
print("\n--- TOP 20 LARGEST DIRECTORIES ---")
sorted_dirs = sorted(dir_sizes.items(), key=lambda x: x[1], reverse=True)
count = 0
for d, sz in sorted_dirs:
    if d == ROOT:
        continue
    rel = os.path.relpath(d, ROOT)
    pct = (sz / total_bytes * 100) if total_bytes > 0 else 0
    print(f"{rel:<60} {format_size(sz):>12} {pct:>6.2f}% ({dir_file_counts[d]} files)")
    count += 1
    if count >= 25:
        break

# Top 30 largest files
print("\n--- TOP 30 LARGEST FILES ---")
sorted_files = sorted(all_files, key=lambda x: x[1], reverse=True)
for i, (fp, sz) in enumerate(sorted_files[:35]):
    rel = os.path.relpath(fp, ROOT)
    pct = (sz / total_bytes * 100) if total_bytes > 0 else 0
    print(f"{i+1:2d}. {rel:<70} {format_size(sz):>12} {pct:>6.2f}%")

# Large file categorization
print("\n--- LARGE FILE THRESHOLDS ---")
for thresh, label in [(250*1024*1024, ">250 MB"), (100*1024*1024, "100-250 MB"), (50*1024*1024, "50-100 MB"), (10*1024*1024, "10-50 MB")]:
    print(f"\nFiles {label}:")
    for fp, sz in sorted_files:
        if thresh == 250*1024*1024 and sz >= thresh:
            print(f"  {os.path.relpath(fp, ROOT)}: {format_size(sz)}")
        elif thresh == 100*1024*1024 and 100*1024*1024 <= sz < 250*1024*1024:
            print(f"  {os.path.relpath(fp, ROOT)}: {format_size(sz)}")
        elif thresh == 50*1024*1024 and 50*1024*1024 <= sz < 100*1024*1024:
            print(f"  {os.path.relpath(fp, ROOT)}: {format_size(sz)}")
        elif thresh == 10*1024*1024 and 10*1024*1024 <= sz < 50*1024*1024:
            print(f"  {os.path.relpath(fp, ROOT)}: {format_size(sz)}")

# Duplicate detection by size and hash for files > 1 MB
print("\n--- DUPLICATE DETECTION (>1 MB) ---")
size_map = collections.defaultdict(list)
for fp, sz in all_files:
    if sz > 1024 * 1024:
        size_map[sz].append(fp)

duplicates = []
for sz, file_list in size_map.items():
    if len(file_list) > 1:
        # compute hash
        hash_map = collections.defaultdict(list)
        for fp in file_list:
            try:
                with open(fp, "rb") as f:
                    h = hashlib.sha256(f.read()).hexdigest()
                hash_map[h].append(fp)
            except Exception:
                pass
        for h, dup_files in hash_map.items():
            if len(dup_files) > 1:
                duplicates.append((sz, h, dup_files))

duplicates.sort(key=lambda x: x[0] * len(x[2]), reverse=True)
for sz, h, dup_files in duplicates:
    print(f"\nDuplicate group: Size={format_size(sz)} (SHA-256: {h[:16]}...):")
    for f in dup_files:
        print(f"  - {os.path.relpath(f, ROOT)}")
