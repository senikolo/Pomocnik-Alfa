#!/usr/bin/env python3
"""Add only TV Republika and wPolsce24 inside the Poland channel card."""
import sys
from pathlib import Path

root = Path(sys.argv[1])
matches = list(root.glob("smali*/com/ispina/lokalnie/TvActivity.smali"))
assert len(matches) == 1, f"Expected one TvActivity.smali, found {len(matches)}"
path = matches[0]
source = path.read_text(encoding="utf-8")

marker = r'    const-string v2, "\u25b6  TVP Polonia"'
assert source.count(marker) == 1, "Expected the TVP Polonia row in the Poland card"
call = "    invoke-direct/range {v0 .. v5}, Lcom/ispina/lokalnie/TvActivity;->addStation(Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;ZLandroid/view/View$OnClickListener;)V"
start = source.index(marker)
call_at = source.find(call, start)
assert call_at >= 0, "Could not find the TVP Polonia row callback"
line_end = source.find("\n", call_at)
assert line_end >= 0, "Malformed TVP Polonia row"

rows = r'''
    new-instance v5, Lcom/ispina/lokalnie/TvDirectoryClick;

    const-string v2, "TV Republika"

    const-string v3, "wiadomo\u015bci \u2022 Polska"

    const-string v4, "https://tvrepublika.pl/live"

    invoke-direct {v5, v7, v2, v4}, Lcom/ispina/lokalnie/TvDirectoryClick;-><init>(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V

    const/4 v4, 0x0

    const-string v2, "\u25b6  TV Republika"

    invoke-direct/range {v0 .. v5}, Lcom/ispina/lokalnie/TvActivity;->addStation(Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;ZLandroid/view/View$OnClickListener;)V

    new-instance v5, Lcom/ispina/lokalnie/TvDirectoryClick;

    const-string v2, "wPolsce24"

    const-string v3, "na \u017cywo \u2022 Polska"

    const-string v4, "https://wpolsce24.tv/video"

    invoke-direct {v5, v7, v2, v4}, Lcom/ispina/lokalnie/TvDirectoryClick;-><init>(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V

    const/4 v4, 0x0

    const-string v2, "\u25b6  wPolsce24"

    invoke-direct/range {v0 .. v5}, Lcom/ispina/lokalnie/TvActivity;->addStation(Landroid/widget/LinearLayout;Ljava/lang/String;Ljava/lang/String;ZLandroid/view/View$OnClickListener;)V
'''
source = source[:line_end + 1] + rows + source[line_end + 1:]

old_note = r'    const-string v0, "Kana\u0142y TVP uruchamiane s\u0105 przez aktualny oficjalny adres transmisji pobierany przy ka\u017cdym starcie."'
new_note = r'    const-string v0, "TVP korzysta z oficjalnych transmisji; Republika i wPolsce24 otwieraj\u0105 si\u0119 w oknie aplikacji."'
assert source.count(old_note) == 1, "Unexpected Poland card description"
source = source.replace(old_note, new_note)

# Keep the official pages in the existing inline TV player frame and restore
# the native PlayerView as soon as the user selects another station or leaves TV.
for signature in (
    ".method private startDirect(Ljava/lang/String;Ljava/lang/String;)V",
    ".method private startBroadcast(Ljava/lang/String;Ljava/lang/String;Z)V",
    ".method private startDirectFallback(Ljava/lang/String;[Ljava/lang/String;)V",
    ".method protected onDestroy()V",
):
    assert source.count(signature) == 1, f"Expected one method: {signature}"
    method_start = source.index(signature)
    locals_start = source.index("\n    .locals ", method_start)
    locals_end = source.index("\n", locals_start + 1) + 1
    source = source[:locals_end] + "    invoke-static {p0}, Lcom/ispina/lokalnie/TvDirectoryClick;->clearInlinePlayer(Landroid/content/Context;)V\n" + source[locals_end:]

path.write_text(source, encoding="utf-8")
print("PASS: added TV Republika and wPolsce24 in the Poland card's shared inline player")
