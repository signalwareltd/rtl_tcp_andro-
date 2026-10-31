#!/bin/sh
# Capture a bounded sample stream from an already-listening driver.
# Usage: stream_test.sh [port] [bytes] [local_output]
set -eu

PORT=${1:-1234}
BYTES=${2:-500000}
LOCAL=${3:-/tmp/rtl_capture.bin}
REMOTE="/sdcard/rtl_capture_$$.bin"

command -v adb >/dev/null 2>&1 || { echo "error: adb not found in PATH" >&2; exit 1; }

HEXPORT=$(printf '%04X' "$PORT")
if ! adb shell cat /proc/net/tcp 2>/dev/null \
    | awk -v p="$HEXPORT" 'toupper($2) ~ (":" p "$") { found = 1 } END { exit !found }'; then
  echo "error: nothing is listening on 127.0.0.1:$PORT; run start_driver.sh first" >&2
  exit 2
fi

adb shell "rm -f $REMOTE; sleep 2 | nc -W 2 127.0.0.1 $PORT | head -c $BYTES > $REMOTE"
adb pull "$REMOTE" "$LOCAL" >/dev/null
SIZE=$(adb shell "wc -c < $REMOTE" | tr -d ' \r\n')
adb shell "rm -f $REMOTE"

case "$SIZE" in
  ''|*[!0-9]*)
    echo "error: could not read capture size" >&2
    exit 3
    ;;
esac
echo "captured $SIZE bytes -> $LOCAL"

HEADER=$(dd if="$LOCAL" bs=1 count=4 2>/dev/null | tr -d '\000')
if [ "$HEADER" != "RTL0" ]; then
  echo "error: missing RTL0 header (got '$HEADER')" >&2
  exit 4
fi
if [ "$SIZE" -le 12 ]; then
  echo "error: header only, no sample data" >&2
  exit 5
fi
echo "header ok: RTL0; sample bytes: $((SIZE - 12))"
if command -v xxd >/dev/null 2>&1; then
  xxd -l 12 "$LOCAL"
else
  od -An -tx1 -N12 "$LOCAL"
fi

echo "note: the bounded client disconnect ends the session; broken pipe is expected."
adb logcat -d -v brief | grep -E 'SdrTcp|Client has connected|set sample rate' | tail -8 || true
