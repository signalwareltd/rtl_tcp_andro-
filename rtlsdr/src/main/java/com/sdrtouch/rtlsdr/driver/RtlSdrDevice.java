/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2022 by Signalware Ltd <driver@sdrtouch.com>
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sdrtouch.rtlsdr.driver;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.core.exceptions.SdrException;
import com.sdrtouch.tools.Log;
import com.sdrtouch.tools.UsbPermissionObtainer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One instance owns one permission/open/close operation. */
public class RtlSdrDevice extends SdrDevice {
    private static final long PERMISSION_TIMEOUT_MS = 20_000;
    private final UsbDevice usbDevice;
    private final long nativeHandler;
    private final transient NativeSession session;
    private final transient PermissionSource permissions;
    private final long permissionTimeoutMs;
    private volatile boolean closeRequested;
    private final Object nativeLock = new Object();
    private boolean started;
    private boolean nativeStarting;
    private boolean disposed;
    private transient Future<UsbDeviceConnection> permissionFuture;
    private transient Thread worker;

    // Package-private seams let host tests exercise the production ownership
    // code without loading JNI, opening USB or running acquisition.
    interface PermissionSource {
        Future<UsbDeviceConnection> obtain(Context context, UsbDevice device);
    }
    interface NativeSession {
        long initialize();
        boolean open(long pointer, int fd, SdrTcpArguments args, String path) throws Exception;
        void close(long pointer);
        void dispose(long pointer);
    }

    public RtlSdrDevice(Context context, UsbDevice device) {
        this(context, device, null, UsbPermissionObtainer::obtainFdFor, PERMISSION_TIMEOUT_MS);
    }

    RtlSdrDevice(Context context, UsbDevice device, NativeSession backend,
                 PermissionSource permissions, long timeoutMs) {
        super(context);
        if (timeoutMs <= 0) throw new IllegalArgumentException("permission timeout");
        this.usbDevice = device;
        this.permissions = permissions;
        this.permissionTimeoutMs = timeoutMs;
        this.session = backend != null ? backend : new NativeSession() {
            @Override public long initialize() { return RtlSdrDevice.this.initialize(); }
            @Override public boolean open(long pointer, int fd, SdrTcpArguments a, String path) throws Exception {
                return RtlSdrDevice.this.openAsync(pointer, fd, a.getGain(), a.getSamplerateHz(),
                        a.getFrequencyHz(), a.getPort(), a.getPpm(), a.getBiasT(), a.getAddress(), path);
            }
            @Override public void close(long pointer) { RtlSdrDevice.this.close(pointer); }
            @Override public void dispose(long pointer) { RtlSdrDevice.this.deInit(pointer); }
        };
        this.nativeHandler = session.initialize();
    }

    @Override
    public void openAsync(final SdrTcpArguments args) {
        synchronized (nativeLock) {
            if (started || disposed || closeRequested) {
                Log.appendLine("RtlSdrDevice is single-use; ignoring duplicate open");
                return;
            }
            started = true;
            worker = new Thread(() -> runSession(args), "rtl-sdr-open");
        }
        worker.start();
    }

    private void runSession(SdrTcpArguments args) {
        Future<UsbDeviceConnection> request = null;
        UsbDeviceConnection connection = null;
        Throwable error = null;
        try {
            if (closeRequested) return;
            request = permissions.obtain(context, usbDevice);
            boolean cancel;
            synchronized (nativeLock) {
                permissionFuture = request;
                cancel = closeRequested || disposed;
            }
            if (cancel) request.cancel(true);
            connection = request.get(permissionTimeoutMs, TimeUnit.MILLISECONDS);
            if (connection == null) throw new IllegalStateException("Could not get a USB connection");
            if (closeRequested) return;
            int fd = connection.getFileDescriptor();
            String path = usbDevice.getDeviceName();
            boolean enterNative;
            synchronized (nativeLock) {
                permissionFuture = null;
                enterNative = !closeRequested && !disposed;
                // Linearization point: after this claim, close is an active
                // native stop request, not a pending-permission cancellation.
                if (enterNative) nativeStarting = true;
            }
            if (enterNative) {
                Log.appendLine("Opening fd " + fd);
                if (!session.open(nativeHandler, fd, args, path) && !closeRequested) {
                    error = new SdrException(SdrException.EXIT_UNKNOWN);
                }
            }
        } catch (CancellationException e) {
            if (!closeRequested) error = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!closeRequested) error = e;
        } catch (ExecutionException e) {
            error = e.getCause();
        } catch (Throwable e) {
            error = e;
        } finally {
            // A timed out/interrupted waiter must also revoke its callback.
            try {
                if (request != null && !request.cancel(false) && connection == null
                        && request.isDone() && !request.isCancelled()) {
                    // Completion can win immediately after get times out or is
                    // interrupted. Reclaim its transferred fd before discarding
                    // the future; cancellation no longer owns that result.
                    connection = request.get(0, TimeUnit.NANOSECONDS);
                }
            }
            catch (Throwable e) { if (error == null) error = e; }
            try {
                if (connection != null) connection.close();
            } catch (Throwable e) {
                if (error == null) error = e;
            }
            synchronized (nativeLock) {
                permissionFuture = null;
                nativeStarting = false;
                worker = null;
            }
            try { dispose(); }
            catch (Throwable e) { if (error == null) error = e; }
            // Release fd/native ownership before a listener starts another session.
            announceOnClosed(error);
        }
    }

    @Override
    public void close() {
        Future<UsbDeviceConnection> pending;
        synchronized (nativeLock) {
            if (closeRequested) return;
            closeRequested = true;
            pending = permissionFuture;
            if (!started) {
                if (!disposed) { disposed = true; session.dispose(nativeHandler); }
            } else if (nativeStarting && !disposed) {
                session.close(nativeHandler);
            }
        }
        // Never wait/join on the UI thread or call Android receiver cleanup
        // while holding nativeLock. Cancelling wakes the owned worker's get().
        if (pending != null) pending.cancel(true);
    }

    private void dispose() {
        synchronized (nativeLock) {
            if (disposed) return;
            disposed = true;
            session.dispose(nativeHandler);
        }
    }

    @Override public String getName() { return "rtl-sdr " + usbDevice.getDeviceName(); }

    @Override protected void finalize() throws Throwable {
        try { super.finalize(); }
        finally { dispose(); }
    }

    @Override public native int[] getSupportedCommands();
    private native long initialize();
    private native void close(long pointer);
    private native void deInit(long pointer);
    private native boolean openAsync(long pointer, int fd, int gain, long rate,
            long frequency, int port, int ppm, int biasT, String address, String path) throws Exception;
}
