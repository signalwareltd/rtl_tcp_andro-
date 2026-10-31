---
name: device-testing
description: End-to-end testing of the rtl_tcp_andro Android driver with a physical USB SDR dongle. Use when validating driver builds on a device, starting rtl_tcp sessions, streaming samples to SDR Touch or a TCP client, testing unplug/replug and client-close behaviour, or debugging native/USB issues with adb, logcat, and run-as. Covers wireless ADB setup, USB permission dialogs, build/install quirks, on-device netcat testing, and known pitfalls. Not for emulator-only UI tests, Play Store policy audits, or native changes tested without a device.
---

# Device testing rtl_tcp_andro

The driver (`marto.rtl_tcp_andro`) opens an RTL-SDR dongle over USB and serves the rtl-tcp
protocol to another app (SDR Touch, RF Analyzer, etc.) or a raw TCP client. Validate changes on a
real phone with a real dongle; emulators cannot exercise USB host mode.

Reference setup: an arm64 Android phone on API 37 with an RTL2832U + R820T dongle. The steps are
generic; only USB host support, a free USB port, and a recent Android API level matter.

## 1. Before anything: wireless ADB, then free the USB port

The phone's USB-C port must be available for the SDR dongle. If adb runs over USB, the port is
occupied (OTG + charging splitters are unreliable), so connect adb over Wi-Fi first.

Android 11+ (Wireless debugging):

```sh
# On the phone: Developer options -> Wireless debugging -> Pair device with pairing code
adb pair <phone-ip>:<pairing-port>      # enter the 6-digit code shown on the phone
adb connect <phone-ip>:<wireless-port>  # the port on the Wireless debugging screen, not the pairing port
adb devices -l
```

Both ports change after a reboot or network change; re-read them from the screen. Keep the phone
and host on the same network; a VPN can break mDNS/discovery.

Older Android (or if pairing is unavailable), with a one-time USB connection:

```sh
adb tcpip 5555
# unplug USB, then:
adb connect <phone-ip>:5555
```

Find the phone IP with `adb shell ip -f inet addr show wlan0` while still connected, or from
Settings -> Wi-Fi.

Only after `adb devices -l` shows the phone over the network, plug the dongle into the phone with a
USB-OTG adapter and confirm the app sees it (`adb logcat` should show `rtl-sdr /dev/bus/usb/...`).
The phone must also have `android.hardware.usb.host` (virtually all OTG-capable phones do).

Sanity check the whole chain before debugging the app:

```sh
adb devices -l
adb shell getprop ro.build.version.sdk        # targetSdk 37, minSdk 24
adb shell dumpsys usb | head -40              # or: adb logcat | grep UsbHostManager
```

## 2. Build, install, launch

Build with Android Studio's JBR. The default JDK (8) cannot run this Gradle project. The JBR
ships inside the Android Studio installation (Linux/Windows: `<android-studio>/jbr`; macOS:
`<android-studio>/Contents/jbr/Contents/Home`):

```sh
export JAVA_HOME="<path to Android Studio's bundled JBR>"
./gradlew :app:assembleDebug
```

Install:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If installation fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed build is signed with
the release key (only CI has it). Back up the installed splits, uninstall, then install the debug
build:

```sh
adb shell pm path marto.rtl_tcp_andro          # lists base.apk + split_config.*.apk
# adb pull each path into a backup dir first
adb uninstall marto.rtl_tcp_andro
adb install app/build/outputs/apk/debug/app-debug.apk
```

Uninstalling loses app data (the driver keeps almost none). Restore the release build later with
`adb install-multiple <pulled splits>`.

Launch a session through the exported `iqsrc://` intent:

```sh
adb shell am start -a android.intent.action.VIEW \
  -d "'iqsrc://-a 127.0.0.1 -p 1234 -f 100000000 -s 2048000 -g 0'" \
  -n marto.rtl_tcp_andro/com.sdrtouch.rtlsdr.DeviceOpenActivity
```

The URI is quoted twice on purpose: the local shell strips the outer quotes, then the remote adb
shell re-parses the command, so the inner single quotes keep it from splitting at the spaces.
Target `DeviceOpenActivity` explicitly so no intent chooser appears.

Arguments (`SdrTcpArguments`): `-a` bind address, `-p` port (required), `-f` frequency Hz, `-s`
sample rate Hz, `-g` gain in tenths of dB (0 = auto), `-P` ppm correction, `-T` bias-T (0/1).
The activity enumerates the attached dongle and asks for USB permission on first use.

