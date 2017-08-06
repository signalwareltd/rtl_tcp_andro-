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

package com.sdrtouch.rtlsdr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.rtlsdr.driver.RtlSdrDevice;
import com.sdrtouch.tools.Log;

public class UsbDelegate extends Activity {

    private static final String TAG = UsbDelegate.class.getSimpleName();

    private SdrTcpArguments sdrTcpArguments;
    private SdrDevice sdrDevice;
    private UsbDevice usbDevice;

    private BinaryRunnerService service;
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            Log.appendLine(TAG + " onServiceConnected");
            BinaryRunnerService.LocalBinder binder = (BinaryRunnerService.LocalBinder) ibinder;
            service = binder.getService();
            binder.startWithDevice(sdrDevice, sdrTcpArguments);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.appendLine(TAG + " onServiceDisconnected");
            service = null;
        }
    };

    private final SdrDevice.OnStatusListener onDeviceStatusListener = new  SdrDevice.OnStatusListener() {
        @Override
        public void onOpen(SdrDevice sdrDevice) {
            Log.appendLine(TAG + " onOpen");

            Intent intent = new Intent(BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED);
            // USB device
            intent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);
            // SDR device
            intent.putExtra(BinaryRunnerService.EXTRA_DEVICE_NAME, sdrDevice.getName());
            intent.putExtra(BinaryRunnerService.EXTRA_SUPPORTED_TCP_CMDS, sdrDevice.getSupportedCommands());
            //TODO add supported gain values
            // TCP arguments
            intent.putExtra(BinaryRunnerService.EXTRA_GAIN, sdrTcpArguments.getGain());
            intent.putExtra(BinaryRunnerService.EXTRA_SAMPLERATE, sdrTcpArguments.getSamplerateHz());
            intent.putExtra(BinaryRunnerService.EXTRA_FREQUENCY, sdrTcpArguments.getFrequencyHz());
            intent.putExtra(BinaryRunnerService.EXTRA_ADDRESS, sdrTcpArguments.getAddress());
            intent.putExtra(BinaryRunnerService.EXTRA_PORT, sdrTcpArguments.getPort());
            intent.putExtra(BinaryRunnerService.EXTRA_PPM, sdrTcpArguments.getPpm());

            startActivityForResult(intent, 1234);
        }

        @Override
        public void onClosed(Throwable e) {
            Log.appendLine(TAG + " onClosed");
            finish();
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.appendLine(TAG + " onActivityResult: requestCode=" + requestCode + " resultCode=" + resultCode);
        if (requestCode != 1234) return; // This is the requestCode that was used with startActivityForResult
        if (resultCode != RESULT_OK) {
            //stop the service
            if (service != null && service.isRunning()) {
                service.closeService();
            }
        }
        finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            Log.appendLine(TAG + " USB attached");
            usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            sdrTcpArguments = SdrTcpArguments.fromString("-a 127.0.0.1 -p 1234 -s 2048000");
            sdrDevice = new RtlSdrDevice(usbDevice);
            sdrDevice.addOnStatusListener(onDeviceStatusListener);

            //start the service
            Intent serviceIntent = new Intent(this, BinaryRunnerService.class);
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.appendLine(TAG + " onStop");
        if (service != null) unbindService(mConnection);

        usbDevice = null;
        sdrDevice = null;
        sdrTcpArguments = null;
    }
}
