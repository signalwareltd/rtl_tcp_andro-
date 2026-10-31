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

package com.sdrtouch.rtlsdr;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import com.sdrtouch.tools.Log;

public class UsbDelegate extends Activity {

    private static final String TAG = UsbDelegate.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        Log.appendLine(TAG + " onCreate action=" + action);

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (usbDevice == null) {
                Log.appendLine(TAG + " USB attached but EXTRA_DEVICE is null");
            } else {
                Log.appendLine(TAG + " USB attached: " + usbDevice.getDeviceName());
                Intent newIntent = new Intent(BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED);
                newIntent.putExtra(UsbManager.EXTRA_DEVICE, usbDevice);

                ResolveInfo resolved = getPackageManager().resolveActivity(newIntent, 0);
                Log.appendLine(TAG + " " + BinaryRunnerService.ACTION_SDR_DEVICE_ATTACHED
                        + " resolves to " + (resolved == null
                        ? "nothing"
                        : resolved.activityInfo.packageName + "/" + resolved.activityInfo.name));

                try {
                    startActivity(newIntent);
                    Log.appendLine(TAG + " attach intent dispatched");
                } catch (Throwable e) {
                    Log.appendLine(TAG + " no activity found for SDR handling: " + e);
                }
            }
        } else if (action != null) {
            Log.appendLine(TAG + " unhandled action " + action);
        }

        finish();
    }
}
