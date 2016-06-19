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

import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.core.devices.SdrDeviceProvider;
import com.sdrtouch.rtlsdr.RtlSdrApplication;
import com.sdrtouch.tools.UsbPermissionHelper;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import marto.rtl_tcp_andro.R;

public class RtlSdrDeviceProvider implements SdrDeviceProvider {
    @Override
    public List<SdrDevice> listDevices(Context ctx, boolean forceRoot) {
        Set<UsbDevice> availableUsbDevices = UsbPermissionHelper.getAvailableUsbDevices(RtlSdrApplication.getAppContext(), R.xml.device_filter);
        List<SdrDevice> devices = new LinkedList<>();
        for (UsbDevice usbDevice : availableUsbDevices) devices.add(new RtlSdrDevice(usbDevice));
        return devices;
    }

    @Override
    public String getName() {
        return "RtlSdr";
    }
}
