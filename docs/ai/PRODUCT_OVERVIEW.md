---
title: Product overview
audience: end-user
scope: Product context and user-facing capabilities
status: complete
snapshot:
  app: 3e261f5a7576
---

# Product overview

SDR driver is an Android app that turns a supported USB software-defined-radio tuner into a radio that other apps can use. A radio app on the same phone asks the driver to open a tuner; the driver asks Android for permission to use the plugged-in hardware, opens it, starts a small radio server on the phone itself, and answers back with the tuner's name and the list of controls that particular tuner understands. The other app then receives the live radio signal over a local network connection and tunes the hardware while it listens. The driver is not a radio player by itself: in normal use it is a bridge between the hardware and a radio app. It can also be launched from the app icon, where a console screen shows what the driver is doing and lets the user start or stop the radio session by hand. A physical tuner is required for the main flow; without one, only the console screen and its messages are available.

## Who it is for

- SDR app user — the person who plugs the tuner into the phone, answers Android's permission questions, and can start or stop the driver from the app's own screen.
- SDR client app — a separate radio app that requests a tuner, receives the live signal and sends tuning controls.
- Android system — announces that a tuner was plugged in, asks the user to allow USB access, shows the driver's ongoing notification and manages power.
- Supported USB tuner — an RTL-SDR type dongle or a HackRF One / HackRF Jawbreaker radio.
- Other installed apps — apps that listen for the driver's announcements that a session has started or ended.

## What you can do

- **Open a tuner on behalf of a radio app.** The requesting app receives a success answer containing the tuner name and the supported command list, and can immediately connect to the driver's local address and port to receive the radio signal and control the tuner.
- **Choose which tuner to open.** The chosen tuner is opened and the chosen session proceeds.
- **Serve the radio signal to a connected client.** The client receives a continuous radio signal at the sample rate and centre frequency it set, and each accepted command changes the tuner accordingly.
- **Control the tuner from the client.** The tuner changes settings live and the connected client hears or decodes the new signal immediately.
- **Start and stop the driver from the console screen.** The radio session starts or stops on the user's command, and the control's on/off appearance always matches the driver's real state rather than the last tap.
- **Read and manage the driver log from the console screen.** The user can follow what the driver is doing, keep or share the log text, and read the help and licence information without leaving the screen.
- **React when a tuner is plugged in.** Plugging in a recognised tuner produces a log entry naming the device and a handoff announcement to whichever app claims it; the user sees no full-screen takeover.
- **Keep the session running in the background.** The radio keeps streaming while the user is in another app or the screen is otherwise idle, and the ongoing notification tells the user that the driver is active.
- **Announce session state to other installed apps.** An installed app that watches for these announcements can launch itself when a tuner session becomes available and stand down when it ends.
- **Use a HackRF radio.** A HackRF radio appears as an openable tuner alongside RTL-SDR dongles and is driven by the same client apps and the same connection protocol.

## Product rules

- Only one tuner session runs at a time. A new request while a session is running closes the current session and opens the new one instead of running both.
- At most one request can be waiting. A newer request replaces an older waiting request, so queued sessions never pile up.
- Success is reported only for a tuner that has actually opened, and it carries the tuner's name plus the list of commands that tuner supports.
- The usable set of commands depends on the attached hardware. A client must read the returned list and use only those commands; anything outside it is ignored rather than ending the session.
- Standard rtl_tcp clients work with the standard command set; the additional commands exist so clients can also drive non-RTL hardware such as HackRF through the same connection.
- Requests that cannot be served end with a specific reason the calling app can read — unsupported platform, malformed request, no tuner present, or access refused — instead of a crash.
- Allowing the driver to post notifications is optional. Refusing it only hides the ongoing notification; the radio session still starts and runs.
- While a session runs the driver stays in the foreground with an ongoing notification and keeps the device awake so streaming survives the app being in the background.
- Connection details travel with the open request: listening address, port, centre frequency, sample rate, tuner gain, frequency correction and bias tee. The requested centre frequency is applied before any client command is served.
- A client that wants the same gain control to work across hardware families should use the device-independent gain command that takes a single percentage.
- Only the recognisable USB tuner families the app knows about are handled; other USB devices are left alone.
- A client can end its session deliberately with a dedicated shutdown command, which stops the radio session on the driver side.
- When a session ends — whether the client closed it, the tuner was unplugged or an error occurred — the driver announces that the device has detached so listening apps can react.

## Sources

See [SOURCES.md](SOURCES.md) for the approved public sources used by this overview.
