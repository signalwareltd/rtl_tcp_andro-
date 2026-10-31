# Debugging and native checks

## Log tags

| Tag | Source |
|---|---|
| `RtlSdr` | Java activity/service (`Log.appendLine`) |
| `SDR` | native sdrtcp / JNI `LOGI` |
| `rtl-sdr-android` | librtlsdr JNI `LOGI` |
| `libusb` | libusb system logging (`USE_SYSTEM_LOGGING_FACILITY`) |
| `UsbHostManager` | Android USB host attach/detach |
| `UsbPermissionActivity` | system USB permission dialog |
| `ActivityManager`, `AndroidRuntime` | lifecycle, Java crashes |

```sh
adb logcat -c
adb logcat -d -v threadtime | grep -E 'RtlSdr|SDR|rtl-sdr-android|libusb|Fatal signal|crash_dump'
```

## Typical sequences

Open success:

```text
D/RtlSdr: Opening fd <n>
I/rtl-sdr-android: ERROR: Found Rafael Micro R820T tuner
I/SDR: Set sampling rate to <rate>
I/SDR: SdrTcp: Listening on 127.0.0.1:<port>
D/RtlSdr: The rtl-tcp implementation is running and is ready to accept clients
```

Client connect and tuning (real client):

```text
I/SDR: SdrTcp: TCP server successfully started and listening for clients!
I/SDR: SdrTcp: Client has connected.
I/SDR: set direct sampling 0
I/SDR: set freq correction 0
I/SDR: set gain mode 0
I/SDR: set sample rate 1024000
```

Clean close via `0x7e`:

```text
D/RtlSdr: tcpCommandCallback: client requested to close rtl_tcp_andro
I/SDR: SdrTcp: Requested sdrtcp stop asynchroneously
I/SDR: SdrTcp: Command listener thread exiting
I/SDR: SdrTcp: Waiting for command thread to die
I/SDR: SdrTcp: TCP server shutting down.
I/SDR: SdrTcp: Server thread shut down
I/SDR: rtlsdr_read_async finished successfully
D/RtlSdr: Successfully closed service
```

Unplug with an active client (fast path):

```text
I/SDR: rtlsdr_read_async failed: -5
I/SDR: rtlsdr_read_async finished successfully    # misleading; see known issues
I/SDR: SdrTcp: Requested sdrtcp stop asynchroneously
I/SDR: SdrTcp: Server thread shut down
```

The framework's `UsbHostManager: Removed device at /dev/bus/usb/...` line can appear slightly
after the app's own failure line.

## Crash buffer and tombstones

```sh
adb logcat -b crash -d -v brief
```

Fatal signals and `crash_dump` output land in this buffer. Tombstones under `/data/tombstones` are
not readable without root. `debuggerd -b <pid>` fails on retail builds with
`debuggerd: root is required`.

## Inspecting a live process with `run-as`

Debug builds are debuggable, so the app UID can read its own `/proc` entries:

```sh
PID=$(adb shell pidof marto.rtl_tcp_andro | tr -d '\r')
adb shell run-as marto.rtl_tcp_andro ls -l /proc/$PID/fd
adb shell run-as marto.rtl_tcp_andro ls /proc/$PID/task
adb shell run-as marto.rtl_tcp_andro cat /proc/$PID/task/<tid>/comm
adb shell run-as marto.rtl_tcp_andro cat /proc/$PID/task/<tid>/wchan
adb shell run-as marto.rtl_tcp_andro cat /proc/$PID/task/<tid>/syscall
```

Useful signals:

- A usbfs fd symlink ending in `(deleted)` means the removed device's fd is still held.
- `wchan = do_sys_poll` plus an arm64 `ppoll` (syscall 73) with `nfds = 1` means the thread polls
  only libusb's internal event pipe: the device fd was removed but the async read was never
  cancelled.
- Java threads appear as `Thread-<n>`; sdrtcp threads are unnamed native pthreads.

## Device puppeteering

```sh
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input swipe 540 2000 540 600 200       # only on a lock screen without a PIN
adb exec-out screencap -p > /tmp/screen.png
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'
adb shell input tap <x> <y>
```

`uiautomator dump` captures the focused window. If it returns SystemUI's notification shade instead
of the app dialog, wake/unlock and check `mCurrentFocus` first.

## Artifact checks (native libraries)

Resolve the NDK from the environment rather than hard-coding a path:

```sh
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?set ANDROID_SDK_ROOT or ANDROID_HOME}}"
NDK="${ANDROID_NDK_HOME:-$(find "$SDK/ndk" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)}"
TOOLS="$NDK/toolchains/llvm/prebuilt/$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64/bin"
[ -x "$TOOLS/llvm-readelf" ] || TOOLS="$(dirname "$(find "$NDK/toolchains/llvm/prebuilt" -name llvm-readelf | head -1)")"
```

Then, for example on arm64-v8a release output:

```sh
B=app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/arm64-v8a
"$TOOLS/llvm-readelf" -d "$B/liblibrtlsdr.so" | grep -E 'NEEDED|SONAME'
"$TOOLS/llvm-nm" -D "$B/liblibusb.so" | grep -E 'libusb_(wrap_sys_device|init_context|open2|get_device2)$'
"$TOOLS/llvm-readelf" -l "$B/liblibusb.so" | grep -A1 LOAD    # LOAD alignment should be 0x4000
unzip -l app/build/outputs/apk/release/*.apk | grep 'lib/arm64-v8a/'
```

Expected current state: `liblibrtlsdr.so` needs `liblibusb.so`; `liblibusb.so` exports
`libusb_wrap_sys_device` and `libusb_init_context`; `libusb_open2`/`libusb_get_device2` are gone;
shared objects are 16 KB page aligned.

## libusb provenance and crash-fix gates

The vendored libusb and its build notes are documented in
`rtlsdr/src/main/cpp/librtlsdr/libusb/UPSTREAM.md`. On the 1.0.30 upgrade branch, verify the two
disconnect fixes are present:

```sh
grep -n 'num_retired == tpriv->num_urbs' rtlsdr/src/main/cpp/librtlsdr/libusb/libusb/os/linux_usbfs.c
grep -n 'num_retired == num_urbs'         rtlsdr/src/main/cpp/librtlsdr/libusb/libusb/os/linux_usbfs.c
grep -n 'usbi_handle_disconnect(ctx, handle)' rtlsdr/src/main/cpp/librtlsdr/libusb/libusb/os/linux_usbfs.c
```

Remember: `libusb_wrap_sys_device()` does not own the fd. The Java layer must keep the
`UsbDeviceConnection` alive for the session and close it after the native call returns, before
notifying listeners that the session ended.

## Build notes

- Use Android Studio's bundled JBR (or JDK 17); the system default may be JDK 8.
- AGP resolves the NDK automatically; both Debug and RelWithDebInfo native builds are produced for
  all four ABIs.
- Release APKs built locally are unsigned unless the signing environment variables are provided;
  they are useful for symbol/ABI checks but cannot update a Play-signed install.

## Cleanup

```sh
adb shell am force-stop marto.rtl_tcp_andro
adb shell rm -f /sdcard/rtl_capture*.bin /data/local/tmp/cmds.bin /data/local/tmp/exit.bin
```

Restore a release-signed install with `adb install-multiple <base.apk> <split_config...apk>` if you
replaced it with a debug build.
