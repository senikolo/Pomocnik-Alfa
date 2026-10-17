#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
trap 'adb logcat -d -v time > results/logcat.txt || true; adb exec-out screencap -p > results/tablet-final.png || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb logcat -c
adb shell am start -n com.senikolo.pa6tablet/com.ispina.lokalnie.PARegionsActivity
sleep 20
adb shell dumpsys activity activities > results/activities.txt
adb shell dumpsys package com.senikolo.pa6tablet > results/package.txt
adb exec-out screencap -p > results/tablet-start.png
adb shell uiautomator dump /sdcard/window.xml || true
adb pull /sdcard/window.xml results/window.xml || true
adb logcat -d -b crash > results/crashes.txt
! grep -q 'FATAL EXCEPTION' results/crashes.txt
grep -q 'com.senikolo.pa6tablet' results/package.txt
grep -q 'PARegionsActivity' results/activities.txt
echo 'PASS Android 6.0 API23: PA real Planner APK installed and activity displayed'
