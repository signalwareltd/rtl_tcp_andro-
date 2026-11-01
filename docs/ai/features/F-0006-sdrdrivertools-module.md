---
title: F-0006 Sdrdrivertools module
audience: engineer
scope: Public feature contract for F-0006
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0074
- C-0075
- C-0076
- C-0077
- C-0078
- C-0079
- C-0080
- C-0081
- C-0082
---

# F-0006 — Sdrdrivertools module

## Outcome

Supplied module `:sdrdrivertools` with 85 indexed declarations.

## Trigger and conditions

- **Trigger:** In-process module call or supplied library boundary

## User-visible behaviour

- `:sdrdrivertools` is an Android library module, not an application: build.gradle applies `com.android.library` with namespace `info.martinmarinov.sdrdrivertools`, compileSdk 37, minSdk 24, targetSdk 37. No applicationId of its own; the module cannot be installed and its behaviour is only observable through a consuming app/driver module.
- `:sdrdrivertools` is a shared build-time dependency of `:app`, `:rtlsdr` and `:hackrf` (`implementation project(path: ':sdrdrivertools')`). Any change to the shared types (e.g. SdrDevice, SdrTcpArguments, SdrException) is a cross-module binary/source contract for three modules; no consumer can be migrated alone.
- The module's public surface is the shared SDR driver toolkit: `com.sdrtouch.tools.ArgumentParser`, `com.sdrtouch.core.SdrTcpArguments`, `com.sdrtouch.core.devices.SdrDevice` (+ nested `OnStatusListener`), `com.sdrtouch.core.devices.SdrDeviceProvider`, `com.sdrtouch.core.exceptions.SdrException`, `com.sdrtouch.core.UsedByJni`. The module is a contract/utility layer rather than a feature screen: it defines the vocabulary (arguments, device, error) that driver modules implement.
- `ArgumentParser` turns a raw argument string into a key/value map with quote-aware splitting (`unquote`, `splitIgnoringQuotes`, `toMap`) and exposes typed accessors (`getStringArgument[OrDefault]`, `getIntArgument[OrDefault]`, `getLongArgument[OrDefault]`). Argument strings containing quoted values (e.g. escaped values in an `iqsrc://` URI or CLI-style options) are split correctly, which is a precondition for the TCP device parameters being well-formed.
- `SdrTcpArguments` is the typed value object for a network-attached SDR: it parses a raw argument string (`fromString`) via `ArgumentParser` and exposes gain, samplerateHz, frequencyHz, address, port, ppm and biasT, plus a `toString` form. Connection parameters for an SDR reached over TCP (host, port, centre frequency, sample rate, tuner gain, ppm correction, bias-tee) cross the module boundary as one immutable-ish object.
- `SdrDevice` defines an asynchronous device lifecycle: `openAsync(SdrTcpArguments)`, `close()`, `cancelPendingOpen()`, `addOnStatusListener(...)`, and protected notifications `announceOnOpen()` / `announceOnClosed(Throwable)`, with a nested `OnStatusListener` interface; `SdrDeviceProvider` is the abstraction that supplies devices. Consumers must treat device availability as eventually-known state with an explicit closed/failed callback carrying a `Throwable`, rather than a blocking open result.
- `SdrException` maps driver error codes to human-readable text and a numeric reason/id (`translateToString(int)`, `toReason(int)`, `getReason()`, `getId()`). Failure of a device open is reportable to the UI as a translated reason instead of a raw error code; the mapping table is the compatibility contract with the native code.
- `@UsedByJni` is a marker annotation declared in the module, indicating annotated members are invoked from native code. Any member carrying it must keep its name/signature when rebuilt, because native code binds by symbol.
- The module's Android manifest declares no permissions, no components and no queries, so it adds no exported or intent-reachable surface to the app. Triggers for this feature never originate in the module itself — they come from consumers (`:app` activities/services or the driver modules).

## Code start points

- `sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java` — `com.sdrtouch.core.SdrTcpArguments`
- `sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java` — `com.sdrtouch.core.SdrTcpArguments.<init>`
- `sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java` — `com.sdrtouch.core.SdrTcpArguments.fromString`
- `sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java` — `com.sdrtouch.core.SdrTcpArguments.getAddress`

## Evidence

[`C-0074`](EVIDENCE.md), [`C-0075`](EVIDENCE.md), [`C-0076`](EVIDENCE.md), [`C-0077`](EVIDENCE.md), [`C-0078`](EVIDENCE.md), [`C-0079`](EVIDENCE.md), [`C-0080`](EVIDENCE.md), [`C-0081`](EVIDENCE.md), [`C-0082`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
