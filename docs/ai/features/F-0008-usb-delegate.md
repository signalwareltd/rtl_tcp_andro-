---
title: F-0008 Usb Delegate
audience: engineer
scope: Public feature contract for F-0008
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0016
- C-0017
- C-0018
- C-0019
- C-0020
- C-0021
- C-0022
- C-0023
- C-0044
- C-0045
- C-0046
- C-0047
---

# F-0008 — Usb Delegate

## Outcome

Translucent, exported Activity that intercepts USB_DEVICE_ATTACHED broadcasts for known RTL-SDR vendor/product IDs and re-dispatches them as a custom SDR_DEVICE_ATTACHED intent, then finishes immediately.

## Trigger and conditions

- **Trigger:** Android broadcast of android.hardware.usb.action.USB_DEVICE_ATTACHED delivered as an Activity launch intent for a matching device
- **Condition:** Attached device's vendor-id/product-id matches an entry in res/xml/autostart_device_filter.xml
- **Condition:** A component able to handle action com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED exists for the happy-path handoff (not confirmed in this snapshot)

## User-visible behaviour

- com.sdrtouch.rtlsdr.UsbDelegate is an exported Activity declared with android:theme="@android:style/Theme.Translucent.NoTitleBar" and an intent-filter for android.hardware.usb.action.USB_DEVICE_ATTACHED, plus meta-data pointing at @xml/autostart_device_filter. The feature is entirely externally triggered; there is no in-app entry point.
- The attach intent-filter is narrowed by a usb-device resource filter enumerating RTL2832-family vendor-id/product-id pairs (0x0bda:0x2832, 0x0bda:0x2838, 0x0413:0x6680, 0x0413:0x6f0f, 0x0458:0x707f among the first entries). Only listed dongles trigger the delegate; other USB devices do not.
- onCreate reads the launch Intent action and only acts when UsbManager.ACTION_USB_DEVICE_ATTACHED equals it; the action and outcome are logged via com.sdrtouch.tools.Log.appendLine. No other lifecycle callback participates; the feature is a single-shot onCreate handler.
- On the happy path the Activity extracts UsbManager.EXTRA_DEVICE, logs the device name, builds a new Intent with action BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED carrying EXTRA_DEVICE, logs the resolveActivity target, calls startActivity, then calls finish(). Observable outcome is limited to a log entry and a dispatched Intent; the delegate itself opens no USB device and shows no UI.
- The outbound intent is addressed by action string only (no setComponent/setPackage), and no intent-filter for com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED appears in the searched manifest snapshot; the constant is referenced only by its own definition and by UsbDelegate. In this snapshot the handoff most likely resolves to nothing, making the attach flow effectively a logged no-op unless an external app claims the action.
- Failure handling is twofold: a null EXTRA_DEVICE is logged and skipped, and a failed startActivity is swallowed by a catch of Throwable that logs 'no activity found for SDR handling'. The Activity finishes either way, so the failure is invisible to the user. No retry, no user-visible error, no fallback path; the dongle attach simply produces log output.
- Because the Activity is exported and translucent, any caller may start it with an arbitrary action; such launches hit the else-if branch, log 'unhandled action', and finish without side effects. Low-risk attack surface: the worst an external caller can do is trigger a log line, since the attach branch requires an extra it supplies itself.
- The delegate does not request USB permission, call openDevice, or claim the interface; it only forwards the UsbDevice descriptor. UsbDelegate cannot fail on USB I/O; all device-level errors belong to the downstream handler.
- The string com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED appears in the source tree only as the constant BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED, as its use in UsbDelegate (emit + log), and in README.md documentation; no manifest or intent-filter in the 'app' repo references it. Inside this build the emit has no consumer; the handoff succeeds only if some other installed app declares the action.
- The UsbDelegate activity is registered in the app manifest for exactly one trigger, android.hardware.usb.action.USB_DEVICE_ATTACHED, with meta-data pointing at @xml/autostart_device_filter; it declares no SDR_DEVICE_ATTACHED filter. Sending SDR_DEVICE_ATTACHED does not re-enter UsbDelegate (or any in-app component).
- The manifest contains only three intent-filters in total (DeviceOpenActivity iqsrc VIEW, UsbDelegate USB_DEVICE_ATTACHED, StreamActivity MAIN/LAUNCHER), and the sole service BinaryRunnerService is not exported and declares no filter. There is no in-build receiver or activity for com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED, so F-0008 has no internal consumer.
- UsbDelegate dispatches the attach intent with startActivity (implicit, no setPackage/setComponent) after logging resolveActivity, and catches Throwable so a missing handler degrades to a log line rather than a crash. User effect: with no matching app installed, plugging in a tuner logs 'no activity found for SDR handling' and nothing launches; the driver flow is unaffected.

## Code start points

- `app/src/main/java/com/sdrtouch/rtlsdr/UsbDelegate.java` — `UsbDelegate#onCreate(Bundle)`
- `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` — `BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED`
- `app/src/main/java/com/sdrtouch/rtlsdr/UsbDelegate.java` — `com.sdrtouch.rtlsdr.UsbDelegate#onCreate(Bundle)`
- `app/src/main/AndroidManifest.xml` — ``

## Evidence

[`C-0016`](EVIDENCE.md), [`C-0017`](EVIDENCE.md), [`C-0018`](EVIDENCE.md), [`C-0019`](EVIDENCE.md), [`C-0020`](EVIDENCE.md), [`C-0021`](EVIDENCE.md), [`C-0022`](EVIDENCE.md), [`C-0023`](EVIDENCE.md), [`C-0044`](EVIDENCE.md), [`C-0045`](EVIDENCE.md), [`C-0046`](EVIDENCE.md), [`C-0047`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
