---
title: F-0004 Protocol and command contract
audience: engineer
scope: Public feature contract for F-0004
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0062
- C-0063
- C-0064
- C-0065
- C-0066
- C-0067
- C-0068
- C-0069
- C-0070
- C-0071
- C-0072
- C-0073
---

# F-0004 — Protocol and command contract

## Outcome

The intent, argument and command contract exposed to callers.

## Trigger and conditions

- **Trigger:** Client app intent or protocol command

## User-visible behaviour

- The driver's protocol is an extension of rtl-tcp: it is claimed compatible with any rtl-tcp capable client, plus additional commands letting Android clients control non-RTL hardware (e.g. SDRplay, HackRF) over the same API. Any third-party rtl-tcp client can drive the device using the standard subset; extended features require the added opcodes.
- A single Java enum TcpCommand defines the wire command code table shared by the Java-side transports, including standard rtl-tcp codes 0x01-0x0d, Android extension codes 0x7e-0x80, and SDRplay RSP codes 0x1f-0x25. The opcode space is partitioned into standard rtl-tcp, Android extensions (0x7e-0x80) and SDRplay RSP (0x1f-0x25); clients must use these exact byte values.
- Command codes are decoded through a static SparseArray index built at class initialization; a duplicate numeric code is a hard failure (RuntimeException at class load), and an unknown code decodes to null. The code table cannot silently drift at build time, but a peer sending an unassigned code yields a null TcpCommand that downstream code must handle.
- The C/C++ native path defines an equivalent command enumeration in tcp_commands.h, with comments binding each opcode to its librtlsdr counterpart (e.g. TCP_SET_FREQ = 0x01 -> rtlsdr_set_center_freq, TCP_SET_FREQ_CORRECTION = 0x05 -> rtlsdr_set_freq_correction). The opcode numbering is duplicated across the Java and C implementations, making them a cross-language contract that must stay in sync.
- Each transport advertises a separate supported-command subset; the HackRF transport supports only 8 commands (TCP_SET_FREQ, TCP_SET_SAMPLE_RATE, TCP_ANDROID_EXIT, TCP_ANDROID_GAIN_BY_PERCENTAGE, TCP_SET_IF_TUNER_GAIN, TCP_SET_GAIN, TCP_SET_AGC_MODE, TCP_SET_GAIN_MODE). The same wire protocol behaves differently depending on attached hardware; unsupported requests (e.g. TCP_SET_FREQ_CORRECTION on HackRF) are not honoured.
- On the HackRF transport, opcodes are re-mapped onto HackRF gain semantics rather than being applied literally: TCP_SET_IF_TUNER_GAIN remaps the RTL gain value to a percentage and scales it to 0..40 for RxLNA, TCP_SET_GAIN scales to 0..62 for RxVGA, and both TCP_SET_AGC_MODE and TCP_SET_GAIN_MODE fall back to fixed default gains (LNA 30, VGA 32) because HackRF has no automatic gain mode. A client written against RTL gain semantics gets approximate, clamped gain control on HackRF, and AGC requests are silently converted to fixed gains rather than reported as unsupported.
- TCP_ANDROID_GAIN_BY_PERCENTAGE is the device-agnostic gain opcode: a single percentage argument is independently scaled to the HackRF LNA (0..40) and VGA (0..62) gain ranges. Clients should prefer 0x7f over 0x04/0x06 for portable gain control across hardware.
- TCP_ANDROID_EXIT (0x7e) is the cooperative shutdown opcode: it sets the transport's canceled flag and interrupts the current reader thread, ending the session. A client can terminate the server remotely; abrupt client disconnect is a different path (unknown).
- Commands received but absent from an implementation's switch fall through to a default branch that logs 'Unsupported command <cmd>' instead of throwing, so an out-of-subset opcode does not tear down the session. Protocol errors are silent to the peer; the client learns of failure only by observing that device state did not change.
- Per-connection framing on the HackRF transport is a 1-byte opcode plus a long argument read from a DataInputStream, dispatched by the inner Runnable HackRfTcp.HardwareCommand, which decodes via TcpCommand.fromCode. The protocol is stateful per TCP connection and ordered by construction (one command read then executed per iteration).
- Ordering at connection start is fixed: the server binds to the configured address/port and applies the requested initial frequency from SdrTcpArguments via the same TCP_SET_FREQ command path before serving client commands. Initial state is applied atomically at start; client commands then mutate it in arrival order.
- On devices where multiple drivers can handle the same protocol, the Android intent chooser may bind the rtl-tcp/iqsrc intent to one driver permanently if the user selects 'always use this app'. A user who chooses 'always' can find that only one of several installed SDR drivers is ever launched, which looks like a protocol regression but is chooser state.

## Code start points

- `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` — `com.sdrtouch.rtlsdr.BinaryRunnerService.onStartCommand`
- `app/src/test/java/com/sdrtouch/tools/ArgumentParserTest.java` — `com.sdrtouch.tools.ArgumentParserTest`
- `hackrf/src/main/java/com/sdrtouch/rtlsdr/hackrf/HackRfSdrDevice.java` — `com.sdrtouch.rtlsdr.hackrf.HackRfSdrDevice.getSupportedCommands`
- `hackrf/src/main/java/com/sdrtouch/rtlsdr/hackrf/HackRfTcp.java` — `com.sdrtouch.rtlsdr.hackrf.HackRfTcp`

## Evidence

[`C-0062`](EVIDENCE.md), [`C-0063`](EVIDENCE.md), [`C-0064`](EVIDENCE.md), [`C-0065`](EVIDENCE.md), [`C-0066`](EVIDENCE.md), [`C-0067`](EVIDENCE.md), [`C-0068`](EVIDENCE.md), [`C-0069`](EVIDENCE.md), [`C-0070`](EVIDENCE.md), [`C-0071`](EVIDENCE.md), [`C-0072`](EVIDENCE.md), [`C-0073`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
