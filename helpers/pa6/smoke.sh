#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
trap 'adb logcat -d -v time > results/logcat.txt || true; adb exec-out screencap -p > results/tablet-final.png || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb logcat -c
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.PA6DashboardActivity
sleep 8
adb shell dumpsys activity activities > results/dashboard-activities.txt
grep -q 'PA6DashboardActivity' results/dashboard-activities.txt
adb exec-out screencap -p > results/dashboard.png
# Keep testing the original planner's actual Android 6 System WebView engine.
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.PARegionsActivity --es pa_tab assistant
sleep 20
adb shell dumpsys activity activities > results/activities.txt
adb shell dumpsys package com.senikolo.pa6tablet > results/package.txt
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
grep -q 'com.senikolo.pa6tablet' results/package.txt
grep -q 'PARegionsActivity' results/activities.txt
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump xmltree pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk AndroidManifest.xml > results/apk-manifest.txt
for module in PA6DashboardActivity PA6RadioActivity PA6RadioService PA6TvActivity PA6TubeActivity PA6NotesActivity PA6WeatherActivity PA6ReaderActivity PA6RemindersActivity PA6ReminderReceiver; do
  grep -q "$module" results/apk-manifest.txt || { echo "Missing Android6 module $module"; exit 1; }
done
echo 'PASS Android 6.0 API23: Alfa dashboard, all declared modules and planner WebView booted'
