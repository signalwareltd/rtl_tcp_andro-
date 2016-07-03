/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
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

import com.sdrtouch.core.exceptions.SdrException;
import com.sdrtouch.tools.Log;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.rtlsdr.RtlSdrApplication;
import com.sdrtouch.tools.UsbPermissionObtainer;

import java.util.concurrent.ExecutionException;

public class RtlSdrDevice extends SdrDevice {
    private final UsbDevice usbDevice;
    private final long nativeHandler;

    public RtlSdrDevice(UsbDevice usbDevice) {
        this.usbDevice = usbDevice;
        this.nativeHandler = initialize();
    }

    @Override
    public void openAsync(Context ctx, final SdrTcpArguments sdrTcpArguments) {
        new Thread() {
            @Override
            public void run() {
                try {
                    int fd = openSessionAndGetFd();
                    String path = usbDevice.getDeviceName();
                    if (!openAsync(nativeHandler, fd, sdrTcpArguments.getGain(), sdrTcpArguments.getSamplerateHz(), sdrTcpArguments.getFrequencyHz(), sdrTcpArguments.getPort(), sdrTcpArguments.getPpm(), sdrTcpArguments.getAddress(), path)) {
                        announceOnClosed(new SdrException(SdrException.EXIT_UNKNOWN));
                    } else {
                        announceOnClosed(null);
                    }
                } catch (Throwable e) {
                    announceOnClosed(e);
                }
            }
        }.start();
    }

    @Override
    public void close() {
        close(nativeHandler);
    }

    @Override
    public String getName() {
        return "rtl-sdr "+usbDevice.getDeviceName();
    }

    private int openSessionAndGetFd() throws ExecutionException, InterruptedException {
        UsbDeviceConnection deviceConnection = UsbPermissionObtainer.obtainFdFor(RtlSdrApplication.getAppContext(), usbDevice).get();
        if (deviceConnection == null) throw new RuntimeException("Could not get a connection");
        int fd = deviceConnection.getFileDescriptor();
        Log.appendLine("Opening fd "+fd);
        return fd;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        deInit(nativeHandler);
    }

    private native long initialize();
    private native void close(long pointer);
    private native void deInit(long pointer);
    private native boolean openAsync(long pointer, int fd, int gain, long samplingrate, long frequency, int port, int ppm, String address, String devicePath) throws RtlSdrException;

    @Override
    public native int[] getSupportedCommands();
}
