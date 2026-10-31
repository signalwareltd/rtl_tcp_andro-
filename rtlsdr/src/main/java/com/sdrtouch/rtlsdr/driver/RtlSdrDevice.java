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

/**
 * RTL-SDR session. One instance drives exactly one openAsync()/close() cycle:
 * a new instance is created for every session, and calling openAsync() again
 * after the session ended is rejected.
 */
public class RtlSdrDevice extends SdrDevice {
    private final UsbDevice usbDevice;
    private final long nativeHandler;
    private volatile boolean closeRequested;

    /** Guards started/disposed and all calls into the native handler. */
    private final Object nativeLock = new Object();
    private boolean started;
    private boolean disposed;


    public RtlSdrDevice(Context context, UsbDevice usbDevice) {
        super(context);
        this.usbDevice = usbDevice;
        this.nativeHandler = initialize();
    }

    @Override
    public void openAsync(final SdrTcpArguments sdrTcpArguments) {
        boolean duplicate;
        synchronized (nativeLock) {
            duplicate = started;
            if (!duplicate) started = true;
        }
        if (duplicate) {
            Log.appendLine("RtlSdrDevice.openAsync called again; this device is single-use");
            announceOnClosed(new IllegalStateException("RtlSdrDevice is single-use"));
            return;
        }

        new Thread() {
            @Override
            public void run() {
                UsbDeviceConnection connection = null;
                Throwable error = null;
                try {
                    connection = UsbPermissionObtainer.obtainFdFor(context, usbDevice).get();
                    if (connection == null) throw new RuntimeException("Could not get a connection");
                    if (closeRequested) {
                        // close() was called while the USB permission dialog was
                        // showing; do not open a session that nobody waits for.
                        Log.appendLine("Close was requested before the session started");
                    } else {
                        int fd = connection.getFileDescriptor();
                        Log.appendLine("Opening fd " + fd);
                        String path = usbDevice.getDeviceName();
                        if (!openAsync(nativeHandler, fd, sdrTcpArguments.getGain(), sdrTcpArguments.getSamplerateHz(), sdrTcpArguments.getFrequencyHz(), sdrTcpArguments.getPort(), sdrTcpArguments.getPpm(), sdrTcpArguments.getBiasT(),sdrTcpArguments.getAddress(), path)) {
                            error = new SdrException(SdrException.EXIT_UNKNOWN);
                        }
                    }
                } catch (Throwable e) {
                    error = e;
                } finally {
                    // libusb_wrap_sys_device() does not own the fd. Close it before
                    // listeners may start a new session on the same USB device.
                    if (connection != null) {
                        connection.close();
                    }
                }
                announceOnClosed(error);
                dispose();
            }
        }.start();
    }

    @Override
    public void close() {
        closeRequested = true;
        synchronized (nativeLock) {
            if (disposed) return;
            if (!started) {
                // The session never started (for example it was superseded in
                // the service queue): release the native handler right away.
                disposed = true;
                deInit(nativeHandler);
            } else {
                close(nativeHandler);
            }
        }
    }

    /** Releases the native session exactly once, regardless of which thread
     *  finishes the session first. */
    private void dispose() {
        synchronized (nativeLock) {
            if (disposed) return;
            disposed = true;
            deInit(nativeHandler);
        }
    }

    @Override
    public String getName() {
        return "rtl-sdr "+usbDevice.getDeviceName();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }

    @Override
    public native int[] getSupportedCommands();

    private native long initialize();
    private native void close(long pointer);
    private native void deInit(long pointer);
    private native boolean openAsync(long pointer, int fd, int gain, long samplingrate, long frequency, int port, int ppm, int biast, String address, String devicePath) throws Exception;
}
