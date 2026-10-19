#!/usr/bin/env bash
set -euo pipefail
pkg=$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" pa6/badging.txt | head -1)
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/com.ispina.lokalnie.PA6SettingsActivity" --ez verify_comfort true
sleep 2
adb logcat -d > results/comfort.txt
grep -q 'PA6_COMFORT.*PASS patterns 3 strengths / disabled / hardware=' results/comfort.txt
adb shell uiautomator dump /sdcard/comfort.xml
adb pull /sdcard/comfort.xml results/comfort.xml
grep -q 'Twój komfort' results/comfort.xml
adb shell input swipe 500 650 500 240 350
sleep 1
adb shell uiautomator dump /sdcard/comfort-bottom.xml
adb pull /sdcard/comfort-bottom.xml results/comfort-bottom.xml
grep -q 'Test wibracji' results/comfort-bottom.xml
adb exec-out screencap -p > results/premium-settings.png
for module in PA6DashboardActivity PA6NotesActivity PA6RemindersActivity PA6WeatherActivity PA6RadioActivity PA6SoundsActivity PA6ReaderActivity PA6DownloadsActivity; do
 adb shell am force-stop "$pkg"
 adb shell am start -W -n "$pkg/com.ispina.lokalnie.$module" > "results/start-$module.txt"
 sleep 2
 adb exec-out screencap -p > "results/premium-$module.png"
 timeout 20s adb shell uiautomator dump /sdcard/premium.xml
 adb pull /sdcard/premium.xml "results/premium-$module.xml"
 grep -q "package=\"$pkg\"" "results/premium-$module.xml"
done
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6TubeActivity" --es verify_layout Tablet
sleep 2
adb exec-out screencap -p > results/premium-discovery.png
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6TubeActivity" --ez verify_controls true --es verify_layout Tablet
sleep 3
# UIAutomator often never idles during SurfaceView playback; these transitions
# have an explicit diagnostic hook and verify actual engine continuity below.
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6TubeActivity" --ez verify_window_modes true --es verify_layout Tablet
sleep 4
adb exec-out screencap -p > results/premium-mini.png
sleep 3
adb exec-out screencap -p > results/premium-fullscreen.png
sleep 4
adb exec-out screencap -p > results/premium-embedded-return.png
adb logcat -d > results/premium-window-modes.txt
for event in FULL EMBEDDED; do grep -q "PA6_WINDOW.*$event" results/premium-window-modes.txt; done
grep -q 'PA6_WINDOW.*PASS continuous playback' results/premium-window-modes.txt
# Radio engine over a deterministic HTTP audio fixture, no dependency on live station availability.
python3 -m http.server 8765 --directory results > results/radio-fixture-server.txt 2>&1 &
server=$!
trap 'kill "$server" || true' EXIT
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/com.ispina.lokalnie.PA6RadioActivity" --es station_url http://10.0.2.2:8765/pa6-audio.m4a --es station_name 'Radio kontrolne'
sleep 5
adb logcat -d > results/premium-radio.txt
grep -q 'PA6_RADIO.*Teraz gra: Radio kontrolne' results/premium-radio.txt
adb shell am force-stop "$pkg"
python3 helpers/pa6/premium_interaction_test.py "$pkg"
python3 helpers/pa6/remaining_module_test.py "$pkg"
adb logcat -d -b crash > results/premium-crashes.txt
! grep -q "Process: $pkg" results/premium-crashes.txt
echo 'PASS native module startup / comfort patterns / hardware-aware settings' | tee results/premium-status.txt
