---
title: User manual and product behaviour specification
audience: end-user
scope: User-visible behaviour and rebuild outcomes
status: complete
snapshot:
  app: 3e261f5a7576
---

# User manual and product behaviour specification

This is the public product specification. It describes what a user can do and what a team must preserve when rebuilding the product. It contains no code pointers.

## Product summary

SDR driver is an Android app that turns a supported USB software-defined-radio tuner into a radio that other apps can use. A radio app on the same phone asks the driver to open a tuner; the driver asks Android for permission to use the plugged-in hardware, opens it, starts a small radio server on the phone itself, and answers back with the tuner's name and the list of controls that particular tuner understands. The other app then receives the live radio signal over a local network connection and tunes the hardware while it listens. The driver is not a radio player by itself: in normal use it is a bridge between the hardware and a radio app. It can also be launched from the app icon, where a console screen shows what the driver is doing and lets the user start or stop the radio session by hand. A physical tuner is required for the main flow; without one, only the console screen and its messages are available.

## Actors

- SDR app user — the person who plugs the tuner into the phone, answers Android's permission questions, and can start or stop the driver from the app's own screen.
- SDR client app — a separate radio app that requests a tuner, receives the live signal and sends tuning controls.
- Android system — announces that a tuner was plugged in, asks the user to allow USB access, shows the driver's ongoing notification and manages power.
- Supported USB tuner — an RTL-SDR type dongle or a HackRF One / HackRF Jawbreaker radio.
- Other installed apps — apps that listen for the driver's announcements that a session has started or ended.

## What you can do

### CAP-0001 Open a tuner on behalf of a radio app

**Actor.** SDR client app.

**When.** A client app sends an open request naming the driver's iqsrc address scheme and carrying the connection details it wants.

**Before you start.**
- The driver app is installed on the device and the device is a platform the driver supports.
- A supported tuner is plugged in, or the request names the specific attached tuner to use.
- The user can answer Android's USB access question.

**What to do.**
1. The client app sends an open request with the listening address and port, centre frequency, sample rate, tuner gain, frequency correction and bias tee it wants.
2. The driver checks that the device platform is supported.
3. The driver reads the requested connection details.
4. The driver determines which tuner to open: the one named in the request, the only one plugged in, or one chosen by the user.
5. The driver asks Android for permission to use that USB device, if it does not already have it.
6. The driver opens the tuner and starts the local radio server bound to the requested address and port, with the requested centre frequency already applied.
7. Once the tuner reports that it is open, the driver answers the requesting app with success, the tuner's name and the list of commands that tuner supports, then leaves the dialog-like screen it used.

**What you see.** The requesting app receives a success answer containing the tuner name and the supported command list, and can immediately connect to the driver's local address and port to receive the radio signal and control the tuner.

**Other paths.**
- The request names an attached tuner explicitly: the driver opens it directly and shows no device choice to the user.
- Exactly one supported tuner is plugged in: the driver opens it without asking the user anything.
- More than one supported tuner is available: the user is asked to pick one before anything is opened.

**If it does not work.**
- Unsupported platform: the request ends at once with a platform-not-supported reason and no hardware is touched.
- Malformed request details: the request ends with a wrong-arguments reason instead of crashing.
- No supported tuner found: the request ends with a no-devices reason.
- The user declines Android's USB access question: the request ends with an access error that the calling app can read and report.
- The tuner fails to open: the failure is reported back to the calling app as a translated reason the user can understand, and no session is left running.

**Rules.**
- Success is only reported for a tuner that has actually opened.
- A second open attempt for the same tuner while one is running replaces the running session rather than opening the hardware twice.
- A repeated start caused by the USB permission question closing does not open a second session.

**Acceptance criteria.**
- A well-formed request on a supported platform with one tuner attached succeeds without any user interaction.
- The success answer contains both the tuner name and a non-empty command list.
- The requested centre frequency is already in effect when the client first connects.
- A malformed request, an unsupported platform, an absent tuner and a refused access question each produce a distinct readable failure reason and no running session.

### CAP-0002 Choose which tuner to open

**Actor.** SDR app user.

**When.** An open request arrives while more than one supported tuner is available.

**Before you start.**
- At least two supported tuners are detectable.
- A client app or the app's own console screen has requested a session.

**What to do.**
1. The driver presents a single selection dialog listing the detectable tuners.
2. The user taps the tuner they want.

**What you see.** The chosen tuner is opened and the chosen session proceeds.

**Other paths.**
- The user dismisses or backs out of the dialog: the request ends with an access error toward the calling app, as if permission had been refused.

