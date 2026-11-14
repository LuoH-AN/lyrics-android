"""Keep only the app's extended Material vectors, with their original paths and license.

Run from the repository root. Android builds use the committed Kotlin files and
never download the icon sources. Core icons remain in material-icons-core.
"""
import argparse
import hashlib
from pathlib import Path
import re
from urllib.request import urlopen
import zipfile

VERSION = "1.7.8"
PREFIX = "commonMain/androidx/compose/material/icons/"
PACKAGE = "com.luoh.music.lrc.ui.icons"
DESTINATION = Path("app/src/main/kotlin/com/luoh/music/lrc/ui/icons")
ARCHIVES = {
    "core": "14db888d2f698178799a4c18a58bb134b3e98063c6748c443b339044e0145432",
    "extended": "77b7e782a519a3e074fc91e09d59334ac1b815edd87d2e89c0c9ca7c95678e93",
}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true", help="Verify the committed files without changing them")
args = parser.parse_args()
cache = Path("build/ci-study")
cache.mkdir(parents=True, exist_ok=True)
archives = {}
for artifact, expected in ARCHIVES.items():
    name = f"material-icons-{artifact}"
    path = cache / f"{name}-sources.jar"
    if not path.exists():
        url = f"https://dl.google.com/dl/android/maven2/androidx/compose/material/{name}/{VERSION}/{name}-{VERSION}-sources.jar"
        with urlopen(url, timeout=60) as response:
            path.write_bytes(response.read())
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, f"Unexpected {name} source archive"
    archives[artifact] = zipfile.ZipFile(path)

used = set()
for path in Path("app/src").rglob("*.kt"):
    if path.is_relative_to(DESTINATION):
        continue
    source = path.read_text()
    used.update(re.findall(r"import (?:androidx\.compose\.material\.icons|com\.luoh\.music\.lrc\.ui\.icons)\.((?:automirrored\.)?filled\.\w+)", source))
    used.update("filled." + icon for icon in re.findall(r"Icons\.(?:Default|Filled)\.(\w+)", source))

expected_files = set()
for icon in sorted(used):
    archive_path = PREFIX + icon.replace(".", "/") + ".kt"
    if archive_path in archives["core"].namelist():
        continue
    source = archives["extended"].read(archive_path).decode()
    source = source.replace("package androidx.compose.material.icons.", f"package {PACKAGE}.")
    destination = DESTINATION / (icon.replace(".", "/") + ".kt")
    expected_files.add(destination)
    if args.check:
        assert destination.read_text() == source, f"Modified vector: {icon}"
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(source)
assert set(DESTINATION.rglob("*.kt")) == expected_files, "Unused vendored vector"
for archive in archives.values():
    archive.close()
print(f"Verified {len(expected_files)} extended icons from AndroidX {VERSION}")
