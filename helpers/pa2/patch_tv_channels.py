#!/usr/bin/env python3
"""Add an official Polish TV directory button to the existing TV screen."""
import sys
from pathlib import Path

root = Path(sys.argv[1])
matches = list(root.glob("smali*/com/ispina/lokalnie/TvActivity.smali"))
assert len(matches) == 1, f"Expected one TvActivity.smali, found {len(matches)}"
path = matches[0]
source = path.read_text(encoding="utf-8")
anchor = """    invoke-virtual {v11, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

    .line 141
    invoke-direct/range {p0 .. p0}, Lcom/ispina/lokalnie/TvActivity;->card()Landroid/widget/LinearLayout;"""
assert source.count(anchor) == 1, "Unexpected TV screen layout; refusing an unsafe patch"
replacement = """    invoke-virtual {v11, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

    const-string v0, "Polskie kana\\u0142y online"

    const/4 v1, 0x1

    invoke-direct {v7, v0, v1}, Lcom/ispina/lokalnie/TvActivity;->button(Ljava/lang/String;Z)Landroid/widget/Button;

    move-result-object v0

    new-instance v1, Lcom/ispina/lokalnie/TvDirectoryClick;

    invoke-direct {v1, v7}, Lcom/ispina/lokalnie/TvDirectoryClick;-><init>(Landroid/content/Context;)V

    invoke-virtual {v0, v1}, Landroid/widget/Button;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    invoke-virtual {v11, v0}, Landroid/widget/LinearLayout;->addView(Landroid/view/View;)V

    .line 141
    invoke-direct/range {p0 .. p0}, Lcom/ispina/lokalnie/TvActivity;->card()Landroid/widget/LinearLayout;"""
path.write_text(source.replace(anchor, replacement), encoding="utf-8")
print("PASS: added Polish online channels button to the TV screen")
