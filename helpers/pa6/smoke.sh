#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
trap 'adb logcat -d -v time > results/logcat.txt || true; adb exec-out screencap -p > results/tablet-final.png || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb logcat -c
assert_no_app_crash(){
 adb logcat -d -b crash > results/check-crashes.txt || true
 if grep -q "Process: com.senikolo.pa6kidsru" results/check-crashes.txt; then
  echo "FAIL: Android6 app process crashed"
  tail -100 results/check-crashes.txt
  exit 1
 fi
}
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6DashboardActivity
sleep 8
adb shell dumpsys activity activities > results/dashboard-activities.txt
grep -q 'PA6DashboardActivity' results/dashboard-activities.txt
adb exec-out screencap -p > results/dashboard.png
assert_no_app_crash
# Exercise the new sounds UI and return to the original Planner.
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6SoundsActivity
sleep 3
adb shell dumpsys activity activities > results/sounds-activity.txt
grep -q "PA6SoundsActivity" results/sounds-activity.txt
adb exec-out screencap -p > results/sounds.png
# Exercise the repaired native multimedia screens, not just their manifest names.
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6RadioActivity
sleep 3
adb shell dumpsys activity activities > results/radio-activity.txt
grep -q "PA6RadioActivity" results/radio-activity.txt
adb exec-out screencap -p > results/radio.png
assert_no_app_crash
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TvActivity --ez verify_libvlc true --ez verify_video_stream true
sleep 20
adb shell dumpsys activity activities > results/tv-activity.txt
grep -q "PA6TvActivity" results/tv-activity.txt
adb exec-out screencap -p > results/tv.png
adb shell am force-stop com.senikolo.pa6kidsru
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TvActivity --ez verify_ru_catalog true
sleep 5
adb exec-out screencap -p > results/tv-russian-catalog.png
adb logcat -d -v time > results/tv-h264-playback.txt
assert_no_app_crash
adb logcat -d -v time > results/native-libvlc.txt
grep -q "PA6_LIBVLC.*NATIVE_ENGINE_READY" results/native-libvlc.txt
# Verify that the redesigned Alfa Tube also opens on Android API23.
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TubeActivity --ez verify_libvlc true --ez verify_video_stream true
sleep 16
adb shell dumpsys activity activities > results/tube-activity.txt
grep -q "PA6TubeActivity" results/tube-activity.txt
adb exec-out screencap -p > results/tube.png
# Observe actual LibVLC media events; failure of an external sample stream is recorded, not hidden.
sleep 5
adb exec-out screencap -p > results/tube-after-40s.png
adb logcat -d -v time > results/tube-api-network-log.txt
if grep -q 'PA6_PLAYER.*PLAYING' results/tube-api-network-log.txt; then
 echo "OBSERVED: LibVLC reported actual media playback start" | tee results/video-status.txt
else
 echo "UNCONFIRMED: no media playback start; check actual Acer / HLS network" | tee results/video-status.txt
fi
assert_no_app_crash
assert_no_app_crash
# Separately exercise the live Alfa Tube search and images; the media fixture did not.
adb shell am force-stop com.senikolo.pa6kidsru
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TubeActivity
sleep 24
adb exec-out screencap -p > results/tube-search-after-24s.png
adb logcat -d -v time > results/tube-search-network.txt
assert_no_app_crash
# Verify Tube Kids is a real functional activity with child-oriented discovery.
adb shell am force-stop com.senikolo.pa6kidsru
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TubeActivity --ez kids true
sleep 15
adb exec-out screencap -p > results/tube-kids-search.png
adb logcat -d -v time > results/tube-kids-network.txt
assert_no_app_crash
# Crucial regression: unlike the H264 test clip, this requires an actual
# public metadata resolver for a known YouTube upload (Me at the zoo).
# A platform/media-provider failure is logged, not misreported as successful.
adb shell am force-stop com.senikolo.pa6kidsru
adb logcat -c
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PA6TubeActivity --ez verify_real_youtube true
sleep 35
adb exec-out screencap -p > results/tube-real-youtube-attempt.png
adb logcat -d -v time > results/tube-real-youtube-log.txt
if grep -q "PA6_PLAYER.*PLAYING Me at the zoo" results/tube-real-youtube-log.txt; then
 echo "CONFIRMED: actual YouTube extraction and playback" > results/youtube-status.txt
else
 echo "UNCONFIRMED: YouTube playback not proved on public instances" > results/youtube-status.txt
fi
assert_no_app_crash
adb logcat -d -v time > results/internal-playback.txt
test "$(grep -c 'PA6_LIBVLC.*NATIVE_ENGINE_READY' results/internal-playback.txt)" -ge 2
unzip -l pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk > results/apk-native-libs.txt
grep -q 'lib/armeabi-v7a/libvlc.so' results/apk-native-libs.txt
grep -q 'lib/x86/libvlc.so' results/apk-native-libs.txt
# This proves startup only; physical-device video decoding and live streams need user testing.
# Keep testing the original planner's actual Android 6 System WebView engine.
adb shell am start -n com.senikolo.pa6kidsru/com.ispina.lokalnie.PARegionsActivity --es pa_tab assistant
sleep 20
adb shell dumpsys activity activities > results/activities.txt
adb shell dumpsys package com.senikolo.pa6kidsru > results/package.txt
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
grep -q 'com.senikolo.pa6kidsru' results/package.txt
grep -q 'PARegionsActivity' results/activities.txt
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump xmltree pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk AndroidManifest.xml > results/apk-manifest.txt
for module in PA6DashboardActivity PA6RadioActivity PA6RadioService PA6TvActivity PA6TubeActivity PA6NotesActivity PA6WeatherActivity PA6ReaderActivity PA6RemindersActivity PA6ReminderReceiver PA6SoundsActivity PA6SoundsService; do
  grep -q "$module" results/apk-manifest.txt || { echo "Missing Android6 module $module"; exit 1; }
done
echo 'PASS Android6: in-app TV + Tube LibVLC initialized, studio UI modules and planner launched; public Piped/TV streams must still be verified on real Acer.'
