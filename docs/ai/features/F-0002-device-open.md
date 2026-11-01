---
title: F-0002 Device Open
audience: engineer
scope: Public feature contract for F-0002
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0001
- C-0002
- C-0003
- C-0004
- C-0005
- C-0006
- C-0007
- C-0008
- C-0009
- C-0010
- C-0011
- C-0012
- C-0013
- C-0014
- C-0015
---

# F-0002 — Device Open

## Outcome

Activity `com.sdrtouch.rtlsdr.DeviceOpenActivity` declared in app/src/main/AndroidManifest.xml.

## Trigger and conditions

- **Trigger:** Activity launch via android.intent.action.VIEW
- **Condition:** Exported component (activity); another app can invoke it.

## User-visible behaviour

- The feature's entry point is the exported activity com.sdrtouch.rtlsdr.DeviceOpenActivity, which declares an intent filter for android.intent.action.VIEW with category DEFAULT and android:scheme="iqsrc", and uses the platform dialog theme. Any app on the device can request opening an SDR device without a UI of its own.
- The manifest lists external SDR client packages under <queries> (e.g. eu.ebctech.dump1090, marto.androsdr2, com.mantz_it.rfanalyzer), consistent with the activity being a publicly callable device-opening endpoint. The app is designed to be invoked by third-party SDR applications.
- onCreate parses the intent URI into SdrTcpArguments by stripping the "iqsrc://" prefix, optionally reads a UsbDevice from UsbManager.EXTRA_DEVICE, and aborts with EXIT_WRONG_ARGS if parsing throws IllegalArgumentException. A malformed iqsrc URI yields a canceled result carrying an error id instead of a crash.
- onCreate first rejects unsupported platforms: when RtlSdrApplication.IS_PLATFORM_SUPPORTED is false the activity immediately calls finishWithError(new SdrException(EXIT_PLATFORM_NOT_SUPPORTED)). Unsupported devices/variants fail fast with a specific error code.
- When the intent carried UsbManager.EXTRA_DEVICE, onStart skips enumeration entirely and goes straight to startServer(new RtlSdrDevice(getApplicationContext(), usbDevice)). No device-selection dialog is shown on the USB-attach path.
- Without a USB device, onStart enumerates all SDR_DEVICE_PROVIDERS via listDevices(context,false) and branches on the total count: 0 -> EXIT_NO_DEVICES, exactly 1 -> auto-open that device, more than 1 -> show the device-selection dialog; any Throwable is routed to finishWithError. A single attached device opens with no user interaction; multiple candidates require a user choice.
- The device-selection dialog is a DeviceDialog DialogFragment added under fragment tag "dialog" via showDialog(), which removes any previous fragment with the same tag and adds the transaction to the back stack. Only one selection dialog is present at a time; dialog failures are silently printed.
- Dialog outcomes: choosing a device calls startServer(selected); canceling the dialog terminates the activity with a generic access error (SdrException.ERROR_ACCESS). Back/cancel behaves like a permission-style failure toward the calling app.
- startServer is idempotent per activity instance: it takes a lock, sets sessionStartRequested, returns early on a repeated call, then requests the notification permission, creates SdrServiceConnection with a finishWithError callback, registers a device status listener, and starts+binds BinaryRunnerService (startForegroundService on API 26+). Repeated onStart invocations for the same device no longer open a second session (LIBUSB_ERROR_BUSY avoided) — per the code comment.
- sessionStartRequested is never reset anywhere in DeviceOpenActivity, while onStop clears usbDevice and mConnection; therefore after a stop/start cycle of the same activity instance a subsequent startServer would be ignored. A second onStart on the same instance (e.g. after the USB permission dialog following an onStop) can silently do nothing, leaving the caller waiting or receiving no result.
- POST_NOTIFICATIONS is requested at most once (API 33+) and only when not already granted; per the in-code comment, denial does not prevent the stream from starting, it only suppresses the notification. Open succeeds regardless of the user's permission answer.
- Success is reported only on the device status callback: onDeviceStatusListener.onOpen calls finishWithSuccess(sdrDevice), which sets RESULT_OK with EXTRA_SUPPORTED_TCP_CMDS (from sdrDevice.getSupportedCommands()) and EXTRA_DEVICE_NAME, then finishes the activity. The calling app learns the device name and the TCP command set it may use.

## Code start points

- `app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java` — `com.sdrtouch.rtlsdr.DeviceOpenActivity#onCreate(Bundle)`
- `app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java` — `com.sdrtouch.rtlsdr.DeviceOpenActivity#onStart()`
- `app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java` — `com.sdrtouch.rtlsdr.DeviceOpenActivity#showDeviceSelectionDialog(List<SdrDevice>)`
- `app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java` — `com.sdrtouch.rtlsdr.DeviceOpenActivity#showDialog(DialogFragment)`

## Evidence

[`C-0001`](EVIDENCE.md), [`C-0002`](EVIDENCE.md), [`C-0003`](EVIDENCE.md), [`C-0004`](EVIDENCE.md), [`C-0005`](EVIDENCE.md), [`C-0006`](EVIDENCE.md), [`C-0007`](EVIDENCE.md), [`C-0008`](EVIDENCE.md), [`C-0009`](EVIDENCE.md), [`C-0010`](EVIDENCE.md), [`C-0011`](EVIDENCE.md), [`C-0012`](EVIDENCE.md), [`C-0013`](EVIDENCE.md), [`C-0014`](EVIDENCE.md), [`C-0015`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
