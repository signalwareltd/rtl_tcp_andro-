# Bundled libusb

- **Upstream version:** libusb 1.0.30 (`LIBUSB_NANO 12037`)
- **Upstream tag:** https://github.com/libusb/libusb/releases/tag/v1.0.30
- **Vendored from:** official release tarball `libusb-1.0.30.tar.bz2`
- **Tarball SHA-256:** `fea36f34f9156400209595e300840767ab1a385ede1dc7ee893015aea9c6dbaf`

## What is vendored

- `libusb/` — library sources and headers (`core.c`, `descriptor.c`, `hotplug.c`, `io.c`, `sync.c`,
  `strerror.c`, `version.h`, `version_nano.h`, `libusb.h`, `libusbi.h`, `os/`)
- `android/config.h` — upstream Android build configuration
- `android/README` — upstream notes on the Android fd flow
- `COPYING`, `AUTHORS`, `ChangeLog`, `NEWS`, `README`

Not vendored: `tests/`, `examples/`, `doc/`, `Xcode/`, `msvc/`, autotools files,
`libusb/Makefile*`, `libusb/libusb-1.0.def`, `libusb/libusb-1.0.rc`, `android/jni/`,
`android/examples/`, and the non-Linux OS backends (darwin, windows, haiku, netbsd, openbsd,
sunos, emscripten, udev, null).

## Build system

libusb 1.0.30 has no CMake build. The local `CMakeLists.txt` is a thin wrapper that mirrors the
maintained Android ndk-build file (`android/jni/libusb.mk`):

- sources: `core.c descriptor.c hotplug.c io.c sync.c strerror.c os/linux_usbfs.c
  os/events_posix.c os/threads_posix.c os/linux_netlink.c`
- include paths: `android/` (for `config.h`), `libusb/`, `libusb/os/`
- flags: `-fvisibility=hidden -pthread`; links `liblog`

The CMake target is still named `libusb`, so the produced file remains `liblibusb.so` and the
existing packaging/link names are unchanged.

## Android file-descriptor flow

The old 2016 fork provided `libusb_open2()` / `libusb_get_device2()` / `op_open2()` /
`op_device2()`. Those are **intentionally gone**. The app now uses the official upstream API:

```c
struct libusb_init_option options[] = {
    { .option = LIBUSB_OPTION_NO_DEVICE_DISCOVERY, .value.ival = 1 },
};
libusb_init_context(&ctx, options, 1);
libusb_wrap_sys_device(ctx, (intptr_t) fd, &handle);
```

`LIBUSB_OPTION_NO_DEVICE_DISCOVERY` skips enumeration and hotplug (required on unrooted Android).
`libusb_wrap_sys_device()` sets `hpriv->fd_keep = 1`, so **`libusb_close()` does not close the
wrapped fd** — the Java owner (`UsbDeviceConnection`) must close it after the native session ends.

## Crash fixes included

- `9eda802d` — `linux_usbfs: Wait until all URBs have been reaped before freeing them`
  (count-based completion; fixes out-of-order URB reaps on disconnect).
- `00e36c8` — `linux_usbfs: Deal with receiving POLLERR before all transfers have completed`
  (reap, then always call `usbi_handle_disconnect()`).