Success looks like:

```text
D/RtlSdr: Opening fd <n>
I/rtl-sdr-android: ERROR: Found Rafael Micro R820T tuner
I/SDR: SdrTcp: Listening on 127.0.0.1:1234
D/RtlSdr: The rtl-tcp implementation is running and is ready to accept clients
```

## 3. Grant USB permission (first run per dongle)

The system shows `com.android.systemui/.usb.UsbPermissionActivity`. The screen must be awake and
unlocked:

```sh
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb exec-out screencap -p > /tmp/screen.png       # inspect visually
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml /tmp/ui.xml
# find the OK/Allow button bounds in ui.xml, then:
adb shell input tap <x> <y>
```

`adb shell dumpsys window | grep mCurrentFocus` confirms the dialog is focused. The dialog may also
offer an "Always open ... when connected" checkbox; not required.

Gotcha (fixed by the Phase 2 lifecycle fixes on the `libusb-1.0.30-upgrade` branch): dismissing
the dialog used to resume `DeviceOpenActivity`, whose `onStart` started a **second** session; the
first claim then failed with `usb_claim_interface error -6` (`LIBUSB_ERROR_BUSY`). The activity and
service now serialize session starts, so only one session opens. Older builds need a force-stop
and relaunch after granting permission:

```sh
adb shell am force-stop marto.rtl_tcp_andro
adb shell am start -a android.intent.action.VIEW \
  -d "'iqsrc://-a 127.0.0.1 -p 1234 -f 100000000 -s 2048000 -g 0'" \
  -n marto.rtl_tcp_andro/com.sdrtouch.rtlsdr.DeviceOpenActivity
# or simply: scripts/start_driver.sh
```

## 4. Verify streaming

Prefer running the client **on the device** with toybox `nc`. `adb forward tcp:1234 tcp:1234` hung
over the wireless-ADB link during testing; do not block a test run on it.

If the device has no `nc` (Android 9 and older often lack it), start the driver bound to the LAN
with `-a 0.0.0.0` and run the client from the host against `<phone-ip>:1234`. This is also the
simplest way to create a stalled client (connect and never read).

```sh
# ~4 MB/s at 2.048 Msps; cap the capture and delete it afterwards
adb shell 'sleep 2 | nc -W 2 127.0.0.1 1234 | head -c 500000 > /sdcard/rtl_capture.bin; ls -l /sdcard/rtl_capture.bin'
adb pull /sdcard/rtl_capture.bin /tmp/rtl_capture.bin
xxd -l 16 /tmp/rtl_capture.bin
adb shell rm -f /sdcard/rtl_capture.bin
```

The first 12 bytes are the dongle info: ASCII `RTL0`, uint32 BE dongle type (5 = R820T), uint32 BE
gain count. The rest is 8-bit I/Q sample data.

`head -c` closes the read side; the driver detects the client disconnect and ends the TCP session
(`SdrTcp: Closing sdrtcp due to main thread finishing`). That is expected for this bounded test,
not a failure. To keep a session alive, run a client that consumes continuously (SDR Touch or
`scripts/rtl_tcp_client.py`).

`scripts/stream_test.sh` wraps the capture + header check. It needs a session already listening.
`scripts/start_driver.sh` launches a clean session and waits for the listener.

## 5. Test with SDR Touch (realistic client)

SDR Touch (`marto.androsdr2`) is the best end-to-end client. In normal use it sends the `iqsrc://`
intent to the driver and connects to the announced port. On USB attach, the driver's `UsbDelegate`
auto-launches SDR Touch if it is installed; that is the most realistic path.

Manual flow:

```sh
# 1) start the driver (above) and wait for "Listening"
# 2) launch SDR Touch and start the radio, or start its main activity:
adb shell am start -n marto.androsdr2/.SDRTouchMain
# 3) confirm the client connected and is sending commands:
adb logcat -d -v brief | grep -E 'SdrTcp|set sample rate|set direct sampling|set gain mode'
```

Expected after SDR Touch connects: `SdrTcp: Client has connected`, then tuning commands such as
`set direct sampling 0`, `set freq correction 0`, `set gain mode 0`, `set sample rate 1024000`.
`/proc/net/tcp` shows an ESTABLISHED 127.0.0.1:<port> pair owned by the app and SDR Touch.

