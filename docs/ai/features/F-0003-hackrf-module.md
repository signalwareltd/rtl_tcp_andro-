---
title: F-0003 HackRF USB driver module (:hackrf)
audience: engineer
scope: Public feature contract for F-0003
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0083
- C-0084
- C-0085
- C-0086
- C-0087
- C-0088
- C-0089
- C-0090
- C-0091
- C-0092
- C-0093
---

# F-0003 — HackRF USB driver module (:hackrf)

## Outcome

Android library module wrapping HackRF One/Jawbreaker SDR hardware: USB permission handling, transceiver mode switching (OFF/RECEIVE/TRANSMIT), vendor-request configuration (MAX2837, SI5351C, ...) and a Runnable-based receive/transmit loop, exposed to :app through SdrDeviceProvider and HackrfCallbackInterface.

## Trigger and conditions

- **Trigger:** USB device attach or already-granted permission for a HackRF USB device, requested by the app via Hackrf.initHackrf(...)
- **Condition:** UsbManager system service available
- **Condition:** USB permission granted for the target UsbDevice (already held or granted via the permission broadcast)
- **Condition:** device matches the HackRF USB vendor/product IDs used by the app device filter

## User-visible behaviour

- :hackrf is a separate Android library module (com.android.library plugin, namespace info.martinmarinov.hackrf, minSdk 24) and :app depends on it through a Gradle project dependency. Any rebuild of the HackRF feature must preserve the module boundary and the exported packages com.mantz_it.hackrf_android and com.sdrtouch.rtlsdr.hackrf.
- The app discovers HackRF hardware through the SdrDeviceProvider abstraction: SdrDeviceProviderRegistry imports and instantiates com.sdrtouch.rtlsdr.hackrf.HackRfDeviceProvider, which declares implements SdrDeviceProvider. The HackRF feature is reachable from the app device-selection surface without a hard-coded branch on device type.
- Hackrf.initHackrf is the trigger entry point: it resolves UsbManager, returns false when that service is missing, and branches on usbManager.hasPermission(device). Initialization is asynchronous whenever permission is not already held; the caller learns the result only through the callback interface.
- When USB permission is not held, initHackrf registers a BroadcastReceiver for the HACKRF_USB_PERMISSION action and fires usbManager.requestPermission(...) via a PendingIntent; on Android 12+ (Build.VERSION_CODES.S) the PendingIntent is created with FLAG_MUTABLE. On API 31+ the mutable flag is required for the device/permission extras to be delivered; on older APIs flags stay 0.
- The permission result is handled in the dynamic receiver: on grant it constructs new Hackrf(usbManager, device, queueSize), shows a Toast and calls callbackInterface.onHackrfReady(hackrf); on deny it shows a Toast and calls callbackInterface.onHackrfError("Permission denied for device ..."). The receiver unregisters itself at the end of onReceive. The observable outcome of the trigger is either a fully constructed driver object or an error string surfaced to the app.
- Failure handling is asymmetric: in the permission-already-held branch a HackrfUsbException calls onHackrfError and returns false, while in the permission-granted branch the same exception calls onHackrfError but control falls through to the shared return true at the end of initHackrf. A caller checking the boolean sees false for open failures only when permission was already held; after a permission grant the boolean can be true even though the device failed to open, with the error arriving via onHackrfError.
- The driver models transceiver state with an int field initialised to HACKRF_TRANSCEIVER_MODE_OFF and exposes constants OFF=0, RECEIVE=1, TRANSMIT=2; the class implements Runnable so transceive work runs on a thread. Mode transitions and cancellation must go through setTransceiverMode/startRX/startTX/stop rather than direct field writes, otherwise the worker thread can observe inconsistent state.
- Device configuration goes over raw USB vendor requests: the class declares a private opcode table mirroring libhackrf (SET_TRANSCEIVER_MODE=1, MAX2837_WRITE=2, MAX2837_READ=3, SI5351C_WRITE=4, SI5351C_READ=5) and comments the block as taken from hackrf.c. There is no Android service or IPC hop to the hardware: the app process owns the USB file descriptor and the device protocol is wire-compatible with libhackrf.
- The module is a modified fork: its Readme calls it a slightly modified version of Dennis Mantz's HackRF library for Android, itself a port of libhackrf; upstream URLs mossmann/hackrf (host/libhackrf) and demantz/hackrf_android are named, and the app credits string repeats the attribution. Upstream libhackrf semantics cannot be assumed for modified paths; the fork is the contract, and attribution must be preserved for licence compliance.
- The README advertises HackRF support as part of the rtl-tcp driver surface: the driver is compatible with rtl-tcp clients and adds commands so Android clients can control other SDR hardware such as SDRplay or HackRF, and invocation throws ActivityNotFoundException when the driver app is not installed. HackRF capability may be reachable both in-process (this module) and through the external driver app; the external path fails with ActivityNotFoundException that the app is expected to handle by prompting installation.
- The app declares HackRF USB identity filters for auto-start: vendor 0x1d50 with product 0x6089 (HackRF One) and vendor 0x1d50 with product 0x604b (HackRF Jawbreaker). A HackRF attach event can launch or notify the app so initHackrf can run without manual device enumeration.

## Code start points

- `hackrf/src/main/java/com/mantz_it/hackrf_android/Hackrf.java` — `com.mantz_it.hackrf_android.Hackrf`
- `hackrf/src/main/java/com/mantz_it/hackrf_android/Hackrf.java` — `com.mantz_it.hackrf_android.Hackrf.<init>`
- `hackrf/src/main/java/com/mantz_it/hackrf_android/Hackrf.java` — `com.mantz_it.hackrf_android.Hackrf.byteArrayToInt`
- `hackrf/src/main/java/com/mantz_it/hackrf_android/Hackrf.java` — `com.mantz_it.hackrf_android.Hackrf.byteArrayToLong`

## Evidence

[`C-0083`](EVIDENCE.md), [`C-0084`](EVIDENCE.md), [`C-0085`](EVIDENCE.md), [`C-0086`](EVIDENCE.md), [`C-0087`](EVIDENCE.md), [`C-0088`](EVIDENCE.md), [`C-0089`](EVIDENCE.md), [`C-0090`](EVIDENCE.md), [`C-0091`](EVIDENCE.md), [`C-0092`](EVIDENCE.md), [`C-0093`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
