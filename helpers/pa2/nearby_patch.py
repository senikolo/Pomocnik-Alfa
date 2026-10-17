#!/usr/bin/env python3
"""Insert one safe home-card hook after the existing nearby card is generated.
Run AFTER the original PA2 patch_project.py, BEFORE recompilation. No replacement of native modules.
"""
from pathlib import Path
import sys
root = Path(sys.argv[1]); matches = list(root.glob('smali*/com/ispina/lokalnie/MainActivity.smali'))
assert len(matches) == 1, 'Expected one MainActivity.smali'
p = matches[0]; s = p.read_text(encoding='utf-8')
hook = '    invoke-static {p0}, Lcom/ispina/lokalnie/PANearbyBoards;->install(Landroid/app/Activity;)V\n'
premium_hook = '    invoke-static {p0}, Lcom/ispina/lokalnie/PADashboardPremium;->install(Landroid/app/Activity;)V\n'
if hook in s:
 if premium_hook not in s:
  s=s.replace(hook,hook+premium_hook,1)
  p.write_text(s,encoding='utf-8')
 assert s.count(premium_hook)==1
 print('PASS: nearby boards and original dashboard Premium styling hooks already present');sys.exit(0)
needle='    const-string v1, "Przystanki w pobliżu"'
assert s.count(needle) == 1, 'The nearby tile was changed; refusing to patch a different screen'
start=s.index(needle)
method='    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V'
end=s.find(method,start)
assert end!=-1 and end-start<1000, 'Nearby tile click method differs; refusing patch'
insert_at=end+len(method)
s=s[:insert_at]+'\n'+hook+premium_hook+s[insert_at:]
assert s.count(hook)==1 and s.count(premium_hook)==1
p.write_text(s,encoding='utf-8')
print('PASS: nearby boards and Premium styling install beside all existing native PA modules')
