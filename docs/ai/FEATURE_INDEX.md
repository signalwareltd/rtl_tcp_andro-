---
title: Feature index
audience: engineer
scope: Public feature catalogue
status: complete
snapshot:
  app: 3e261f5a7576
---

# Feature index

| ID | Feature | Category | Status | Trigger |
|---|---|---|---|---|
| F-0001 | [Binary Runner](features/F-0001-binary-runner.md) | background | implemented | Service start or binding |
| F-0002 | [Device Open](features/F-0002-device-open.md) | screen | implemented | Activity launch via android.intent.action.VIEW |
| F-0003 | [HackRF USB driver module (:hackrf)](features/F-0003-hackrf-module.md) | module | implemented | USB device attach or already-granted permission for a HackRF USB device, requested by the app via Hackrf.initHackrf(...) |
| F-0004 | [Protocol and command contract](features/F-0004-protocol-and-command-contract.md) | protocol | implemented | Client app intent or protocol command |
| F-0005 | [RTL-SDR driver module (rtl_tcp server over a USB tuner)](features/F-0005-rtlsdr-module.md) | module | implemented | Intent VIEW with scheme iqsrc:// handled by com.sdrtouch.rtlsdr.DeviceOpenActivity, or a USB_DEVICE_ATTACHED broadcast routed to com.sdrtouch.rtlsdr.UsbDelegate, or the launcher entry com.sdrtouch.rtlsdr.StreamActivity |
| F-0006 | [Sdrdrivertools module](features/F-0006-sdrdrivertools-module.md) | subsystem | implemented | In-process module call or supplied library boundary |
| F-0007 | [Stream](features/F-0007-stream.md) | screen | implemented | Activity launch via android.intent.action.MAIN |
| F-0008 | [Usb Delegate](features/F-0008-usb-delegate.md) | feature | conditional | Android broadcast of android.hardware.usb.action.USB_DEVICE_ATTACHED delivered as an Activity launch intent for a matching device |

## How to use this index

Start with `USER_MANUAL.md` for the product behaviour. Use a feature page for its engineering evidence and code start points.
