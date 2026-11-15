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

shot() {   # name, extra
  adb shell am force-stop $PKG
  sleep 1
  if [ -n "$2" ]; then adb shell am start -W -n $MAIN --es demo seed >/dev/null; sleep 4; adb shell am start -W -n $MAIN --es demo "$2" >/dev/null
  else adb shell am start -W -n $MAIN --es demo seed >/dev/null; fi
  sleep 6
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
# rotated 180
adb shell am force-stop $PKG; adb shell am start -W -n $MAIN --es demo rot >/dev/null; sleep 2
shot 11_home_rotated ""
shot 12_ac_rotated match_ac
adb shell am start -W -n $MAIN --es demo unrot >/dev/null
adb logcat -d -s AndroidRuntime:E > "$OUT/crash_log.txt" || true
ls -la "$OUT"
