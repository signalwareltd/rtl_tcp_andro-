---
title: F-0001 Binary Runner
audience: engineer
scope: Public feature contract for F-0001
status: complete
snapshot:
  app: 3e261f5a7576
claims:
- C-0048
- C-0049
- C-0050
- C-0051
- C-0052
- C-0053
- C-0054
- C-0055
- C-0056
- C-0057
- C-0058
- C-0059
- C-0060
- C-0061
---

# F-0001 — Binary Runner

## Outcome

Service `com.sdrtouch.rtlsdr.BinaryRunnerService` declared in app/src/main/AndroidManifest.xml.

## Trigger and conditions

- **Trigger:** Service start or binding
- **Condition:** Foreground service type `mediaPlayback`.

## User-visible behaviour

- BinaryRunnerService is declared in the main manifest as a non-exported service in the app process with foregroundServiceType mediaPlayback; the app declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK, POST_NOTIFICATIONS and INTERNET. Third-party apps cannot start it directly; all F-0001 behaviour is in-process.
- The feature has two entry triggers: (a) binding, where SdrServiceConnection.onServiceConnected casts the binder and calls LocalBinder.startWithDevice(device, args); (b) service start, where onStartCommand immediately calls startWithDevice() to drain the queue. There is no user-facing launch of the service other than through these two paths.
- LocalBinder.startWithDevice enqueues the request and then either starts the service as a foreground service (startForegroundService on O+, startService otherwise) when isRunning is false, or calls closeService() to replace the current running session. Switching devices while running is implemented as close-then-reopen, never as open-while-open.
- addWork implements latest-request-wins: it drains every already-queued Pair and cancels the superseded devices' pending opens via cancelPendingOpen() before queueing the new request. Queued-but-not-started sessions can never accumulate; at most one waiting session exists.
- startWithDevice() is synchronized and guarded by the sessionActive flag, so a queued session cannot open the device while another is starting or running; the call logs and returns instead (the documented defence against LIBUSB_ERROR_BUSY). Deferral is the only concurrency control; the deferred item is picked up only by the next onClosed or onStartCommand.
- When a session is admitted, the service sets sessionActive, publishes announceRunning, registers the status listener on the device and calls openAsync(arguments) directly on the calling thread, without waiting for the device to report onOpen. Observers see 'running' before the driver actually accepts clients; a failed open is reported only later via onClosed(Throwable).
- The foreground notification is created lazily on the main thread after openAsync: channel id 'rtl_sdr' with IMPORTANCE_HIGH on O+, an ongoing non-auto-cancel notification titled with R.string.app_name, promoted with FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK on Q+. A session that opens and closes before the main thread runs the post never shows a notification, avoiding a stale ongoing notification.
- On onOpen the service posts ackquireWakeLock() to the main looper; that acquires a SCREEN_BRIGHT_WAKE_LOCK|ON_AFTER_RELEASE wake lock named 'rtl_tcp_andro:sdr_driver_lock' with a one-hour timeout, swallowing all Throwables. The screen may stay on for up to one hour per successful open; acquisition failures are silent.
- onClosed — invoked from the device's worker thread — runs under the service monitor, logs success or the exception, broadcasts ACTION_SDR_DEVICE_DETACHED with EXTRA_DEVICE_NAME, posts stopForeground(true), nulls thisSdrDevice, clears sessionActive, releases the wake lock, and then calls startWithDevice() to promote the next queued session. DETACHED is the observable end-of-session signal for other components, emitted for both clean and exceptional closes.
- When the queue is empty, startWithDevice() calls announceNotRunning(), clears all registered StatusCallbacks and calls stopSelf(), returning the service to a stopped state with no persisted session. Callback listeners are dropped without notification that they were removed; a later rebind must re-register.
- Because onStartCommand returns START_STICKY and reads nothing from the Intent, a system restart with a null Intent reaches startWithDevice() with an empty queue, announces not-running and stops the service: no session is restored. After process death the driver session is not re-established by the service; the UI must re-issue the command.
- Status propagation to the UI: announceRunning/announceNotRunning flip the isRunning flag and iterate registered StatusCallbacks under synchronized(statusCallbacks); registerCallback immediately replays the current state, and StreamActivity uses onServerRunning/onServerNotRunning to toggle a UI switch on the main thread. An observer that binds later still receives the correct current state.

## Code start points

- `app/src/main/java/com/sdrtouch/rtlsdr/SdrServiceConnection.java` — `SdrServiceConnection#onServiceConnected(ComponentName,IBinder)`
- `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` — `BinaryRunnerService#onStartCommand(Intent,int,int)`
- `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` — `com.sdrtouch.rtlsdr.BinaryRunnerService.LocalBinder#startWithDevice(SdrDevice,SdrTcpArguments)`
- `app/src/main/java/com/sdrtouch/rtlsdr/BinaryRunnerService.java` — `com.sdrtouch.rtlsdr.BinaryRunnerService#addWork(SdrDevice,SdrTcpArguments)`

## Evidence

[`C-0048`](EVIDENCE.md), [`C-0049`](EVIDENCE.md), [`C-0050`](EVIDENCE.md), [`C-0051`](EVIDENCE.md), [`C-0052`](EVIDENCE.md), [`C-0053`](EVIDENCE.md), [`C-0054`](EVIDENCE.md), [`C-0055`](EVIDENCE.md), [`C-0056`](EVIDENCE.md), [`C-0057`](EVIDENCE.md), [`C-0058`](EVIDENCE.md), [`C-0059`](EVIDENCE.md), [`C-0060`](EVIDENCE.md), [`C-0061`](EVIDENCE.md)

## Related journeys

- See `USER_MANUAL.md`.
