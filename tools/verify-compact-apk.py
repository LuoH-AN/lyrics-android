"""Check the installable test APK without building a second debug package."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

build_tools = max(
    (path for path in (Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir()
     if (path / "apksigner").is_file() and (path / "aapt").is_file()),
    key=lambda path: tuple(map(int, re.findall(r"\d+", path.name))),
)
apk = Path("app/build/outputs/apk/compact/app-compact.apk")
signature = subprocess.check_output(
    [str(build_tools / "apksigner"), "verify", "--print-certs", str(apk)], text=True,
)
certificates = {value.lower() for value in re.findall(
    r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", signature,
)}
# Export only the public certificate from the existing public test keystore.
certificate = subprocess.check_output([
    "keytool", "-exportcert", "-keystore", "debug.keystore",
    "-storepass", "android", "-alias", "androiddebugkey",
])
assert certificates == {hashlib.sha256(certificate).hexdigest()}, "Unexpected test signing certificate"
badging = subprocess.check_output([str(build_tools / "aapt"), "dump", "badging", str(apk)], text=True)
assert "name='com.luoh.music.lrc.dev'" in badging.splitlines()[0], "Unexpected test package ID"
assert "application-debuggable" not in badging, "Compact package must omit debugging tools"
assert Path("app/build/outputs/mapping/compact/mapping.txt").is_file(), "Missing crash mapping"
report = json.loads(Path("app/build/reports/package-size/size.json").read_text())
print(badging.splitlines()[0])
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"APK: **{report['bytes'] / 1_000_000:.2f} MB**\n\n")
        summary.write("Download **lyrics-android-debug** from this run's artifacts.\n\n")
        summary.write(f"SHA-256: `{report['sha256']}`\n")