**If it does not work.**
- The request that needed a choice ends, the dialog is gone, and the calling app is told the request failed with an access reason; the user can simply try again from the client app.

**Rules.**
- Only one selection dialog is shown at a time; a new request replaces it rather than stacking dialogs.

**Acceptance criteria.**
- With two or more tuners attached, exactly one dialog appears.
- Picking a tuner starts a session on that tuner.
- Dismissing the dialog ends the request and reports failure to the caller.

### CAP-0003 Serve the radio signal to a connected client

**Actor.** SDR client app.

**When.** The client connects to the address and port it named in its successful open request.

**Before you start.**
- An open request has already succeeded.
- The client knows the supported command list returned with that success.

**What to do.**
1. The client opens a TCP connection to the driver's local address and port.
2. The client sends commands, each one a single command byte followed by a numeric argument.
3. The driver executes each command in the order it arrives and keeps the signal flowing to the client.
4. The client works with the settings it has chosen until it sends more commands or closes.

**What you see.** The client receives a continuous radio signal at the sample rate and centre frequency it set, and each accepted command changes the tuner accordingly.

**Other paths.**
- A client that speaks only the standard rtl_tcp command set works without any of the driver's extra commands.
- A client sends the dedicated shutdown command: the session is cancelled and ends cleanly from the server side.

**If it does not work.**
- A command the current hardware does not support is logged and ignored; the connection and the signal are kept, and the client notices only that nothing changed.
- If the connection is lost or the tuner is unplugged, the session ends and the driver announces the device detach so other apps can react.

**Rules.**
- Commands are validated against the supported command list of the attached hardware.
- The standard command codes, the driver's own extension codes and the SDRplay RSP codes occupy separate, fixed ranges of the command byte space, and clients must use those exact values.
- Commands from one connection are handled in order, one at a time.
- Unsupported commands never tear down a session.

**Acceptance criteria.**
- A standard rtl_tcp client connects and receives a signal without any driver-specific changes.
- Tuning commands take effect while the client is receiving.
- Sending a command outside the advertised list leaves the connection open and the signal running.
- The shutdown command ends the session and the session-end announcement is emitted.

### CAP-0004 Control the tuner from the client

**Actor.** SDR client app.

**When.** The user of the client app tunes, changes gain or otherwise adjusts the radio while connected.

**Before you start.**
- A session is running and the client is connected.

**What to do.**
1. The client sends a command for the setting it wants to change — for example centre frequency, sample rate, tuner gain, gain mode or automatic gain control.
2. The driver applies the request to the opened hardware, clamped to what that hardware can do.
3. For portable gain control the client sends the single-percentage gain command instead of a hardware-specific gain command.

**What you see.** The tuner changes settings live and the connected client hears or decodes the new signal immediately.

**Other paths.**
- Frequency correction and bias tee are supplied with the open request and set the starting state before any client command arrives.
- On hardware that has no automatic gain mode, an automatic-gain request is turned into fixed, sensible gain values so the client still gets usable reception.

**If it does not work.**
- A setting the attached hardware cannot express is ignored and logged; the previous setting stays in force and the client can choose another supported setting.

**Rules.**
- Each hardware family advertises its own command subset, so the same command can be honoured on one tuner and ignored on another.
- Gain commands written for RTL tuners are mapped onto the corresponding HackRF gain stages and clamped to their ranges.
- The percentage gain command is the portable way to ask for gain across hardware families.

**Acceptance criteria.**
- Changing the centre frequency from the client retunes the signal without reconnecting.
- Changing the sample rate from the client changes the delivered signal rate.
- A single percentage gain request produces a reasonable gain on both RTL and HackRF hardware.
- An automatic-gain request on hardware without automatic gain leaves reception working at fixed gain.

### CAP-0005 Start and stop the driver from the console screen

**Actor.** SDR app user.

**When.** The user taps the app icon and then the on/off control on the console screen.

**Before you start.**
- The device platform is supported.
- A supported tuner is plugged in for a session to open.

**What to do.**
1. The user opens the app from its icon and lands on the console screen, which shows the driver's running log and a start/stop control.
2. The user taps the control to start; the driver opens a tuner exactly as it would for a client request.
3. The user taps the control again to stop; the running session is closed and the log records it.

**What you see.** The radio session starts or stops on the user's command, and the control's on/off appearance always matches the driver's real state rather than the last tap.

**Other paths.**
- Leaving the screen and coming back re-reads the real driver state, so the control is correct even if the session changed while the user was away.
- If the device platform is unsupported, the screen instead shows a warning that the driver cannot run here.

