#!/usr/bin/env bash
# Runs inside the emulator step: install the app, open each screen with demo data, screenshot.
set -x
PKG=com.offlineremote.app
MAIN=$PKG/.ui.MainActivity
OUT=shots
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

shot() {   # name, demo value (fresh process every time), optional wait seconds
  adb shell am start -S -W -n $MAIN --es demo "${2:-seed}" >/dev/null
  sleep ${3:-7}
  adb exec-out screencap -p > "$OUT/$1.png"
}

shot 01_home ""
shot 02_add add
shot 03_more more
shot 04_brands brands
shot 05_match_ac match_ac
shot 06_tv tv
shot 07_match_led match_led
shot 08_learn learn
shot 09_learned learned
shot 10_match_tv match_tv
shot 13_scan_audio scan_audio
shot 14_scan_ac scan_ac 20
shot 15_soundbar_brands soundbars
shot 17_scan_led scan_led 12
# rotated 180
adb shell am start -S -W -n $MAIN --es demo rot >/dev/null; sleep 2
shot 11_home_rotated ""
shot 12_ac_rotated match_ac
shot 16_scan_rotated scan_audio
# record the system display rotation while rotated (expect ROTATION_180 / rotation=2)
{ adb shell dumpsys window displays | grep -iE "mRotation|mCurrentRotation|rotation=" | head -5;
  adb shell dumpsys input | grep -i "SurfaceOrientation" | head -2; } > "$OUT/rotation.txt" 2>&1
adb shell am start -S -W -n $MAIN --es demo unrot >/dev/null
adb logcat -d -s AndroidRuntime:E > "$OUT/crash_log.txt" || true
ls -la "$OUT"
