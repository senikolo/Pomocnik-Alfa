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
javac -source 8 -target 8 -encoding UTF-8 -cp "$SDK/platforms/android-35/android.jar:build/pa6-libvlc/classes.jar" -d build/pa6-classes build/pa6-java/*.java
find build/pa6-classes -name '*.class' -print0 | xargs -0 "$BT/d8" --min-api 23 --lib "$SDK/platforms/android-35/android.jar" --output build/pa6-dex build/pa6-libvlc/classes.jar
"$BT/aapt" package -f -M helpers/pa6/AndroidManifest.xml -S build/pa6-res -A build/pa6-assets -I "$SDK/platforms/android-35/android.jar" -F output/raw.apk
(cd build/pa6-dex && zip -q -0 ../../output/raw.apk classes*.dex)
(cd build/pa6-package && zip -q -r -9 ../../output/raw.apk lib)
"$BT/zipalign" -p -f 4 output/raw.apk output/aligned.apk
"$BT/aapt" dump badging output/aligned.apk | tee output/badging.txt
grep -q "sdkVersion:'23'" output/badging.txt
grep -q "package: name='com.senikolo.pa6kidsru'" output/badging.txt
umask 077
export PA6_TEST_PASS="$(openssl rand -hex 24)"
keytool -genkeypair -noprompt -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 3650 -alias pa6-tablet-test -dname 'CN=PA6 Acer test only, O=PA development' -storetype PKCS12 -keystore "$RUNNER_TEMP/pa6-test.p12" -storepass:env PA6_TEST_PASS -keypass:env PA6_TEST_PASS >/dev/null 2>&1
"$BT/apksigner" sign --ks "$RUNNER_TEMP/pa6-test.p12" --ks-type PKCS12 --ks-key-alias pa6-tablet-test --ks-pass env:PA6_TEST_PASS --key-pass env:PA6_TEST_PASS --out output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk output/aligned.apk
"$BT/apksigner" verify --verbose --print-certs output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk | tee output/signature.txt
sha256sum output/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk > output/SHA256SUMS.txt
rm -f "$RUNNER_TEMP/pa6-test.p12"