**If it does not work.**
- A start that fails is written to the on-screen log with a readable reason; the control returns to its off appearance and the user can try again.
- Opening the tuner is handed to the same device-opening flow as a client request, so a refused USB permission or an unplugged tuner surfaces as a log line with the reason.

**Rules.**
- The console screen is the only in-app way to start or stop a session.
- The control's state is derived from the driver, never from the tap itself.
- Failure to start is reported in the log and never as a blocking dialog or an automatic retry.

**Acceptance criteria.**
- Tapping the control with a tuner attached opens a session and the control shows on.
- Tapping again closes the session and the control shows off.
- Leaving and returning to the screen shows the real current state.
- On an unsupported platform the warning is displayed instead of an attempt to start.

### CAP-0006 Read and manage the driver log from the console screen

**Actor.** SDR app user.

**When.** The user is on the console screen and wants to see what the driver has done or clear it.

**Before you start.**
- The app is open on the console screen.

**What to do.**
1. The screen continuously appends driver messages to a scrolling log area.
2. The user copies the log to share it, clears it to start fresh, or opens the help and licence information from the same screen.
3. Hardware and session events appear in the log as they happen.

**What you see.** The user can follow what the driver is doing, keep or share the log text, and read the help and licence information without leaving the screen.

**Other paths.**
- The log keeps updating while the user reads it, including during an active session lifecycle.

**If it does not work.**
- If nothing has happened yet the log is simply empty and the rest of the screen still works.

**Rules.**
- The log is the user-visible record of session starts, session ends and failures.
- Clearing the log affects only what is displayed, not the running session.

**Acceptance criteria.**
- Session starts and ends appear in the log without the user refreshing anything.
- Copy places the current log text on the clipboard.
- Clear empties the log area.
- Help and licence information are reachable from the console screen.

### CAP-0007 React when a tuner is plugged in

**Actor.** Android system.

**When.** A USB tuner is attached to the phone.

**Before you start.**
- The attached device belongs to one of the recognised tuner vendor/product families.
- The driver app is installed.

**What to do.**
1. Android launches the driver's plug-in handling for the attached tuner.
2. The driver confirms that the event really is a USB device attach, then reads the attached device and records its name in the driver log.
3. The driver forwards an SDR-device-attached announcement carrying the device, so an installed app that wants to react can do so.
4. The driver finishes immediately without showing a screen of its own.

**What you see.** Plugging in a recognised tuner produces a log entry naming the device and a handoff announcement to whichever app claims it; the user sees no full-screen takeover.

**Other paths.**
- No installed app claims the announcement: the log records that nothing was found to handle it and the driver stops there, leaving the rest of the flow untouched.
- An unrelated process launches the same handling with a different action: the driver logs that the action is unhandled and finishes without side effects.

**If it does not work.**
- The plug-in event carries no device information: the driver notes it and skips the handoff.
- The handoff cannot be delivered: the driver records that no activity was found for SDR handling and finishes quietly instead of crashing.

**Rules.**
- The plug-in handling only reacts to those hardware families in the app's recognise list; other USB devices are ignored.
- The plug-in handling forwards the device; it does not itself request USB permission or claim the hardware.
- Plug-in handling never shows a user interface.

**Acceptance criteria.**
- Attaching a recognised tuner produces a log entry with the device name.
- A handoff announcement carrying the device is dispatched for that attach.
- No screen is displayed by the plug-in path.
- Attaching an unrecognised USB device does nothing.

### CAP-0008 Keep the session running in the background

**Actor.** Android system.

**When.** A tuner session becomes active.

**Before you start.**
- A session has been opened successfully.
- The app holds the permissions it declares for foreground work.

**What to do.**
1. As soon as the session starts, the driver puts itself in the foreground so Android does not stop it, and shows an ongoing, non-dismissible notification titled with the app name.
2. When the tuner reports that it has opened, the driver also takes a wake lock that keeps the screen bright, released automatically after one hour.
3. When the session ends, the notification is removed and the wake lock is released.

**What you see.** The radio keeps streaming while the user is in another app or the screen is otherwise idle, and the ongoing notification tells the user that the driver is active.

**Other paths.**
- If the user has not allowed notifications, the session still starts and runs; only the ongoing notification is missing.

**If it does not work.**
- A session that starts and ends very quickly never shows the notification, so no stale ongoing notification is left behind.
- If the wake lock cannot be taken, streaming continues unaffected.

