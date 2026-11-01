---
title: Architecture
audience: engineer
scope: Public modules and ownership boundaries
status: complete
snapshot:
  app: 3e261f5a7576
---

# Architecture

## Modules

| Module | Namespace / app ID | Build file |
|---|---|---|
| :app | marto.rtl_tcp_andro | app/build.gradle |
| :hackrf | info.martinmarinov.hackrf | hackrf/build.gradle |
| :rtlsdr | info.martinmarinov.rtlsdr | rtlsdr/build.gradle |
| :sdrdrivertools | info.martinmarinov.sdrdrivertools | sdrdrivertools/build.gradle |

## Capability areas

- **background.** F-0001 Binary Runner
- **feature.** F-0008 Usb Delegate
- **module.** F-0003 HackRF USB driver module (:hackrf); F-0005 RTL-SDR driver module (rtl_tcp server over a USB tuner)
- **protocol.** F-0004 Protocol and command contract
- **screen.** F-0002 Device Open; F-0007 Stream
- **subsystem.** F-0006 Sdrdrivertools module

## Ownership boundaries

- **User-facing state** belongs to the screens and interactions described in the user manual.
- **Background and service state** is owned by the component that keeps a user-visible operation alive.
- **External state** belongs to the device, service or calling app on the other side of a boundary.
- **Persistent state** is owned by the product's storage mechanism, not by a screen instance.

## Diagrams

See [architecture diagram](diagrams/architecture.md).

## Code map

See [CODE_MAP.md](CODE_MAP.md) for path-and-symbol start points.
