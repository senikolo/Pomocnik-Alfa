#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
trap 'adb logcat -d -v time > results/logcat.txt || true; adb exec-out screencap -p > results/tablet-final.png || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb logcat -c
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity
sleep 20
adb shell dumpsys activity activities > results/dashboard-activities.txt
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.PARegionsActivity
sleep 15
adb shell dumpsys activity activities > results/activities.txt
adb shell am start -n 'com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity$Radio'
sleep 3
adb shell am start -n 'com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity$Tv'
sleep 3
adb shell am start -n 'com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity$Notes'
sleep 2
adb shell am start -n 'com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity$Weather'
sleep 2
adb shell am start -n 'com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity$Reminders'
sleep 2
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.HomeActivity
sleep 2
adb shell dumpsys activity activities > results/dashboard-after.txt
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
grep -q 'HomeActivity' results/dashboard-after.txt
echo 'PASS Android 6.0 API23: PA dashboard, planner, Radio, TV, Notes, Weather and Reminders started without fatal crashes'