**Rules.**
- The ongoing notification exists only while a session is live.
- Notification permission is optional and never blocks a session.
- The wake lock is limited to one hour per successful open and is always released when the session closes.

**Acceptance criteria.**
- Starting a session shows an ongoing notification that cannot be swiped away while the session runs.
- The notification disappears when the session ends.
- Refusing notification permission still results in a running session.
- Streaming continues after the user switches to another app.

### CAP-0009 Announce session state to other installed apps

**Actor.** Other installed apps.

**When.** A driver session opens, or a session ends for any reason.

**Before you start.**
- The driver app is installed and a session has been started.

**What to do.**
1. When a session opens, the driver broadcasts an SDR-device-attached announcement.
2. When the session closes — because the user stopped it, the client shut it down, the tuner was unplugged or an error occurred — the driver broadcasts an SDR-device-detached announcement carrying the device name.

**What you see.** An installed app that watches for these announcements can launch itself when a tuner session becomes available and stand down when it ends.

**Other paths.**
- If no app listens, the announcements are simply not acted on and the session is unaffected.

**If it does not work.**
- The closing announcement is emitted for both clean and failed ends, so a listening app is never left believing a dead session is still alive.

**Rules.**
- The announcement names are a public contract with other apps and must stay stable.
- The closed announcement is emitted exactly once per session end, with the device name attached.

**Acceptance criteria.**
- Opening a session produces an attached announcement.
- Ending a session, cleanly or through an error, produces a detached announcement carrying the device name.
- Announcements do not change the outcome of the session itself.

### CAP-0010 Use a HackRF radio

**Actor.** SDR app user.

**When.** A HackRF One or HackRF Jawbreaker is plugged in, or the driver is asked to open one.

**Before you start.**
- The HackRF hardware is attached and recognised by the app's device filter.
- The user grants Android's USB access question for that device.

**What to do.**
1. The driver recognises the HackRF hardware when it is attached or when the available tuners are listed.
2. If USB access has not yet been granted, the driver asks the user through Android's standard question.
3. When the user allows access, the driver creates the HackRF driver, confirms it is ready, and serves it through the same local radio-server surface as other tuners.
4. The client controls it with the same commands, and gain requests are mapped onto the HackRF gain stages.

**What you see.** A HackRF radio appears as an openable tuner alongside RTL-SDR dongles and is driven by the same client apps and the same connection protocol.

**Other paths.**
- Access already granted for the HackRF: the driver opens it immediately with no question asked.
- A client that only knows hardware-independent commands still gets usable gain through the single-percentage gain command.

**If it does not work.**
- The user denies access: the driver shows a short on-screen message that permission was denied for that device and reports the error to the app, and no session starts.
- The HackRF cannot be opened: the error is reported through the same error channel so the calling app can display a reason.
- A client sends a command the HackRF does not support: it is ignored and the session continues.

**Rules.**
- HackRF exposes a smaller command set than an RTL tuner, so clients must rely on the advertised list.
- Gain commands written for RTL tuners are translated to HackRF gain stages and clamped to their ranges.
- Because HackRF has no automatic gain mode, automatic-gain and gain-mode requests are turned into fixed, sensible gain values rather than refused.
- The HackRF driver is derived from an open-source HackRF library for Android; upstream attribution and licence notices must be preserved.

**Acceptance criteria.**
- With a HackRF attached and access granted, the device is offered as an openable tuner.
- The success answer for a HackRF lists only the commands it supports.
- Frequency, sample rate and gain requests from a client change the HackRF reception.
- Denying access produces a user-visible message and an error to the caller, with no session left running.


## User journeys

### JRN-0001 A radio app starts the driver and listens

**Goal.** Get a live radio signal from the plugged-in tuner without the user leaving the radio app.

**Steps.**
1. The user attaches a supported tuner.
2. In the radio app, the user starts reception; the radio app sends an open request with the address, port, centre frequency, sample rate, gain, frequency correction and bias tee it wants.
3. The driver checks the platform, reads the request, finds the tuner and asks Android for USB access if needed.
4. The user allows access.
5. The driver opens the tuner, starts the local radio server with the requested centre frequency already applied, and returns success with the tuner name and command list.
6. The radio app connects to the local address and port and starts receiving.
7. The user tunes and adjusts gain inside the radio app; the driver applies each change.
8. When the user stops listening, the driver closes the session and announces the detach.

**Success.** The radio app is receiving a live signal from the local tuner and its tuning and gain controls work, all without the user ever opening the driver app.