SDR Touch may use a different port than your manual intent because it launches the driver itself
and picks its own port. Read the port from `SdrTcp: Listening on 127.0.0.1:<port>`.

## 6. rtl-tcp commands and clean close

A command is 5 bytes: 1-byte code + uint32 big-endian parameter. Codes live in
`rtlsdr/src/main/cpp/src/tcp_commands.h`; common ones: `0x01` frequency, `0x02` sample rate,
`0x03` gain mode, `0x04` gain, `0x05` ppm, `0x7e` exit, `0x7f` gain percent, `0x80` 16-bit
(unsupported for RTL-SDR).

Android's shell `printf` is not reliable for `\x` escapes, so create the file on the host and push
it:

```sh
# SET_FREQ 101000000 (0x06053180), then SET_SAMPLE_RATE 1024000 (0x000FA000)
printf '\x01\x06\x05\x31\x80\x02\x00\x0f\xa0\x00' > /tmp/cmds.bin
adb push /tmp/cmds.bin /data/local/tmp/cmds.bin
# Commands sent immediately after connect are applied on the Phase 2 branch; older builds
# may need a ~1 s delay (see known issues).
adb shell '(cat /data/local/tmp/cmds.bin; sleep 3) | nc -W 3 127.0.0.1 1234 > /dev/null'

# Clean close
printf '\x7e\x00\x00\x00\x00' > /tmp/exit.bin
adb push /tmp/exit.bin /data/local/tmp/exit.bin
adb shell 'cat /data/local/tmp/exit.bin | nc -W 3 127.0.0.1 1234 > /dev/null'
```

Expected close: `tcpCommandCallback: client requested to close rtl_tcp_andro`,
`SdrTcp: Requested sdrtcp stop asynchroneously`, `Server thread shut down`, then
`rtlsdr_read_async finished successfully` and `Successfully closed service` (or an exception
message; see known issues).

## 7. Lifecycle tests

Client disconnect and `0x7e` exercise the cancellation path. Then test the USB detach path.

Unplugging cannot be simulated in software without root, and this device was not rooted. Do it
manually, and test two variants:

1. **With an active client** (SDR Touch streaming, or a long-lived nc/python client):
   unplug for ~5 s. Expect fast teardown: `rtlsdr_read_async failed: -5`, sdrtcp stop, server
   thread exits, client connection resets, fd released, no crash. Replug and reopen.
2. **With no client** (the app is streaming into a full work pool):
   unplug. On the Phase 2 branch, `sdrtcp_feed()` no longer sleeps and teardown is sub-second
   (`rtlsdr_read_async stopped with -5` → `Device has been detached` →
   `SdrException: LIBUSB_ERROR_NO_DEVICE`). On the lifecycle-hardening build the feed path also
   never waits on the work pool, so teardown is milliseconds — including with a stalled client that
   never reads (that case used to delay detection by up to ~15 s). Builds between the
   resubmit-failure fix and Phase 2 took up to ~7 s; older builds can hang forever with the deleted
   usbfs fd held open (see references/known-issues).

Checks after either test:

```sh
adb logcat -b crash -d -v brief            # must be empty: no fatal signal/tombstone
adb shell run-as marto.rtl_tcp_andro ls -l /proc/$(adb shell pidof marto.rtl_tcp_andro | tr -d '\r')/fd | grep -E 'bus|usb'
# After teardown there should be no /dev/bus/usb fd (or only the current session's).
```

Replug auto-start is not guaranteed when the replug happens during teardown. If no new session
appears, relaunch the intent manually; that path is valid.

## 8. Debugging and device puppeteering

Tags: `RtlSdr` (Java), `SDR` (sdrtcp/native), `rtl-sdr-android` (JNI/librtlsdr), `libusb`
(libusb system logging), `UsbHostManager`, `UsbPermissionActivity`, `ActivityManager`.

```sh
adb logcat -c
adb logcat -d -v threadtime | grep -E 'RtlSdr|SDR|rtl-sdr-android|libusb|Fatal signal|crash_dump'
adb logcat -b crash -d -v brief
adb shell dumpsys activity services marto.rtl_tcp_andro
adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'
adb shell dumpsys package marto.rtl_tcp_andro | grep -E 'versionName|versionCode'
```

