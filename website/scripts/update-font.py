"""Manually refresh the self-hosted Google Fonts subset; not a build dependency."""
import hashlib
import json
from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"


class Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.text = []

    def handle_data(self, data):
        self.text.append(data)


def read(url):
    with urlopen(Request(url, headers={"User-Agent": UA}), timeout=45) as response:
        return response.read()


parser = Text()
parser.feed((ROOT / "index.html").read_text())
chars = "".join(sorted(set("".join(parser.text) + (ROOT / "app.js").read_text() + "".join(chr(i) for i in range(32, 127)))))
url = "https://fonts.googleapis.com/css2?" + urlencode({"family": "Noto Sans SC:wght@100..900", "display": "swap", "text": chars})
css = read(url).decode()
urls = list(dict.fromkeys(re.findall(r"url\(([^)]+)\)", css)))
assert len(urls) == 1 and urls[0].startswith("https://fonts.gstatic.com/"), "Unexpected font response"
font = read(urls[0])
assert font[:4] == b"wOF2", "Expected WOFF2 font"
license_url = "https://raw.githubusercontent.com/google/fonts/main/ofl/notosanssc/OFL.txt"
license_text = read(license_url)
assert b"SIL OPEN FONT LICENSE" in license_text
folder = ROOT / "fonts"
folder.mkdir(exist_ok=True)
(folder / "lyrics-sans.woff2").write_bytes(font)
(folder / "OFL.txt").write_bytes(license_text)
(folder / "glyphs.txt").write_text(chars + "\n")
(folder / "manifest.json").write_text(json.dumps({"family": "Noto Sans SC", "weight": "100 900", "source": "Google Fonts official subset", "url": urls[0], "license_url": license_url, "sha256": hashlib.sha256(font).hexdigest(), "bytes": len(font)}, ensure_ascii=False, indent=2) + "\n")
print(f"Updated self-hosted font: {len(font)} bytes, {len(chars)} characters")
