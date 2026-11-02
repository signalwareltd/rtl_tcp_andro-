# Offline USB permission cancellation regressions

Run from the repository with a local JDK, Android SDK, and cached JUnit 4.13.2 / Hamcrest 1.3:

    python tools/run_offline_permission_tests.py --results <private-results-directory>

The runner compiles the changed production classes against the real Android SDK API surface, then executes those same classes against host-only Android fixtures and an injected fake native session. It does not build an APK, load JNI, invoke ADB, open sockets or USB, or execute acquisition. JUNIT_JAR and HAMCREST_JAR may point to local jars. JAVA_HOME and ANDROID_HOME / ANDROID_SDK_ROOT are supported. A full app/NDK build and real Android receiver-delivery behavior are separate gates.

## Ownership contract

AsyncFuture has one terminal result, exception or cancellation. All waiters check the state before waiting and wake on completion/cancellation. Timed waits use monotonic time and throw TimeoutException. Producers transferring resources use trySetDone; a rejected value still belongs to its producer. Cancellation is cooperative; this future does not own or interrupt a producer thread.

Each USB request owns its receivers and an immutable, package-scoped PendingIntent with a unique URI and the original device identity. Framework fill-in extras are unnecessary: completion checks the current enumerated device and UsbManager.hasPermission rather than trusting a supplied grant flag. Detach, cancellation, error and completion release callback ownership. If cancellation wins while openDevice returns, the request closes the rejected connection.

RtlSdrDevice retains its pending future, uses a 20-second permission wait, and cancels it on close or failed wait. A completed connection that wins the timeout/interruption race is reclaimed and closed. Closing before the native handoff prevents native entry. The FD and native handle are released before onClosed. Duplicate open calls cannot complete the original operation early.

The source factory still performs synchronous Android IPC; an IPC that itself stalls is not made interruptible by Future cancellation. After the native handoff claim, close uses the existing native stop mechanism. These host tests do not prove immediate native/USB shutdown, kernel recovery, foreground-service readiness, or physical SDR safety.

## Regression coverage

47 cases cover completed-before-get, cancellation/completion races, multiple waiters, spurious wakeups, timeout and interruption semantics; request identity, denial, detach, stale/late grants and framework errors; worker close-before-publication, close-before-native-handoff, failed-wait result reclamation, duplicate open and cleanup ordering. Tests use latches to control important interleavings. The pure Future tests also live in the standard sdrdrivertools src/test tree; Android/worker tests and fixtures are only compiled by this standalone runner and cannot enter production packaging.

This change is based on upstream 7b9bf69cfdfdd0bb73e231046198fc2d5307d3dd. It does not modify the installed Samsung driver 3.151 (source pin 08672ad9d4e72095bbe115dbbc920ecd90b8744f). No installation or acquisition is authorized by these tests. Keep the Samsung SDR block and publication hold.
