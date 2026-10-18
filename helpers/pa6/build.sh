#!/usr/bin/env bash
set -euo pipefail
mkdir -p feeds build/pa6-data build/pa6-node build/pa6-assets/pa_regions build/pa6-java build/pa6-res/drawable-nodpi build/pa6-classes build/pa6-dex output baseline
SDK="$ANDROID_HOME"
BT="$SDK/build-tools/35.0.0"
python3 -m py_compile helpers/pa6/*.py
python3 helpers/pa2/fetch_data.py feeds
TZ=Europe/Warsaw python3 helpers/pa2/build_data.py feeds build/pa6-data | tee output/data-build.txt
TZ=Europe/Warsaw node helpers/pa2/test.cjs build/pa6-data | tee output/route-tests.txt
TZ=Europe/Warsaw node helpers/pa2/test-transport.cjs build/pa6-data | tee output/transport-tests.txt
npm install --no-audit --no-fund --prefix build/pa6-node @babel/core@7 @babel/preset-env@7 core-js@3 esbuild@0.25.10
NODE_PATH="$PWD/build/pa6-node/node_modules" node helpers/pa6/transpile.cjs helpers/pa2 build/pa6-assets/pa_regions
python3 helpers/pa6/prepare_tablet.py build/pa6-data build/pa6-assets/pa_regions
python3 helpers/pa6/compat_patch.py build/pa6-java
# NewPipeExtractor runs inside Alfa Tube on the tablet (GPLv3), not via a public API.
mkdir -p build/pa6-newpipe
mvn -B -q -f helpers/pa6/newpipe-pom.xml dependency:copy-dependencies \
 -DincludeScope=runtime -DoutputDirectory="$PWD/build/pa6-newpipe"
test "$(find build/pa6-newpipe -iname '*NewPipeExtractor*.jar' | wc -l)" -ge 1
NP_JARS="$(find build/pa6-newpipe -name '*.jar' | sort | paste -sd: -)"
# NewPipe Extractor v0.26.5 uses java.util.Optional and java.nio.file APIs
# unavailable on Android API23. D8 rewrites these APIs; L8 ships runtime j$.
DESUGAR_VERSION=2.1.5
DESUGAR_BASE="https://dl.google.com/dl/android/maven2/com/android/tools"
mkdir -p build/pa6-desugar-dex
for artifact in desugar_jdk_libs_nio desugar_jdk_libs_configuration_nio; do
 curl -fL --retry 3 --connect-timeout 20 --max-time 180 \
   "$DESUGAR_BASE/$artifact/$DESUGAR_VERSION/$artifact-$DESUGAR_VERSION.jar" \
   -o "build/$artifact.jar"
done
unzip -p build/desugar_jdk_libs_configuration_nio.jar META-INF/desugar/d8/desugar.json > build/pa6-desugar.json
python3 -m json.tool build/pa6-desugar.json >/dev/null
test -s build/pa6-desugar.json

cp helpers/pa6/assets/pa6-ca-bundle.pem build/pa6-assets/pa6-ca-bundle.pem
cp helpers/pa6/assets/pa6-ca-license.txt build/pa6-assets/pa6-ca-license.txt
# Bundle a real embedded VLC decoder into the APK. No external VLC application needed.
# LibVLC 3.5.1 is LGPL-2.1. Native libraries are shipped unchanged.
mkdir -p build/pa6-libvlc build/pa6-package
curl -fL --retry 4 --connect-timeout 20 --max-time 180 \
 https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.5.1/libvlc-all-3.5.1.aar \
 -o build/pa6-libvlc/libvlc.aar
unzip -oq build/pa6-libvlc/libvlc.aar classes.jar 'jni/*' -d build/pa6-libvlc
test -s build/pa6-libvlc/classes.jar
for abi in armeabi-v7a x86; do
 if [ -d "build/pa6-libvlc/jni/$abi" ]; then
  mkdir -p "build/pa6-package/lib/$abi"
  cp "build/pa6-libvlc/jni/$abi/"*.so "build/pa6-package/lib/$abi/"
 fi
done
test -s build/pa6-package/lib/armeabi-v7a/libvlc.so || ls -R build/pa6-package/lib/armeabi-v7a
echo 'LibVLC: LGPL 2.1, https://code.videolan.org/videolan/vlc-android' > build/pa6-assets/LIBVLC-LICENSE.txt
for f in transit-core conversation osm-map live-state app; do
 node --check "build/pa6-assets/pa_regions/$f.js"
done
test -s build/pa6-assets/pa_regions/route_all_0.json
# Use the already verified original PA launcher cat from the last tested
# alpha10 APK. Android 10 binary code is NOT reused in this Android 6 build.
gh release download pa2-rail-alpha10-isolated-e44250d0 -p Pomocnik-Alfa-alpha10-POLREGIO-Intercity-OSOBNY-TEST.apk -D baseline
cp baseline/Pomocnik-Alfa-alpha10-POLREGIO-Intercity-OSOBNY-TEST.apk baseline.apk
test -s baseline.apk
python3 helpers/pa6/extract_icon.py baseline.apk build/pa6-res/drawable-nodpi/ic_cat.png
javac -source 8 -target 8 -encoding UTF-8 -cp "$SDK/platforms/android-35/android.jar:build/pa6-libvlc/classes.jar:$NP_JARS" -d build/pa6-classes build/pa6-java/*.java
find build/pa6-classes -name '*.class' -print0 | xargs -0 "$BT/d8" --min-api 23 \
 --desugared-lib build/pa6-desugar.json \
 --lib "$SDK/platforms/android-35/android.jar" --output build/pa6-dex \
 build/pa6-libvlc/classes.jar $(find build/pa6-newpipe -name '*.jar' | sort)
java -cp "$BT/lib/d8.jar" com.android.tools.r8.L8 \
 --min-api 23 --lib "$SDK/platforms/android-35/android.jar" \
 --desugared-lib build/pa6-desugar.json --output build/pa6-desugar-dex \
 build/desugar_jdk_libs_nio.jar
# Append the L8 runtime as an additional dex file. Android 6 natively supports multidex.
python3 - <<'PY'
from pathlib import Path
existing=list(Path("build/pa6-dex").glob("classes*.dex"))
runtime=Path("build/pa6-desugar-dex/classes.dex")
assert runtime.stat().st_size>10000,(runtime,existing)
numbers=[]
for entry in existing:
 stem=entry.stem
 numbers.append(1 if stem=="classes" else int(stem.replace("classes","")))
nextdex=max(numbers)+1
target=Path("build/pa6-dex")/f"classes{nextdex}.dex"
target.write_bytes(runtime.read_bytes())
print("PASS L8 desugar runtime packaged into",target)
PY
"$BT/aapt" package -f -M helpers/pa6/AndroidManifest.xml -S build/pa6-res -A build/pa6-assets -I "$SDK/platforms/android-35/android.jar" -F output/raw.apk
(cd build/pa6-dex && zip -q -0 ../../output/raw.apk classes*.dex)
(cd build/pa6-package && zip -q -r -9 ../../output/raw.apk lib)
"$BT/zipalign" -p -f 4 output/raw.apk output/aligned.apk
"$BT/aapt" dump badging output/aligned.apk | tee output/badging.txt
grep -q "sdkVersion:'23'" output/badging.txt
grep -q "package: name='com.senikolo.pa6tubeplus.test221'" output/badging.txt
umask 077
# Isolated test package: the reference release used an unrecoverable random key.
# Never imply that this test signature can update an existing installation.
KEYSTORE="$RUNNER_TEMP/pa6-signing.p12"
trap 'rm -f "$KEYSTORE"' EXIT
if [ -n "${PA_STABLE_KEYSTORE_B64:-}" ] && [ -n "${PA_STABLE_KEYSTORE_PASS:-}" ]; then
 printf '%s' "$PA_STABLE_KEYSTORE_B64" | base64 --decode > "$KEYSTORE"
 keytool -list -v -keystore "$KEYSTORE" -storepass:env PA_STABLE_KEYSTORE_PASS > "$RUNNER_TEMP/pa6-key-info.txt"
 grep -q '36:14:42:C8:98:55:22:36:A7:D4:29:9E:ED:A5:C3:DC:27:5B:A1:F3:68:C0:11:29:2D:C0:7D:44:B2:E8:AC:E1' "$RUNNER_TEMP/pa6-key-info.txt"
else
 export PA_STABLE_KEYSTORE_PASS="$(openssl rand -hex 24)"
 keytool -genkeypair -noprompt -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 3650 -alias pa6-isolated-test -dname 'CN=PA6 Tube Plus 2.2.1 isolated TEST' -storetype PKCS12 -keystore "$KEYSTORE" -storepass:env PA_STABLE_KEYSTORE_PASS -keypass:env PA_STABLE_KEYSTORE_PASS >/dev/null 2>&1
 echo 'LIMITATION: release-specific TEST signature; no update compatibility promised.'
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:PA_STABLE_KEYSTORE_PASS --key-pass env:PA_STABLE_KEYSTORE_PASS --v1-signing-enabled true --v2-signing-enabled true --out output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk output/aligned.apk
"$BT/apksigner" verify --verbose --print-certs output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk | tee output/signature.txt
sha256sum output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk > output/SHA256SUMS.txt
