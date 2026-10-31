#!/bin/sh
# Start a clean rtl_tcp session and wait for the TCP listener.
# Usage: start_driver.sh [port] [freq_hz] [rate_hz] [gain_tenth_db]
set -eu

PKG=marto.rtl_tcp_andro
PORT=${1:-1234}
FREQ=${2:-100000000}
RATE=${3:-2048000}
GAIN=${4:-0}

command -v adb >/dev/null 2>&1 || { echo "error: adb not found in PATH" >&2; exit 1; }

adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb logcat -c
# The URI contains spaces. Quote it again so the *remote* adb shell keeps it as
# one argument; otherwise am start splits it and fails to resolve the intent.
URI="iqsrc://-a 127.0.0.1 -p $PORT -f $FREQ -s $RATE -g $GAIN"
adb shell am start -a android.intent.action.VIEW \
  -d "'$URI'" \
  -n "$PKG/com.sdrtouch.rtlsdr.DeviceOpenActivity" >/dev/null

HEXPORT=$(printf '%04X' "$PORT")
i=0
while [ "$i" -lt 20 ]; do
  if adb shell cat /proc/net/tcp 2>/dev/null \
      | awk -v p="$HEXPORT" 'toupper($2) ~ (":" p "$") { found = 1 } END { exit !found }'; then
    echo "driver listening on 127.0.0.1:$PORT"
    adb logcat -d -v brief | grep -E 'RtlSdr|SDR|rtl-sdr-android' | tail -12 || true
    exit 0
  fi
  i=$((i + 1))
  sleep 0.5
done

echo "error: no listener on port $PORT" >&2
echo "recent logs:" >&2
adb logcat -d -v brief \
  | grep -E 'RtlSdr|SDR|rtl-sdr-android|UsbPermission|libusb' \
  | tail -30 >&2 || true
echo "If the USB permission dialog is visible, grant it and retry (see SKILL.md section 3)." >&2
exit 2