`debuggerd -b <pid>` requires root and fails with `debuggerd: root is required` on retail builds.
For a debuggable build, use `run-as` to inspect the process (same UID):

```sh
adb shell run-as marto.rtl_tcp_andro ls -l /proc/<pid>/fd
adb shell run-as marto.rtl_tcp_andro cat /proc/<pid>/task/<tid>/comm
adb shell run-as marto.rtl_tcp_andro cat /proc/<pid>/task/<tid>/wchan
adb shell run-as marto.rtl_tcp_andro cat /proc/<pid>/task/<tid>/syscall
```

Useful signals: a read thread stuck in `do_sys_poll` with `ppoll` `nfds=1` is polling only
libusb's internal event pipe — the device fd was removed but the app never cancelled. A usbfs fd
symlink ending in `(deleted)` is a held-open removed device. See `references/debugging.md`.

UI puppeteering:

```sh
adb shell input keyevent KEYCODE_WAKEUP
adb shell input swipe 540 2000 540 600 200      # dismiss keyguard (no PIN)
adb exec-out screencap -p > /tmp/screen.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
adb shell input tap <x> <y>
```

`uiautomator dump` captures the focused window; if it dumps SystemUI's shade instead of the app
dialog, check `mCurrentFocus` and wake/unlock first.

More detail, including log-tag meanings and native checks, is in `references/debugging.md`.

## 9. Known issues that will confuse a test run

Short list; full symptoms and workarounds in `references/known-issues.md`.

- **Command listener can exit immediately** right after a client connects (thread is created before
  `STAGE_CLIENT_SERVING` is set). **Fixed on the Phase 2 branch**; older builds need a ~1 s delay.
- **`recv() == 0` is not handled** in the command listener; a client EOF can spin the thread.
  **Fixed on the Phase 2 branch.**
- **`sdrtcp_cleanup()` leaks `client_socket`** (server side stays in `CLOSE_WAIT`). **Fixed on the
  Phase 2 branch** (socket is shut down and closed; no `CLOSE_WAIT` after teardown).
- **500 ms `sdrtcp_feed()` sleep** when no client is connected. **Removed on the Phase 2 branch**;
  buffers are dropped and no-client unplug teardown is sub-second. The lifecycle-hardening build
  also removed the 1 s work-pool wait from the feed path, so a stalled (never-reading) client no
  longer delays disconnect detection.
- **Misleading error reporting**: `rtlsdr_read_async` can log `failed: -5` and then
  `finished successfully`; Java may report `SdrException: Unknown error!`. **Fixed on the Phase 2
  branch** (device loss maps to `LIBUSB_ERROR_NO_DEVICE` and the success log is conditional).
- **Duplicate session after the USB permission dialog** (`LIBUSB_ERROR_BUSY`): **fixed on the
  Phase 2 branch** (see section 3).
- **Fixed on the `libusb-1.0.30-upgrade` branch**: a failed resubmission after a data completion
  used to be ignored, leaving no in-flight transfer and hanging the async read on unplug. If
  testing an older build or `master` before that branch lands, expect that hang.
- **Also fixed on that branch (Phase 2)**: asynchronous `sdrtcp` teardown vs. the finalizer (the JNI
  now joins the server thread before `rtlsdr_close`), `dev->rtl_dev` lifetime, the cancellation
  free-while-flying window, and session serialization. See the parent plan §6c.
- **Android 9 has no `nc`**: use `-a 0.0.0.0` and a host client (see section 4).
- **Huawei/EMUI freezes the app when the screen is off**: native threads sit in `__refrigerator`,
  connections queue in the kernel backlog but are never accepted. Keep the screen on and whitelist
  the driver from battery optimisation. Details in references/known-issues.md.

## 10. Quick checklist

- [ ] `adb devices -l` shows the phone over wireless ADB; phone USB port free
- [ ] dongle attached; permission granted; logs show the tuner and `Listening`
- [ ] bounded capture starts with `RTL0` and contains samples
- [ ] SDR Touch connects and sends tuning commands
- [ ] `0x7e` / client close ends the session cleanly
- [ ] unplug with an active client: fast clean teardown, no crash, reopen works
- [ ] unplug with no client: teardown finishes in milliseconds on the hardening build (≤ ~7 s on older builds), no crash
- [ ] `adb logcat -b crash -d` empty; no leaked usbfs fd after teardown
- [ ] captures deleted from `/sdcard`
