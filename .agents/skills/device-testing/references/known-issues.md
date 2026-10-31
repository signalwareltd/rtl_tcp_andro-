# Known issues when device-testing the driver

Observed on driver builds around version 3.16 during the libusb 1.0.30 upgrade (branch
`libusb-1.0.30-upgrade`, not merged into `master` at the time of writing). Some are fixed on that
branch; the rest are app-side work items.

## 1. Duplicate session after the USB permission dialog

Symptom: on first run, after the system permission dialog closes, two sessions start; one fails
with `usb_claim_interface error -6` (`LIBUSB_ERROR_BUSY`) and `Closed service due to exception`.

Cause: the activity's `onStart` runs again when the dialog closes and starts another session.

Workaround: grant permission, force-stop, relaunch. Confirm only one `Listening on ...` line.

## 2. Command listener exits immediately

Symptom: a client connects, but commands sent right after connect are ignored; the log shows
`Command listener thread exiting` almost immediately after `TCP server successfully started`.

Cause: the command thread is created before the state is set to `STAGE_CLIENT_SERVING`, so it can
observe the old state and exit.

Workaround: delay commands ~1 s after connect; SDR Touch naturally sends them later. Reconnect if
a command is missed.

## 3. Client EOF spins the command listener

Symptom: after an abrupt client disconnect the command thread may not exit cleanly and can burn CPU.

Cause: `recv() == 0` (EOF) is not handled, so the read loop never completes its 5-byte command.

Workaround: force-stop the app between test phases; do not rely on many reconnects in one session.

## 4. `client_socket` leak, `CLOSE_WAIT`, and a wedged listener

Symptom: `/proc/net/tcp` shows `127.0.0.1:<port>` in `CLOSE_WAIT` owned by the app after a client
leaves; new connections can stick in `SYN_SENT` because `listen(..., 1)` has a full accept queue.

Cause: `sdrtcp_cleanup()` never closes the client socket.

Workaround: force-stop the app and relaunch. If the listener exists but nothing connects, this is
the first thing to check.

## 5. 500 ms sleep in `sdrtcp_feed()` when no client

Symptom: with no TCP client, unplug teardown takes up to ~7 s (15 transfers x 500 ms) and the libusb
event thread is blocked inside the app callback.

Cause: `sdrtcp_feed()` sleeps instead of dropping buffers when no client is consuming data.

Workaround: treat up to ~7 s as expected on current builds; use an active client for the fast path.

## 6. Misleading error reporting

Symptom: logs show `rtlsdr_read_async failed: -5` followed by `rtlsdr_read_async finished
successfully`; Java may report `SdrException: Unknown error!`.

Cause: the success log is unconditional and libusb error codes are not mapped to a
device-detached `SdrException`.

Workaround: grep for the `failed:` line; `-5`/`NO_DEVICE` on unplug is expected.

## 7. Disconnect hang with queued data completions (fixed on the upgrade branch)

Symptom (older builds): unplug with no active client; the deleted usbfs fd stays open, the TCP
listener stays bound, no teardown logs appear, and the read thread sits in `ppoll` with `nfds=1`.

Cause: data completions were reaped at unplug, their resubmission failed, and the return value was
ignored. Nothing was left in flight for `usbi_handle_disconnect()`, so `dev_lost` was never set.

Fix: the upgrade branch checks the resubmission result and cancels the async read on failure.

Workaround on older builds: force-stop after unplug; do not leave the session half-alive.

## 8. Replug auto-start is not guaranteed

Symptom: after replug no new session starts, even though `UsbDelegate` normally auto-starts one.
The manual `iqsrc://` intent still works.

Cause: the attach event can arrive while the previous session is still tearing down.

Workaround: relaunch the intent manually and verify only one session is running.

## 9. Original libusb completion crash (fixed by the 1.0.30 upgrade)

Symptom (older builds): native crash in `usbi_handle_transfer_completion` / `list_del` during
unplug.

Fixed by the bundled libusb 1.0.30 upgrade (upstream `9eda802d`, `00e36c8`).

Caveat: devices that advertise `BULK_SCATTER_GATHER` use one URB per 256 KiB transfer, so the
multi-URB out-of-order path (and the original crash) may not reproduce on them. Include
USB-2-only devices in the matrix and log the usbfs capabilities when validating this fix.

## 10. `adb forward` can hang over wireless ADB

Symptom: `adb forward tcp:1234 tcp:1234` blocks, or `adb forward --list` stays empty.

Workaround: run the client on the device with toybox `nc`, as shown in the skill. Do not gate a test
run on `adb forward`.

## 11. Debug APK cannot update a release-signed install

Symptom: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

Cause: the installed build is signed with the release key, which is only available to CI.

Workaround: back up the installed splits, uninstall, install the debug APK. Restore later with
`adb install-multiple`.

## 12. Native lifetime races (known, not always reproducible)

- `sdrtcp_stop_serving_client()` is asynchronous; the finalizer that frees the native session struct
  can run while TCP threads are still winding down. Avoid rapid session churn and force-stop
  between phases.
- `dev->rtl_dev` is read by TCP threads while the session is tearing down. Avoid sending commands
  exactly at close time.
