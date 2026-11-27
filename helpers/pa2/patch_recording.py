#!/usr/bin/env python3
"""Wire the Java recording controls into the original TvActivity and manifest."""
import sys
from pathlib import Path

root = Path(sys.argv[1])
[tv] = root.glob("smali*/com/ispina/lokalnie/TvActivity.smali")
source = tv.read_text(encoding="utf-8")
owner = "Lcom/ispina/lokalnie/TvActivity;"
button = "Lcom/ispina/lokalnie/PARecordingControls;"
activity = "Landroid/app/Activity;"
clear_source = f"{button}->clearTvSource({activity})V"
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
# Keep the original registers alive. In particular v0 still supplies false
# to setStatus in the original exception handler when playback fails.
method = replace_once(method, "    .locals 3\n", "    .locals 6\n")
method = replace_once(method, current_url, current_url + f"""

    const/4 v3, 0x0
    const-string v4, "broadcast"
    iget-object v5, p0, {owner}->currentMode:Ljava/lang/String;
    invoke-virtual {{v4, v5}}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v4
    if-eqz v4, :pa_recording_source
    iget-object v5, p0, {owner}->currentCode:Ljava/lang/String;
    invoke-static {{v5}}, {owner}->broadcastReferer(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v3
    :pa_recording_source
    invoke-static {{p0, p1, p2, v3}}, {set_source}""")
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
    source = source[:line_end] + f"\n    invoke-static {{p0}}, {clear_source}\n" + source[line_end:]
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
