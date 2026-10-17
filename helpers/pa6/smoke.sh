#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
trap 'adb logcat -d -v time > results/logcat.txt || true; adb exec-out screencap -p > results/tablet-final.png || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb logcat -c
assert_no_app_crash(){
 adb logcat -d -b crash > results/check-crashes.txt || true
 if grep -q "Process: com.senikolo.pa6showcase" results/check-crashes.txt; then
  echo "FAIL: Android6 app process crashed"
  tail -100 results/check-crashes.txt
  exit 1
 fi
}
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PA6DashboardActivity
sleep 8
adb shell dumpsys activity activities > results/dashboard-activities.txt
grep -q 'PA6DashboardActivity' results/dashboard-activities.txt
adb exec-out screencap -p > results/dashboard.png
assert_no_app_crash
# Exercise the new sounds UI and return to the original Planner.
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PA6SoundsActivity
sleep 3
adb shell dumpsys activity activities > results/sounds-activity.txt
grep -q "PA6SoundsActivity" results/sounds-activity.txt
adb exec-out screencap -p > results/sounds.png
# Exercise the repaired native multimedia screens, not just their manifest names.
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PA6RadioActivity
sleep 3
adb shell dumpsys activity activities > results/radio-activity.txt
grep -q "PA6RadioActivity" results/radio-activity.txt
adb exec-out screencap -p > results/radio.png
assert_no_app_crash
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PA6TvActivity --ez verify_libvlc true
sleep 3
adb shell dumpsys activity activities > results/tv-activity.txt
grep -q "PA6TvActivity" results/tv-activity.txt
adb exec-out screencap -p > results/tv.png
assert_no_app_crash
adb logcat -d -v time > results/native-libvlc.txt
grep -q "PA6_LIBVLC.*NATIVE_ENGINE_READY" results/native-libvlc.txt
# Verify that the redesigned Alfa Tube also opens on Android API23.
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PA6TubeActivity --ez verify_libvlc true
sleep 3
adb shell dumpsys activity activities > results/tube-activity.txt
grep -q "PA6TubeActivity" results/tube-activity.txt
adb exec-out screencap -p > results/tube.png
assert_no_app_crash
adb logcat -d -v time > results/internal-playback.txt
test "$(grep -c 'PA6_LIBVLC.*NATIVE_ENGINE_READY' results/internal-playback.txt)" -ge 2
unzip -l pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk | grep -q 'lib/armeabi-v7a/libvlc.so'
unzip -l pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk | grep -q 'lib/x86/libvlc.so'
# This proves startup only; physical-device video decoding and live streams need user testing.
# Keep testing the original planner's actual Android 6 System WebView engine.
adb shell am start -n com.senikolo.pa6showcase/com.ispina.lokalnie.PARegionsActivity --es pa_tab assistant
sleep 20
adb shell dumpsys activity activities > results/activities.txt
adb shell dumpsys package com.senikolo.pa6showcase > results/package.txt
adb exec-out screencap -p > results/tablet-start.png
adb shell uiautomator dump /sdcard/window.xml || true
adb pull /sdcard/window.xml results/window.xml || true
adb logcat -d -v time > results/app-logcat.txt
# A UI that merely opens but does not execute its JavaScript is NOT a passed port.
if grep -E 'Uncaught (SyntaxError|TypeError|ReferenceError)|Refused to execute inline script' results/app-logcat.txt; then
 echo 'FAIL JavaScript runtime of actual Android 6 System WebView'
 exit 1
fi
adb logcat -d -b crash > results/crashes.txt
! grep -q 'FATAL EXCEPTION' results/crashes.txt
grep -q 'com.senikolo.pa6showcase' results/package.txt
grep -q 'PARegionsActivity' results/activities.txt
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump xmltree pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk AndroidManifest.xml > results/apk-manifest.txt
for module in PA6DashboardActivity PA6RadioActivity PA6RadioService PA6TvActivity PA6TubeActivity PA6NotesActivity PA6WeatherActivity PA6ReaderActivity PA6RemindersActivity PA6ReminderReceiver PA6SoundsActivity PA6SoundsService; do
  grep -q "$module" results/apk-manifest.txt || { echo "Missing Android6 module $module"; exit 1; }
done
echo 'PASS Android6: in-app TV + Tube LibVLC initialized, studio UI modules and planner launched; public Piped/TV streams must still be verified on real Acer.'
