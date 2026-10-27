#!/usr/bin/env python3
"""Add the shared savings and German-learning entry points to the rebuilt PA APK."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
main_files = list(root.glob("smali*/com/ispina/lokalnie/MainActivity.smali"))
assert len(main_files) == 1, f"Expected one MainActivity.smali, found {len(main_files)}"
main = main_files[0]
text = main.read_text(encoding="utf-8")

# Smart Saver duplicates the product search in DealsActivity. Subscriptions
# already has a dedicated home-screen entry.
smart_saver = re.compile(
    r'    new-instance v0, Lcom/ispina/lokalnie/MainActivity\$\$ExternalSyntheticLambda18;\n\n'
    r'    invoke-direct \{v0, p0\}, Lcom/ispina/lokalnie/MainActivity\$\$ExternalSyntheticLambda18;-><init>\(Lcom/ispina/lokalnie/MainActivity;\)V\n\n'
    r'    const-string v1, "Smart Saver"\n\n'
    r'    const-string v2, "[^"]*"\n\n'
    r'    invoke-direct \{p0, v1, v2, v0\}, Lcom/ispina/lokalnie/MainActivity;->addActionCard\(Ljava/lang/String;Ljava/lang/String;Landroid/view/View\$OnClickListener;\)V\n\n'
)
text, removed = smart_saver.subn("", text, count=1)
assert removed == 1, "Smart Saver card did not match the expected home-screen layout"
assert '    const-string v1, "Smart Saver"' not in text, "A duplicate Smart Saver home entry remains"

# Ding.pl can return recognized supermarket deals beyond the original nine.
# Register these retailers in the existing source parser so the new filter and
# the existing DealsActivity both use the same canonical store list.
parser_files = list(root.glob("smali*/com/ispina/lokalnie/deals/DealsParser.smali"))
assert len(parser_files) == 1, f"Expected one DealsParser.smali, found {len(parser_files)}"
parser = parser_files[0]
parser_text = parser.read_text(encoding="utf-8")
clinit = '.method static constructor <clinit>()V\n    .locals 10'
assert parser_text.count(clinit) == 1, "Unexpected DealsParser store-list initializer"
parser_text = parser_text.replace(clinit, clinit.replace(".locals 10", ".locals 12"), 1)
store_anchor = '    const-string v9, "Allegro"\n\n    const-string v0, "Biedronka"'
assert parser_text.count(store_anchor) == 1, "Unexpected DealsParser store-list order"
parser_text = parser_text.replace(store_anchor,
    '    const-string v9, "Allegro"\n\n'
    '    const-string v10, "POLOmarket"\n\n'
    '    const-string v11, "SPAR"\n\n'
    '    const-string v0, "Biedronka"', 1)
array_anchor = 'filled-new-array/range {v0 .. v9}, [Ljava/lang/String;'
assert parser_text.count(array_anchor) == 1, "Unexpected DealsParser retailer array"
parser_text = parser_text.replace(array_anchor,
    'filled-new-array/range {v0 .. v11}, [Ljava/lang/String;', 1)
parser.write_text(parser_text, encoding="utf-8")

anchor = '    const-string v0, "NARZ\\u0118DZIA"\n\n    invoke-direct {p0, v0}, Lcom/ispina/lokalnie/MainActivity;->addSectionLabel(Ljava/lang/String;)V'
assert text.count(anchor) == 1, "Unexpected home-screen section layout"
cards = '''    new-instance v0, Lcom/ispina/lokalnie/EconomyClick;

    invoke-direct {v0, p0}, Lcom/ispina/lokalnie/EconomyClick;-><init>(Landroid/content/Context;)V

    const-string v1, "Plan posi\\u0142k\\u00f3w"

    const-string v2, "Bud\\u017cet na dzie\\u0144, lista zakup\\u00f3w i oferty w wybranych sklepach."

    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V

    new-instance v0, Lcom/ispina/lokalnie/GermanNewsClick;

    invoke-direct {v0, p0}, Lcom/ispina/lokalnie/GermanNewsClick;-><init>(Landroid/content/Context;)V

    const-string v1, "Trenuj sw\\u00f3j niemiecki"

    const-string v2, "Codzienne wiadomo\\u015bci gospodarcze, s\\u0142ownictwo i kr\\u00f3tkie \\u0107wiczenie B1/B2."

    invoke-direct {p0, v1, v2, v0}, Lcom/ispina/lokalnie/MainActivity;->addActionCard(Ljava/lang/String;Ljava/lang/String;Landroid/view/View$OnClickListener;)V

'''
text = text.replace(anchor, cards + anchor, 1)
main.write_text(text, encoding="utf-8")

manifest = root / "AndroidManifest.xml"
xml = manifest.read_text(encoding="utf-8")
activities = [
    '<activity android:exported="false" android:name="com.ispina.lokalnie.EconomyActivity" android:windowSoftInputMode="adjustResize"/>',
    '<activity android:exported="false" android:name="com.ispina.lokalnie.GermanNewsActivity" android:windowSoftInputMode="adjustResize"/>',
]
anchor_activity = '<activity android:exported="false" android:name="com.ispina.lokalnie.PARegionsActivity" android:windowSoftInputMode="adjustResize"/>'
assert xml.count(anchor_activity) == 1, "Expected the PA Regions manifest anchor"
for activity in activities:
    class_name = activity.split('android:name="', 1)[1].split('"', 1)[0]
    if f'android:name="{class_name}"' not in xml:
        xml = xml.replace(anchor_activity, anchor_activity + "\n        " + activity, 1)

receiver = '''<receiver android:enabled="true" android:exported="false" android:name="com.ispina.lokalnie.GermanNewsReceiver">
            <intent-filter>
                <action android:name="com.ispina.lokalnie.action.DAILY_GERMAN"/>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>'''
if 'android:name="com.ispina.lokalnie.GermanNewsReceiver"' not in xml:
    anchor_app = "    </application>"
    assert xml.count(anchor_app) == 1, "Unexpected application manifest boundary"
    xml = xml.replace(anchor_app, "        " + receiver + "\n" + anchor_app, 1)
if 'android:name="android.permission.RECEIVE_BOOT_COMPLETED"' not in xml:
    permission_anchor = '    <uses-permission android:name="android.permission.INTERNET"/>'
    assert xml.count(permission_anchor) == 1, "Unexpected permission section"
    xml = xml.replace(permission_anchor, permission_anchor +
        '\n    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>', 1)
if 'android:name="android.permission.POST_NOTIFICATIONS"' not in xml:
    permission_anchor = '    <uses-permission android:name="android.permission.INTERNET"/>'
    xml = xml.replace(permission_anchor, permission_anchor +
        '\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>', 1)
manifest.write_text(xml, encoding="utf-8")

print("PASS: duplicate Smart Saver card removed; meal-planner and optional German module registered; POLOmarket and SPAR enabled")
