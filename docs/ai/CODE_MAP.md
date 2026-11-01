---
title: Code map
audience: engineer
scope: Feature to source starting points
status: complete
snapshot:
  app: 3e261f5a7576
---

# Code map

Path and symbol start points for a change. Line ranges are intentionally excluded.

| Feature | Name | Path | Symbol |
|---|---|---|---|
| F-0001 | Binary Runner | `app/src/main/java/com/sdrtouch/rtlsdr/SdrServiceConnection.java` | `SdrServiceConnection#onServiceConnected(ComponentName,IBinder)` |
| F-0002 | Device Open | `app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java` | `com.sdrtouch.rtlsdr.DeviceOpenActivity#onCreate(Bundle)` |
| F-0003 | HackRF USB driver module (:hackrf) | `hackrf/src/main/java/com/mantz_it/hackrf_android/Hackrf.java` | `com.mantz_it.hackrf_android.Hackrf` |
| F-0004 | Protocol and command contract | `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` | `com.sdrtouch.rtlsdr.BinaryRunnerService.onStartCommand` |
| F-0005 | RTL-SDR driver module (rtl_tcp server over a USB tuner) | `rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java` | `com.sdrtouch.rtlsdr.driver.RtlSdrDevice` |
| F-0006 | Sdrdrivertools module | `sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java` | `com.sdrtouch.core.SdrTcpArguments` |
| F-0007 | Stream | `app/src/main/java/com/sdrtouch/rtlsdr/StreamActivity.java` | `com.sdrtouch.rtlsdr.StreamActivity#onCreate(Bundle)` |
| F-0008 | Usb Delegate | `app/src/main/java/com/sdrtouch/rtlsdr/UsbDelegate.java` | `UsbDelegate#onCreate(Bundle)` |
