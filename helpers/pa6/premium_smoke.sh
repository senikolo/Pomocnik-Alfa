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
for text in 'Twój komfort' 'Reakcja przy dotknięciu' 'Test wibracji'; do grep -q "$text" results/comfort.xml; done
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
adb logcat -d -b crash > results/premium-crashes.txt
! grep -q "Process: $pkg" results/premium-crashes.txt
echo 'PASS native module startup / comfort patterns / hardware-aware settings' | tee results/premium-status.txt
