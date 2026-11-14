"""Validate the optimized test APK and save component sizes for review."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("baseline", type=Path)
parser.add_argument("--max-mib", type=int, default=28)
parser.add_argument("--report", type=Path, required=True)
args = parser.parse_args()
with zipfile.ZipFile(args.apk) as archive:
    assert archive.testzip() is None, "Corrupt APK"
    entries = archive.infolist()
    names = {entry.filename for entry in entries}
    assert {"AndroidManifest.xml", "classes.dex"} <= names
    libraries = [entry for entry in entries if entry.filename.startswith("lib/")]
    assert libraries and all(entry.filename.startswith("lib/arm64-v8a/") for entry in libraries)
    assert "lib/arm64-v8a/libtranslate_jni.so" in names, "Offline translator was removed"
    assert "lib/arm64-v8a/liblanguage_id_l2c_jni.so" in names, "Language detection was removed"
    fonts = [entry for entry in entries if entry.filename.endswith(".ttf")]
    assert fonts and sum(entry.file_size for entry in fonts) < 2 * 1024 * 1024, "Full font library reintroduced"
    report = {
        "bytes": args.apk.stat().st_size,
        "baselineBytes": args.baseline.stat().st_size,
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "dexBytes": sum(entry.file_size for entry in entries if entry.filename.endswith(".dex")),
        "fontBytes": sum(entry.file_size for entry in fonts),
        "nativeBytes": sum(entry.file_size for entry in libraries),
        "largestEntries": [{"path": entry.filename, "packedBytes": entry.compress_size,
                            "unpackedBytes": entry.file_size}
                           for entry in sorted(entries, key=lambda entry: entry.compress_size, reverse=True)[:12]],
    }
assert report["bytes"] < args.max_mib * 1024 * 1024, "Optimized APK exceeds size budget"
assert report["bytes"] < report["baselineBytes"], "Optimized APK did not shrink"
report["reductionPercent"] = round((1 - report["bytes"] / report["baselineBytes"]) * 100, 2)
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
