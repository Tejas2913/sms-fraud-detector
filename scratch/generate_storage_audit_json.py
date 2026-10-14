import os
import hashlib
import json
import collections

ROOT = r"c:\Users\hp\Desktop\SMS_Fraud"

def format_size(bytes_val):
    if bytes_val >= 1024 * 1024 * 1024:
        return f"{bytes_val / (1024 * 1024 * 1024):.2f} GB"
    elif bytes_val >= 1024 * 1024:
        return f"{bytes_val / (1024 * 1024):.2f} MB"
    elif bytes_val >= 1024:
        return f"{bytes_val / 1024:.2f} KB"
    else:
        return f"{bytes_val} B"

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
            
            curr = root
            while True:
                dir_sizes[curr] += sz
                dir_file_counts[curr] += 1
                if curr == ROOT or curr == os.path.dirname(curr):
                    break
                curr = os.path.dirname(curr)
        except Exception:
            pass

# Top-level breakdown
top_entries = []
for item in os.listdir(ROOT):
    p = os.path.join(ROOT, item)
    if os.path.isdir(p):
        sz = dir_sizes.get(p, 0)
        fc = dir_file_counts.get(p, 0)
        top_entries.append({"name": item, "size_bytes": sz, "size_str": format_size(sz), "pct": (sz / total_bytes * 100) if total_bytes > 0 else 0, "file_count": fc, "type": "Directory"})
    else:
        try:
            sz = os.path.getsize(p)
            top_entries.append({"name": item, "size_bytes": sz, "size_str": format_size(sz), "pct": (sz / total_bytes * 100) if total_bytes > 0 else 0, "file_count": 1, "type": "File"})
        except Exception:
            pass

top_entries.sort(key=lambda x: x["size_bytes"], reverse=True)

# Top 25 directories
sorted_dirs = sorted([(d, sz) for d, sz in dir_sizes.items() if d != ROOT], key=lambda x: x[1], reverse=True)
top_dirs = []
for d, sz in sorted_dirs[:25]:
    top_dirs.append({
        "path": os.path.relpath(d, ROOT),
        "size_bytes": sz,
        "size_str": format_size(sz),
        "pct": (sz / total_bytes * 100) if total_bytes > 0 else 0,
        "file_count": dir_file_counts[d]
    })

# Top 35 largest files
sorted_files = sorted(all_files, key=lambda x: x[1], reverse=True)
top_files = []
for fp, sz in sorted_files[:35]:
    top_files.append({
        "path": os.path.relpath(fp, ROOT),
        "size_bytes": sz,
        "size_str": format_size(sz),
        "pct": (sz / total_bytes * 100) if total_bytes > 0 else 0
    })

# Thresholds
thresholds = {
    ">250MB": [os.path.relpath(fp, ROOT) for fp, sz in sorted_files if sz >= 250*1024*1024],
    "100-250MB": [os.path.relpath(fp, ROOT) for fp, sz in sorted_files if 100*1024*1024 <= sz < 250*1024*1024],
    "50-100MB": [os.path.relpath(fp, ROOT) for fp, sz in sorted_files if 50*1024*1024 <= sz < 100*1024*1024],
    "10-50MB": [os.path.relpath(fp, ROOT) for fp, sz in sorted_files if 10*1024*1024 <= sz < 50*1024*1024],
}

# SMS Guard Specific Assets & Build breakdown
app_build_size = dir_sizes.get(os.path.join(ROOT, "app", "build"), 0)
dot_gradle_size = dir_sizes.get(os.path.join(ROOT, ".gradle"), 0)
app_src_size = dir_sizes.get(os.path.join(ROOT, "app", "src"), 0)
datasets_size = dir_sizes.get(os.path.join(ROOT, "datasets"), 0)
notebooks_size = dir_sizes.get(os.path.join(ROOT, "notebooks"), 0)
models_size = dir_sizes.get(os.path.join(ROOT, "models"), 0)
research_size = dir_sizes.get(os.path.join(ROOT, "research"), 0)

# Check Git
git_dir = os.path.join(ROOT, ".git")
git_exists = os.path.exists(git_dir)
git_size = dir_sizes.get(git_dir, 0) if git_exists else 0

result_data = {
    "root": ROOT,
    "total_bytes": total_bytes,
    "total_size_str": format_size(total_bytes),
    "total_files": len(all_files),
    "total_dirs": len(all_dirs),
    "top_level": top_entries,
    "top_dirs": top_dirs,
    "top_files": top_files,
    "thresholds": thresholds,
    "app_build_size": app_build_size,
    "app_build_size_str": format_size(app_build_size),
    "dot_gradle_size": dot_gradle_size,
    "dot_gradle_size_str": format_size(dot_gradle_size),
    "app_src_size": app_src_size,
    "app_src_size_str": format_size(app_src_size),
    "git_exists": git_exists,
    "git_size": git_size,
    "git_size_str": format_size(git_size)
}

with open(r"scratch\storage_audit_data.json", "w") as f:
    json.dump(result_data, f, indent=2)

print("Saved scratch\\storage_audit_data.json successfully.")
