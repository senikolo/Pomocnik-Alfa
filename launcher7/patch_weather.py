#!/usr/bin/env python3
import os, sys, xml.etree.ElementTree as ET

root_dir = sys.argv[1]
manifest = os.path.join(root_dir, "AndroidManifest.xml")
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)
A = lambda name: "{%s}%s" % (ANDROID, name)

tree = ET.parse(manifest)
root = tree.getroot()
old_pkg = root.get("package") or "pl.alfalauncher.seven"
new_pkg = "pl.alfalauncher.seven.weatherfix"

app = root.find("application")
if app is None:
    raise SystemExit("No <application> in manifest")

# Components declared with relative names must keep pointing to the original DEX classes
# after changing only the install package id.
for elem in app.iter():
    tag = elem.tag.rsplit("}",1)[-1]
    if tag not in {"activity","activity-alias","service","receiver","provider"}:
        continue
    val = elem.get(A("name"))
    if not val:
        continue
    if val.startswith("."):
        elem.set(A("name"), old_pkg + val)
    elif "." not in val:
        elem.set(A("name"), old_pkg + "." + val)

root.set("package", new_pkg)
root.set(A("versionCode"), "162")
root.set(A("versionName"), "1.6.2-weather-fix")
app.set(A("label"), "Alfa Launcher 7 • 1.6.2")
app.set(A("networkSecurityConfig"), "@xml/network_security_config")
app.set(A("usesCleartextTraffic"), "true")

xml_dir = os.path.join(root_dir, "res", "xml")
raw_dir = os.path.join(root_dir, "res", "raw")
os.makedirs(xml_dir, exist_ok=True)
os.makedirs(raw_dir, exist_ok=True)

network = """<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/compat_roots" />
        </trust-anchors>
    </base-config>
</network-security-config>
"""
with open(os.path.join(xml_dir, "network_security_config.xml"), "w", encoding="utf-8") as f:
    f.write(network)

tree.write(manifest, encoding="utf-8", xml_declaration=True)
print("Patched package:", old_pkg, "->", new_pkg)