**Interruptions.**
- Android's USB access question appears mid-flow and delays the open.
- More than one tuner is attached, so the user must choose.
- The user refuses the USB access question.
- No supported tuner is attached.
- The device platform is not supported by the driver.
- The connection drops or the tuner is unplugged mid-session.

**Recovery.**
- A refused access question or an absent tuner ends the request with a readable reason; the user can plug in a tuner or allow access and retry from the radio app.
- A badly formed request ends with a wrong-arguments reason rather than hanging.
- If the session ends unexpectedly, the detach announcement lets the radio app stop cleanly, and starting again from the radio app opens a fresh session because the driver never restores an old one by itself.

### JRN-0002 The user runs the driver by hand from the app

**Goal.** Check that the driver works and start or stop it without needing another app.

**Steps.**
1. The user plugs in a supported tuner and taps the app icon.
2. The console screen appears with the driver log and the on/off control.
3. The user taps the control to start; the driver opens the tuner and the log records the progress.
4. The control shows on, an ongoing notification appears and the session keeps running while the user leaves the screen.
5. The user returns to the screen, sees the control still on, and taps it to stop.
6. The log records the end of the session and the notification disappears.

**Success.** The user can see the driver running, confirmed both by the on/off control and by the ongoing notification, and can stop it at any time.

**Interruptions.**
- No tuner is plugged in.
- The user refuses the USB access question.
- More than one tuner is attached and the user must choose.
- The device platform is unsupported, so only the warning is shown.
- The user leaves the screen while a session runs.

**Recovery.**
- A failed start is written to the log with a readable reason and the control returns to off, so the user can fix the cause and tap again.
- Coming back to the screen re-reads the real driver state, so the control is never stale.
- If the session stops while the user is elsewhere, returning to the screen shows the control off.

### JRN-0003 Plugging in a tuner

**Goal.** Have the phone notice the tuner as soon as it is connected.

**Steps.**
1. The user plugs a recognised tuner into the phone.
2. Android launches the driver's plug-in handling.
3. The driver reads the device, records its name in the log and forwards an SDR-device-attached announcement.
4. The driver finishes at once without taking over the screen.
5. Any installed app that handles the announcement can react, for example by offering to start listening.

**Success.** The tuner is noticed the moment it is plugged in, the driver log names it, and an interested app is offered the handoff, with no full-screen interruption.

**Interruptions.**
- The attached device is not one of the recognised tuner families.
- No installed app handles the announcement.

**Recovery.**
- An unrecognised device or an unclaimed announcement simply produces a log entry; the user can still open the driver or start their radio app manually.
- The plug-in path never blocks or crashes the driver, so a later manual start is unaffected.

### JRN-0004 A session ends

**Goal.** Stop using the radio and have everything released cleanly.

**Steps.**
1. The session ends because the user stops it from the console screen, the client sends the shutdown command, the tuner is unplugged, or an error occurs.
2. The driver closes the tuner, removes the ongoing notification and releases the wake lock.
3. The driver announces the device detach with the device name.
4. The driver returns to idle with no session remembered.

**Success.** The tuner and the phone's resources are released, the ongoing notification is gone, and any listening app learns that the device is no longer available.

**Interruptions.**
- The end is caused by an error or an unexpected unplug rather than a user action.
- The screen was left on and the wake lock was still held.

**Recovery.**
- The end-of-session announcement is emitted for clean and failed ends alike, so listening apps can stand down and the user can start a new session from either the console screen or a radio app.
- Because the driver keeps no session after an end, restarting always means a fresh open, which asks for USB access again only if it is no longer granted.

### JRN-0005 A client works across different tuner hardware

**Goal.** Give the user working tuning and gain on whichever supported tuner is attached.

**Steps.**
1. The radio app requests a session and receives the tuner name plus the supported command list.
2. The radio app enables only the controls that the returned list supports.
3. For gain the radio app uses the single-percentage gain command, which the driver scales to the attached hardware.
4. For frequency and sample rate the radio app uses the standard commands, which both hardware families support.

**Success.** The same radio app gives usable tuning and gain on an RTL-SDR dongle and on a HackRF, without the user knowing which family is attached.

**Interruptions.**
- The user plugs in HackRF hardware and grants access.
- The user plugs in an RTL-SDR dongle instead.
- The radio app assumes a command the current hardware does not support.

**Recovery.**
- A command outside the advertised list is ignored and the session keeps running, so the radio app can fall back to a supported control on the next interaction.
- Automatic-gain requests on hardware without automatic gain still leave the radio usable at fixed gain, so reception never stops because of a gain-mode mismatch.

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

See [SOURCES.md](SOURCES.md).

