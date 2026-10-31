# Changelog

## 3.2 — 2026-10-31

**TL;DR:** We replaced the 2016 libusb fork with upstream **libusb 1.0.30** and hardened how the
driver starts, streams, and tears down. This fixes the native crash on USB disconnect and removes
the teardown stalls and session races around it.

### USB stack: libusb 1.0.30

We replaced the vendored libusb 1.0.20 snapshot with upstream 1.0.30
([provenance and build notes](rtlsdr/src/main/cpp/librtlsdr/libusb/UPSTREAM.md)). The old snapshot
predates the upstream fixes for transfers that the kernel reaps out of submission order when a
device disappears.

The upgrade brings two fixes for that failure mode:

- [9eda802d](https://github.com/libusb/libusb/commit/9eda802d947d9c4212eb3f821fa51956029dade0)
  waits until every **URB** (USB request block — one kernel-level USB transfer) has been reaped
  before freeing a transfer.
- [00e36c8](https://github.com/libusb/libusb/commit/00e36c829fe87a3d285d7a774eb8a1ca8797d0c0)
  reaps what is ready and then always reports the disconnect after `POLLERR`.

Together they remove the `usbi_handle_transfer_completion` / `list_del` crash that appeared when a
dongle was unplugged mid-stream.

Android now uses the upstream file-descriptor flow: `libusb_init_context()` with
`LIBUSB_OPTION_NO_DEVICE_DISCOVERY`, then `libusb_wrap_sys_device()`. The fork-only
`libusb_open2()` / `libusb_get_device2()` path is gone.

`libusb_wrap_sys_device()` does not close a wrapped fd, so the Java layer now owns the
`UsbDeviceConnection` and closes it after the native session returns. The fd stays valid for the
whole session, and the next session starts only after it is released — closing the connection while
libusb is still using it would invalidate the stream.

### Driver life cycle

- **TCP teardown is synchronous.** The server thread is joined before `rtlsdr_close()`. Previously
  the finalizer could free the native session while the TCP threads were still winding down.
- **Cancellation waits for every transfer.** After a cancel, the driver waits for each submitted
  transfer to deliver its completion before freeing it. `libusb_cancel_transfer()` returning
  `LIBUSB_ERROR_NOT_FOUND` does not prove the transfer is out of libusb's hands. The wait is
  bounded; on timeout the driver leaks the transfers instead of freeing memory libusb may still
  own.
- **The data path no longer blocks.** `sdrtcp_feed()` runs on the libusb receive thread and could
  sleep or wait on the work pool. A stalled client therefore delayed disconnect handling by up to
  ~15 s in our tests. It now drops buffers when no client can keep up.
- **Teardown is milliseconds.** Client sockets are non-blocking, and per-session self-pipes wake
  the `select()` loops. Client-close and exit-command teardown dropped from ~1 s to ~2 ms in
  testing.
- **Device-detached errors are distinguishable.** `LIBUSB_ERROR_NO_DEVICE` and
  `LIBUSB_ERROR_NOT_FOUND` now surface as a device-detached `SdrException`. Previously an unplug
  surfaced as `EXIT_UNKNOWN`, the same code as an actual driver failure.
- **Only one session runs at a time.** The activity, service binder, and `onClosed` paths now share
  one session guard. A second request replaces the queued session instead of opening the dongle
  twice; closing during open aborts the open. This removes the duplicate `onStart` from the USB
  permission dialog that produced `LIBUSB_ERROR_BUSY`.
- **Native memory is released deterministically.** Teardown runs after `announceOnClosed()`, with
  the finalizer kept only as a fallback; a single-use guard rejects reopening a session object.

### Validation

We tested with an NESDR Nano 3 on:

- Pixel 11 Pro XL, Android 17 (API 37).
- Huawei P20, Android 9 (API 28, EMUI 9), including SDR Touch 2.75.

Covered: streaming and tuning commands with SDR Touch and raw TCP clients; unplug/replug while
streaming (including replug auto-start); stalled clients; duplicate-session bursts; repeated
start/stop churn. Teardown completed in milliseconds on both devices, and the crash buffer stayed
empty.

### Known limitations

- [ ] The multi-URB path is not validated on hardware yet. Both test devices advertise
  `USBFS_CAP_BULK_SCATTER_GATHER`, so a 256 KiB transfer uses one URB (`num_urbs == 1`). Host
  controllers without scatter-gather split it into 16 URBs — the configuration that produced the
  original crash. The upstream fixes cover it, but we need USB-2-only hardware to confirm.
- [ ] On Huawei/EMUI devices the OS may freeze the app when the screen is off, even with a
  foreground service. Keep the screen on while streaming; this is EMUI power management, not a
  driver bug.
