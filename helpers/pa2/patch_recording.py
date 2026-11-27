#!/usr/bin/env python3
"""Wire the Java recording controls into the original TvActivity and manifest."""
import sys
from pathlib import Path

root = Path(sys.argv[1])
[tv] = root.glob("smali*/com/ispina/lokalnie/TvActivity.smali")
source = tv.read_text(encoding="utf-8")
owner = "Lcom/ispina/lokalnie/TvActivity;"
button = "Lcom/ispina/lokalnie/PARecordingButton;"
activity = "Landroid/app/Activity;"
set_source = f"{button}->setTvSource({activity}Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"


def replace_once(text, old, new):
    assert text.count(old) == 1, f"Unexpected recording patch anchor: {old}"
    return text.replace(old, new, 1)


initialize = f"    invoke-direct {{p0}}, {owner}->initPlayer()V"
source = replace_once(source, initialize, initialize + f"""

    iget-object p1, p0, {owner}->inlineFrame:Landroid/widget/FrameLayout;
    invoke-static {{p0, p1}}, {button}->attachTv({activity}Landroid/widget/FrameLayout;)V""")

current_url = f"    iput-object p2, p0, {owner}->currentUrl:Ljava/lang/String;"
# Match within playResolved. Other TV methods also assign currentUrl.
method_start = source.index(".method private playResolved(Ljava/lang/String;Ljava/lang/String;Z)V")
method_end = source.index("\n.end method", method_start)
method = source[method_start:method_end]
method = replace_once(method, current_url, current_url + f"""

    const/4 v1, 0x0
    const-string v2, "broadcast"
    iget-object v0, p0, {owner}->currentMode:Ljava/lang/String;
    invoke-virtual {{v2, v0}}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :pa_recording_source
    iget-object v0, p0, {owner}->currentCode:Ljava/lang/String;
    invoke-static {{v0}}, {owner}->broadcastReferer(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    :pa_recording_source
    invoke-static {{p0, p1, p2, v1}}, {set_source}""")
source = source[:method_start] + method + source[method_end:]

for signature in (
    "startDirect(Ljava/lang/String;Ljava/lang/String;)V",
    "startDirectFallback(Ljava/lang/String;[Ljava/lang/String;)V",
    "startBroadcast(Ljava/lang/String;Ljava/lang/String;Z)V",
    "startTvpGo(Ljava/lang/String;Ljava/lang/String;Z)V",
    "startTvp3Low(Z)V",
):
    start = source.index(".method private " + signature)
    locals_at = source.index("\n    .locals ", start)
    line_end = source.index("\n", locals_at + 1)
    assert int(source[locals_at:line_end].split()[-1]) >= 1, signature
    source = source[:line_end] + f"""

    const/4 v0, 0x0
    invoke-static {{p0, v0, v0, v0}}, {set_source}
""" + source[line_end:]
tv.write_text(source, encoding="utf-8")

manifest = root / "AndroidManifest.xml"
xml = manifest.read_text(encoding="utf-8")
foreground = '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>'
xml = replace_once(xml, foreground, foreground + '\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>')
application = '<application '
service = '<service android:exported="false" android:foregroundServiceType="dataSync" android:name="com.ispina.lokalnie.PARecordingService"/>'
first_component = '\n        <activity '
assert xml.count(application) == 1 and first_component in xml
xml = xml.replace(first_component, '\n        ' + service + first_component, 1)
manifest.write_text(xml, encoding="utf-8")
print("PASS: TV controls, source selection and non-exported recording service installed")
