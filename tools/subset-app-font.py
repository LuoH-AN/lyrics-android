"""Create the bundled alphabet subset; Android supplies CJK glyphs via font fallback.

Run manually with fontTools 4.67.0. The resulting TTF is committed, so Android
builds do not fetch fonts or run Python. Input is the original Mi Outfit font.
"""
import argparse
import hashlib
import json
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("source", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
font = TTFont(args.source, recalcTimestamp=False)
original_cmap = font.getBestCmap()


def system_glyph(code):
    return any(start <= code <= end for start, end in (
        (0x2E80, 0x2FFF), (0x3040, 0x31FF), (0x3400, 0x9FFF),
        (0xF900, 0xFAFF), (0x20000, 0x323AF),
    ))


options = subset.Options()
options.name_IDs = ["*"]
options.name_legacy = True
options.name_languages = ["*"]
options.recalc_timestamp = False
subsetter = subset.Subsetter(options=options)
subsetter.populate(unicodes=[code for code in original_cmap if not system_glyph(code)])
subsetter.subset(font)
# Give the derivative a new family name; retain author and license records.
names = {1: "Lyrics Outfit", 3: "1.000;LyricsOutfit-Variable",
         4: "Lyrics Outfit Regular", 6: "LyricsOutfit-Variable",
         16: "Lyrics Outfit", 21: "Lyrics Outfit"}
for record in font["name"].names:
    if record.nameID in names:
        record.string = names[record.nameID].encode(record.getEncoding())
font.recalcTimestamp = False
args.output.parent.mkdir(parents=True, exist_ok=True)
font.save(args.output)
assert len(font.getBestCmap()) > 1000
assert args.output.stat().st_size < 1_000_000
print(json.dumps({"sourceSha256": hashlib.sha256(args.source.read_bytes()).hexdigest(),
    "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
    "sourceBytes": args.source.stat().st_size, "bytes": args.output.stat().st_size,
    "characters": len(font.getBestCmap()), "family": "Lyrics Outfit",
    "license": "SIL Open Font License 1.1",
    "axes": [{"tag": axis.axisTag, "min": axis.minValue, "max": axis.maxValue}
             for axis in font["fvar"].axes]}, indent=2))
