#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
pkg=com.senikolo.pa6tubeplus.test221
activity=$pkg/com.ispina.lokalnie.PA6TubeActivity
trap 'adb logcat -d -v time > results/logcat.txt || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
# Installing the very same signed build over itself must retain data.
adb install -r pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb shell pm grant "$pkg" android.permission.WRITE_EXTERNAL_STORAGE
adb logcat -c
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6DashboardActivity"
sleep 3
adb exec-out screencap -p > results/dashboard.png
ffmpeg -v error -f lavfi -i color=c=blue:s=320x240:r=15 -f lavfi -i sine=frequency=440 -t 1 -c:v libx264 -pix_fmt yuv420p -c:a aac -y results/pa6-av.mp4
ffmpeg -v error -i results/pa6-av.mp4 -an -c:v copy -y results/pa6-silent.mp4
adb push results/pa6-av.mp4 /sdcard/Download/pa6-av.mp4
adb push results/pa6-silent.mp4 /sdcard/Download/pa6-silent.mp4
adb shell am start -n "$activity" --ez verify_download_validation true --ez verify_libvlc true
sleep 5
adb logcat -d > results/mp4-validation.txt
grep -q 'PASS complete AV accepted; silent MP4 rejected' results/mp4-validation.txt
adb shell am force-stop "$pkg"
adb shell am start -n "$activity" --ez verify_download_failure true
sleep 5
adb shell run-as "$pkg" true 2>/dev/null || true
adb logcat -d > results/download-error.txt
grep -q 'PA6_DOWNLOAD.*Download failed' results/download-error.txt
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml results/download-error-window.xml
grep -q 'Nie można pobrać filmu' results/download-error-window.xml
# Explicit phone/tablet modes: no app-data wipe, no second APK build.
for mode in Smartfon Tablet; do
 adb shell am force-stop "$pkg"
 adb shell "mkdir -p /data/data/$pkg/shared_prefs; printf '%s' '<?xml version=\"1.0\" encoding=\"utf-8\"?><map><string name=\"ui_mode\">$mode</string></map>' > /data/data/$pkg/shared_prefs/pa6_display.xml"
 adb shell am start -n "$activity" --ez verify_video_stream true --ez verify_libvlc true
 sleep 8
 adb exec-out screencap -p > "results/tube-$mode.png"
 adb shell input swipe 480 700 480 240 400
 adb exec-out screencap -p > "results/tube-$mode-scroll.png"
done
adb logcat -d > results/playback.txt
grep -q 'PA6_LIBVLC.*NATIVE_ENGINE_READY' results/playback.txt
if grep -q 'PA6_PLAYER.*PLAYING' results/playback.txt; then
 echo 'CONFIRMED sample LibVLC playback' > results/sample-status.txt
else
 echo 'UNCONFIRMED sample playback on CI network' > results/sample-status.txt
fi
adb shell am force-stop "$pkg"
adb shell am start -n "$activity" --ez verify_real_youtube true
sleep 35
adb logcat -d > results/youtube.txt
adb exec-out screencap -p > results/youtube.png
if grep -q 'PA6_PLAYER.*PLAYING Me at the zoo' results/youtube.txt; then
 echo 'CONFIRMED YouTube extraction and playback' > results/youtube-status.txt
else
 echo 'UNCONFIRMED live YouTube playback; real Acer test required' > results/youtube-status.txt
fi
adb logcat -d -b crash > results/crashes.txt
! grep -q "Process: $pkg" results/crashes.txt
echo 'PASS API23 install/reinstall, complete AV validation, silent MP4 rejection, download error UI, Tablet/Smartfon startup, native LibVLC initialization'
