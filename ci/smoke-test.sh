#!/usr/bin/env bash
# Installs the APK on a running emulator, searches for words, and fails if the
# dictionary doesn't answer. Screenshots go to the output folder.
set -euo pipefail
APK=$1
OUT=$2
PKG=io.github.akrishna87.dictionary
mkdir -p "$OUT"

adb install -r "$APK"
adb shell am start -n "$PKG/.MainActivity"
sleep 8 # first launch copies the database out of the APK

ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb exec-out cat /sdcard/ui.xml; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
wait_for() { # text, seconds
  for _ in $(seq "$2"); do ui | grep -q "$1" && return 0; sleep 1; done
  echo "FAIL: never saw '$1' on screen"; ui | head -c 3000; shot "fail-$(echo "$1" | tr -c 'A-Za-z0-9' _)"; exit 1
}
tap_text() { # tap the centre of the first node whose text matches
  local b
  b=$(ui | python3 -c "
import re,sys
x=sys.stdin.read()
m=re.search(r'text=\"$1[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x)
print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else sys.exit(1)")
  adb shell input tap $b
}

wait_for "Search the dictionary" 30
shot 1-home
wait_for "WORD OF THE DAY" 5

tap_text "Search the dictionary"
adb shell input text "serendip"
wait_for "serendipity" 10
shot 2-search

tap_text "serendipity"
wait_for "good luck in making unexpected" 10
shot 3-word

adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
tap_text "Search the dictionary" || true
adb shell input text "abetted"
adb shell input keyevent KEYCODE_ENTER
wait_for "Showing the base form" 10
shot 4-inflection
echo "smoke test passed"
