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
tap_text() { # text [dx dy]: tap the centre of the first node starting with text, or dx,dy from its top-left corner
  local b
  b=$(ui | python3 -c "
import re,sys
x=sys.stdin.read()
m=re.search(r'text=\"$1[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x)
if not m: sys.exit(1)
dx,dy='${2:-}','${3:-}'
print(int(m[1])+int(dx),int(m[2])+int(dy)) if dx else print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2)") || true
  [ -n "$b" ] || { echo "FAIL: nothing on screen matching '$1'"; shot "fail-tap"; exit 1; }
  adb shell input tap $b
}

submit_search() { # text to wait for: press Enter, retrying if the key press was dropped
  for _ in 1 2 3; do
    adb shell input keyevent KEYCODE_ENTER
    for _ in $(seq 8); do ui | grep -q "$1" && return 0; sleep 1; done
  done
  wait_for "$1" 1
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

adb shell input keyevent KEYCODE_BACK # back to the search screen (a second back would leave the app)
wait_for "serendip" 10
tap_text "serendip" # the search field still holds the last query
adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
adb shell input text "abetted"
sleep 1
submit_search "Showing the base form"
shot 4-inflection

tap_text "Thesaurus"
wait_for "All synonyms\|No synonyms\|Broader" 10 # which one depends on the word; the tab itself must work
shot 5-thesaurus

adb shell input keyevent KEYCODE_BACK # close the word; the search field still holds "abetted"
wait_for "abetted" 10
tap_text "abetted"
adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
adb shell input text "happy"
sleep 1
submit_search "Antonyms" # happy has antonyms (unhappy) under its definitions
shot 5b-antonyms

adb shell input keyevent KEYCODE_BACK # close the word
tap_text "Library"
wait_for "Browse A" 10
wait_for "Shelves" 5
shot 6-library

wait_for "Books" 5
tap_text "See all"
wait_for "public-domain classics" 10
tap_text "Find a title"
adb shell input text "pride"
sleep 1
tap_text "Pride and Prejudice"
wait_for "universally acknowledged" 20
shot 7-reader
tap_text "It is a truth" 60 45 # tap the word "It"
wait_for "Full entry" 10
shot 8-define-word
tap_text "Full entry"
wait_for "My note" 10 # the full dictionary entry
adb shell input keyevent KEYCODE_BACK # entry -> book
wait_for "universally acknowledged" 10
adb shell input keyevent KEYCODE_BACK # book -> library
wait_for "Browse A" 10
tap_text "Browse A"
wait_for "Pick a letter" 10
tap_text "Z"
wait_for "words\|Browsing" 10
shot 9-browse
echo "smoke test passed"
