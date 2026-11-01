---
title: F-0007 Stream
audience: engineer
scope: Public feature contract for F-0007
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0024
- C-0025
- C-0026
- C-0027
- C-0028
- C-0029
- C-0030
- C-0031
- C-0032
- C-0033
- C-0034
- C-0035
- C-0036
- C-0037
- C-0038
- C-0039
- C-0040
- C-0041
- C-0042
- C-0043
---

# F-0007 — Stream

## Outcome

Activity `com.sdrtouch.rtlsdr.StreamActivity` declared in app/src/main/AndroidManifest.xml.

## Trigger and conditions

- **Trigger:** Activity launch via android.intent.action.MAIN
- **Condition:** Exported component (activity); another app can invoke it.

## User-visible behaviour

- StreamActivity is the exported LAUNCHER entry point of the app, so the stream feature is reached by launching the app icon. User tapping the launcher icon lands on the stream console screen.
- StreamActivity.onCreate inflates R.layout.activity_stream, caches the terminal, scroll and command-line views, and wires the toggle, license, copy, clear and help controls plus a platform-unsupported warning. The screen exposes the driver log, the start/stop toggle and diagnostic actions.
- The on/off toggle is the sole start/stop trigger: it asserts the bound service is non-null, then either calls service.closeService() or startServiceFlow(), and resets its checked state from service.isRunning(). A tap either tears down a running driver session or starts a new one; UI state is re-derived from the service, not from the click.
- Starting a stream builds the driver argument string into an implicit-style iqsrc:// URI, pins the target to DeviceOpenActivity, and awaits the result with request code 1. Device selection/open is delegated to a separate activity; StreamActivity observes only success/failure.
- On return from the start flow, StreamActivity logs success for RESULT_OK, otherwise decodes the int extra marto.rtl_tcp_andro.RtlTcpExceptionId into SdrException.err_info (defaulting to unknown_error) and logs the failure reason. Failure surfaces only as a log line in the terminal, never as a dialog or retry.
- StreamActivity binds BinaryRunnerService in onResume with BIND_AUTO_CREATE and registers itself as a Log.Callback, and unbinds/unregisters in onPause. Rotation/backgrounding drops and re-establishes the connection; the toggle state is restored by the callback replay described below.
- On connection the activity registers a BinaryRunnerService.StatusCallback through LocalBinder; onServerRunning/onServerNotRunning set the toggle checked state on the UI thread, and registerCallback immediately replays the current state. The toggle reflects real driver state after re-bind, even if the user toggled it while the activity was gone.
- BinaryRunnerService.onStartCommand starts the queued session and returns START_STICKY, so the driver session is intended to survive process-level kills. A restarted service re-enters startWithDevice with an empty queue and tears itself down.
- startWithDevice is synchronized and defers to an active session: if sessionActive it logs and returns; otherwise it drains one queued (device, arguments) pair, announces running, attaches the status listener, opens the device asynchronously and posts startForeground on the main handler; an empty queue instead announces not-running, clears status callbacks and stopSelf(). Only one SDR session can start at a time; a second request is queued rather than failing.
- Queueing uses a latest-request-wins policy: addWork drains all pending work under the workQueue lock, cancels the superseded pending opens via cancelPendingOpen(), and appends the newest request. Rapid repeated starts do not accumulate sessions and the abandoned request's activity is finished by the device layer.
- A running session is advertised with an ongoing high-importance notification on channel "rtl_sdr", started with foreground service type FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK on Android Q+. The user sees a persistent driver notification while the stream runs, and the process is protected from background kill.
- When the device reports open, the service logs readiness on the device's callback and acquires a 1-hour SCREEN_BRIGHT wake lock on the main thread. Screen stays on for up to one hour of streaming; a failure to acquire is silent apart from logcat.

## Code start points

- `app/src/main/java/com/sdrtouch/rtlsdr/StreamActivity.java` — `com.sdrtouch.rtlsdr.StreamActivity#onCreate(Bundle)`
- `app/src/main/java/com/sdrtouch/rtlsdr/StreamActivity.java` — `com.sdrtouch.rtlsdr.StreamActivity#startServiceFlow()`
- `app/src/main/java/com/sdrtouch/rtlsdr/StreamActivity.java` — `com.sdrtouch.rtlsdr.StreamActivity#onActivityResult(int,int,Intent)`
- `app/src/main/java/com/sdrtouch/rtlsdr/StreamActivity.java` — `com.sdrtouch.rtlsdr.StreamActivity#onResume()`

## Evidence

[`C-0024`](EVIDENCE.md), [`C-0025`](EVIDENCE.md), [`C-0026`](EVIDENCE.md), [`C-0027`](EVIDENCE.md), [`C-0028`](EVIDENCE.md), [`C-0029`](EVIDENCE.md), [`C-0030`](EVIDENCE.md), [`C-0031`](EVIDENCE.md), [`C-0032`](EVIDENCE.md), [`C-0033`](EVIDENCE.md), [`C-0034`](EVIDENCE.md), [`C-0035`](EVIDENCE.md), [`C-0036`](EVIDENCE.md), [`C-0037`](EVIDENCE.md), [`C-0038`](EVIDENCE.md), [`C-0039`](EVIDENCE.md), [`C-0040`](EVIDENCE.md), [`C-0041`](EVIDENCE.md), [`C-0042`](EVIDENCE.md), [`C-0043`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
