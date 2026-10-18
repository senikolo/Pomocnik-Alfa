#!/usr/bin/env bash
set -euo pipefail
mkdir -p results
pkg=com.senikolo.pa6tubeplus.test230
activity=$pkg/com.ispina.lokalnie.PA6TubeActivity
trap 'adb logcat -d -v time > results/logcat.txt || true' EXIT
adb install pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
# Installing the very same signed build over itself must retain data.
adb install -r pa6/Pomocnik-Alfa-Tablet-Android6-B3-A32-TEST.apk
adb shell pm grant "$pkg" android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant "$pkg" android.permission.READ_EXTERNAL_STORAGE
adb logcat -c
adb shell am start -W -n "$pkg/com.ispina.lokalnie.PA6DashboardActivity" > results/cold-start.txt
adb shell am start -W -n "$activity" --ez verify_performance true > results/tube-start.txt
for attempt in {1..10}; do
 adb logcat -d > results/performance.txt
 grep -q 'PA6_PERF.*PASS cached search' results/performance.txt && break
 sleep 1
done
adb logcat -d > results/performance.txt
grep -q 'PA6_PERF.*PASS cached search, 12-item page, stale-result rejection, isolated HTTP cancellation' results/performance.txt
adb shell am force-stop "$pkg"

adb shell am start -n "$pkg/com.ispina.lokalnie.PA6DashboardActivity"
sleep 3
adb exec-out screencap -p > results/dashboard.png
ffmpeg -v error -f lavfi -i color=c=blue:s=320x240:r=15 -f lavfi -i sine=frequency=440 -t 1 -c:v libx264 -pix_fmt yuv420p -c:a aac -y results/pa6-av.mp4
ffmpeg -v error -i results/pa6-av.mp4 -an -c:v copy -y results/pa6-silent.mp4
adb push results/pa6-av.mp4 /sdcard/Download/pa6-av.mp4
adb push results/pa6-silent.mp4 /sdcard/Download/pa6-silent.mp4
ffmpeg -v error -f lavfi -i sine=frequency=440 -t 5 -c:a aac -y results/pa6-audio.m4a
adb push results/pa6-audio.m4a /sdcard/Download/pa6-audio.m4a
adb shell am force-stop "$pkg"
adb shell am start -n "$activity" --ez verify_audio_only true
for attempt in {1..10}; do
 adb logcat -d > results/audio-only.txt
 grep -q 'PA6_PLAYER.*PLAYING Test samego audio' results/audio-only.txt && break
 sleep 1
done
grep -q 'PA6_PLAYER.*AUDIO_ONLY_MODE=true' results/audio-only.txt
grep -q 'PA6_PLAYER.*PLAYING Test samego audio' results/audio-only.txt
adb exec-out screencap -p > results/audio-only.png
adb shell am force-stop "$pkg"
adb shell am start -n "$activity" --ez verify_quality_menu true
sleep 2
adb shell uiautomator dump /sdcard/quality.xml
adb pull /sdcard/quality.xml results/quality-menu.xml
for quality in 240p 360p 480p 720p; do grep -q "$quality" results/quality-menu.xml; done
adb exec-out screencap -p > results/quality-menu.png
python3 - <<'PYCODE'
import re,subprocess,xml.etree.ElementTree as ET
for node in ET.parse('results/quality-menu.xml').iter('node'):
 if node.get('text')=='240p':
  x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
  subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
  break
else: raise AssertionError('240p not selectable')
PYCODE
sleep 1
adb shell uiautomator dump /sdcard/quality-selected.xml
adb pull /sdcard/quality-selected.xml results/quality-selected.xml
grep -q 'Jakość obrazu.*240p' results/quality-selected.xml
adb shell am force-stop "$pkg"

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
 adb shell am start -n "$activity" --ez verify_video_stream true --ez verify_libvlc true --es verify_layout "$mode"
 sleep 8
 adb exec-out screencap -p > "results/tube-$mode.png"
 adb shell input swipe 480 700 480 240 400
 adb exec-out screencap -p > "results/tube-$mode-scroll.png"
done
adb logcat -d > results/playback.txt
grep -q 'LAYOUT_MODE=Smartfon WIDE=false' results/playback.txt
grep -q 'LAYOUT_MODE=Tablet WIDE=true' results/playback.txt
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
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6TvActivity" --ez verify_tv_catalog true --ez verify_russia1 true --es verify_tv_country Rosja
sleep 15
adb logcat -d > results/tv-new-catalog.txt
grep -q 'PA6_TV_TEST.*PASS requested channels' results/tv-new-catalog.txt
adb exec-out screencap -p > results/tv-new-catalog.png
adb shell uiautomator dump /sdcard/tv-countries.xml
adb pull /sdcard/tv-countries.xml results/tv-countries.xml
for country in Polska Rosja; do grep -q "$country" results/tv-countries.xml; done
if grep -q 'PA6_PLAYER.*PLAYING Rossija 1' results/tv-new-catalog.txt; then
 echo 'CONFIRMED official Russia 1 playback on API23' > results/tv-russia1-status.txt
else
 echo 'UNCONFIRMED Russia 1 live playback on CI network' > results/tv-russia1-status.txt
fi
adb logcat -d -b crash > results/crashes.txt
! grep -q "Process: $pkg" results/crashes.txt
echo 'PASS local AAC audio-only playback, quality selection 240p-720p; API23 install/reinstall, complete AV validation, silent MP4 rejection, download error UI, Tablet/Smartfon startup, native LibVLC initialization'
