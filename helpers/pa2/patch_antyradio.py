#!/usr/bin/env python3
"""Add Antyradio to the existing native Pomocnik Alfa radio list."""
from pathlib import Path
import sys

root = Path(sys.argv[1])
radio_files = list(root.glob("smali*/com/ispina/lokalnie/RadioActivity.smali"))
assert len(radio_files) == 1, f"Expected one RadioActivity.smali, found {len(radio_files)}"
radio = radio_files[0]
text = radio.read_text(encoding="utf-8")
start = text.index(".method static constructor <clinit>()V")
end = text.index(".end method", start) + len(".end method")
clinit = text[start:end]
assert 'const-string v0, "Antyradio"' not in clinit, "Antyradio is already present"
assert clinit.count("    .locals 32") == 1, "Unexpected RadioActivity station initializer"
anchor = "    filled-new-array/range {v2 .. v31}, [[Ljava/lang/String;"
assert clinit.count(anchor) == 1, "Unexpected station array initializer"
insert = '''    const-string v0, "Antyradio"

    const-string v1, "https://n-4-2.dcs.redcdn.pl/sc/o2/Eurozet/live/antyradio.livx?audio=5"

    filled-new-array {v0, v1}, [Ljava/lang/String;

    move-result-object v32

'''
clinit = clinit.replace("    .locals 32", "    .locals 33", 1)
clinit = clinit.replace(anchor, insert + "    filled-new-array/range {v2 .. v32}, [[Ljava/lang/String;", 1)
text = text[:start] + clinit + text[end:]
radio.write_text(text, encoding="utf-8")

main_files = list(root.glob("smali*/com/ispina/lokalnie/MainActivity.smali"))
assert len(main_files) == 1, f"Expected one MainActivity.smali, found {len(main_files)}"
main = main_files[0]
main_text = main.read_text(encoding="utf-8")
old = 'const-string v2, "Stacje polskie, niemieckie i rosyjskie, z flagami. Graj\\u0105 r\\u00f3wnie\\u017c w tle."'
new = 'const-string v2, "Polskie stacje, w tym Antyradio, niemieckie i rosyjskie. Odtwarzanie w tle."'
assert main_text.count(old) == 1, "Unexpected main menu radio description"
main.write_text(main_text.replace(old, new, 1), encoding="utf-8")
print(f"Added Antyradio to {radio} and updated {main}")
