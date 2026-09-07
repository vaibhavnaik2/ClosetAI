#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-evidence
adb install -r packages/ClosetAI-Android-debug.apk
adb logcat -c
adb shell am start -W -n com.closetai.app/.MainActivity | tee device-evidence/launch.txt
sleep 5
adb shell pidof com.closetai.app > device-evidence/pid.txt
adb shell uiautomator dump /sdcard/closetai-ui.xml
adb pull /sdcard/closetai-ui.xml device-evidence/login.xml
if ! grep -q 'Sign in' device-evidence/login.xml; then
  echo 'Expected sign-in screen was not displayed'
  exit 1
fi
adb exec-out screencap -p > device-evidence/login.png
adb shell am force-stop com.closetai.app
adb shell am start -W -n com.closetai.app/.MainActivity
sleep 3
adb shell pidof com.closetai.app
adb logcat -d -b crash > device-evidence/crashes.txt
if grep -q 'FATAL EXCEPTION' device-evidence/crashes.txt; then
  cat device-evidence/crashes.txt
  exit 1
fi
