---
title: F-0005 RTL-SDR driver module (rtl_tcp server over a USB tuner)
audience: engineer
scope: Public feature contract for F-0005
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0094
- C-0095
- C-0096
- C-0097
- C-0098
- C-0099
- C-0100
- C-0101
- C-0102
- C-0103
- C-0104
- C-0105
- C-0106
---

# F-0005 — RTL-SDR driver module (rtl_tcp server over a USB tuner)

## Outcome

Android library module `:rtlsdr` that discovers supported SDR hardware, opens it through a JNI librtlsdr/libusb bridge (RtlSdrDevice.openAsync), and exposes the resulting IQ stream to client apps over the rtl_tcp TCP protocol, managed by a mediaPlayback foreground service.

## Trigger and conditions

- **Trigger:** Intent VIEW with scheme iqsrc:// handled by com.sdrtouch.rtlsdr.DeviceOpenActivity, or a USB_DEVICE_ATTACHED broadcast routed to com.sdrtouch.rtlsdr.UsbDelegate, or the launcher entry com.sdrtouch.rtlsdr.StreamActivity
- **Condition:** RtlSdrApplication.IS_PLATFORM_SUPPORTED must be true
- **Condition:** A supported USB SDR device must be present or enumerable by a registered SdrDeviceProvider
- **Condition:** FOREGROUND_SERVICE_MEDIA_PLAYBACK and POST_NOTIFICATIONS permissions for the foreground service and its notification

## User-visible behaviour

- `:rtlsdr` is an Android library module (com.android.library) with namespace info.martinmarinov.rtlsdr, minSdk 24 and compileSdk/targetSdk 37, compiling native code through CMake at src/main/cpp/CMakeLists.txt; `:app` consumes it via project(':rtlsdr') while `:rtlsdr` itself depends on ':sdrdrivertools'. Any change to the module affects both the driver app and every client that speaks rtl_tcp to it.
- The module's native stack is a vendored librtlsdr with libusb under rtlsdr/src/main/cpp/librtlsdr/libusb (provenance/build notes in UPSTREAM.md), and the repository changelog records that TCP teardown became synchronous, joining the server thread before rtlsdr_close(). Teardown ordering is a compatibility constraint: the device may only be closed after the server thread is joined.
- The cross-app trigger is an exported activity com.sdrtouch.rtlsdr.DeviceOpenActivity with an android.intent.action.VIEW / DEFAULT filter on scheme iqsrc; the same manifest exports UsbDelegate for android.hardware.usb.action.USB_DEVICE_ATTACHED, declares the launcher StreamActivity, and declares a non-exported BinaryRunnerService with foregroundServiceType mediaPlayback under application RtlSdrApplication. A third-party SDR client can start the driver purely by URI without binding to any service itself.
- DeviceOpenActivity.onCreate refuses to run on unsupported platforms (finishing with SdrException.EXIT_PLATFORM_NOT_SUPPORTED) before touching the UI, then parses the incoming iqsrc:// URI into SdrTcpArguments and finishes with EXIT_WRONG_ARGS on IllegalArgumentException; a UsbManager.EXTRA_DEVICE extra, if present, is retained as the preselected device. Malformed or unsupported requests fail fast and are reported through the driver error protocol instead of opening a half-initialised session.
- Device availability is resolved in onStart: with EXTRA_DEVICE present the activity starts a session directly on a new RtlSdrDevice; otherwise it asks every registered SdrDeviceProvider for devices and then fails with EXIT_NO_DEVICES (0), auto-starts the single device (1), or shows a selection dialog (>1). Zero-detection and ambiguous-detection are the two user-visible branches before any hardware access.
- startServer serialises session creation: under sessionStartLock a sessionStartRequested flag makes duplicate start requests return early, because onStart() runs again when the USB permission dialog closes and a second openAsync would fail with LIBUSB_ERROR_BUSY; the accepted call requests the notification permission, builds an SdrServiceConnection, registers a status listener, and starts plus binds BinaryRunnerService (startForegroundService on O+, bindService with BIND_AUTO_CREATE). Two concurrent opens of the same USB handle are prevented; a duplicate request is silently dropped rather than reported to the caller.
- BinaryRunnerService.startWithDevice() is synchronized and refuses to begin a new session while sessionActive is true; when the work queue is empty it announces not-running, clears status callbacks and stops itself, otherwise it marks the session active, announces it, attaches the status listener, calls sdrDevice.openAsync(sdrTcpArguments), and posts startForeground() to the main thread only if the same session is still current. Library-open exclusion is enforced in Java before libusb would reject it, and Service lifecycle calls stay on the main thread.
- While a session runs the service is a foreground service: startForeground() creates notification channel id "rtl_sdr" with IMPORTANCE_HIGH and calls startForeground(ONGOING_NOTIFICATION_ID, ..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) on API Q+ (plain two-argument call below Q), matching the manifest's foregroundServiceType mediaPlayback. The driver keeps streaming in the background; without POST_NOTIFICATIONS the notification may be suppressed on newer Android versions.
- The Java-to-native boundary is com.sdrtouch.rtlsdr.driver.RtlSdrDevice: openAsync(SdrTcpArguments) (implemented with a run() body) starts the driver, close()/dispose() tear it down, and the class declares the native methods initialize(), deInit(long), close(long) and openAsync(long,int,int,long,long,int,int,int,String,String) that reach the vendored librtlsdr/libusb code. All hardware state and the TCP listener live below this boundary; failures here surface as SdrException exit codes forwarded to the calling app.
- Device enumeration is provider-based: RtlSdrDeviceProvider.listDevices(Context, boolean) returns the openable devices, and loadNativeLibraries() loads the module's shared objects before hardware access. Supporting another SDR family means adding a provider to the registry rather than editing the driver activity.
- The module announces driver lifecycle to other apps with the public action strings com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED and com.sdrtouch.rtlsdr.SDR_DEVICE_DETACHED, declared as constants on BinaryRunnerService and documented in the README as a way to auto-launch a client app. A string-based contract exists outside the app process; changing these strings breaks clients.
- The :app manifest declares package marto.rtl_tcp_andro with INTERNET, WAKE_LOCK, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK and POST_NOTIFICATIONS permissions, plus <queries> entries for known client packages (for example marto.androsdr2, com.mantz_it.rfanalyzer, io.welle.welle, eu.ebctech.dump1090), showing that rtl_tcp interoperability with external apps is part of the contract. Third-party clients can be detected and launched, and streaming can continue with a wake lock in the background.

## Code start points

- `rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java` — `com.sdrtouch.rtlsdr.driver.RtlSdrDevice`
- `rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java` — `com.sdrtouch.rtlsdr.driver.RtlSdrDevice.<init>`
- `rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java` — `com.sdrtouch.rtlsdr.driver.RtlSdrDevice.close`
- `rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java` — `com.sdrtouch.rtlsdr.driver.RtlSdrDevice.deInit`

## Evidence

[`C-0094`](EVIDENCE.md), [`C-0095`](EVIDENCE.md), [`C-0096`](EVIDENCE.md), [`C-0097`](EVIDENCE.md), [`C-0098`](EVIDENCE.md), [`C-0099`](EVIDENCE.md), [`C-0100`](EVIDENCE.md), [`C-0101`](EVIDENCE.md), [`C-0102`](EVIDENCE.md), [`C-0103`](EVIDENCE.md), [`C-0104`](EVIDENCE.md), [`C-0105`](EVIDENCE.md), [`C-0106`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
