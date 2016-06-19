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

package com.sdrtouch.tools;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.sdrtouch.core.devices.SdrDevice;

import java.util.List;

import marto.rtl_tcp_andro.R;

public class DeviceDialog extends DialogFragment {
	private final static String SDR_DEVICE = "sdrDevice_%d";
	private final static String SDR_DEVICES_COUNT = "sdrDevices_count";
		
	public static DialogFragment invokeDialog(List<SdrDevice> devices) {
		final Bundle b = new Bundle();
		
		synchronized (devices) {
			b.putInt(SDR_DEVICES_COUNT, devices.size());
			for (int id = 0; id < devices.size(); id++) {
				b.putSerializable(String.format(SDR_DEVICE, id), Check.isNotNull(devices.get(id)));
			}
		}
		
		final DeviceDialog dmg = new DeviceDialog();
		dmg.setArguments(b);
		
		return dmg;
	}
	
	@Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		final OnDeviceDialog callback = (OnDeviceDialog) getActivity();
		final Bundle b = getArguments();
		final int devicesCount = b.getInt(SDR_DEVICES_COUNT);
		final SdrDevice[] devices = new SdrDevice[devicesCount];
		final String[] options = new String[devicesCount];
		for (int id = 0; id < devicesCount; id++) {
		    SdrDevice sdrDevice = (SdrDevice) Check.isNotNull(b.getSerializable(String.format(SDR_DEVICE, id)));
			devices[id] = sdrDevice;
			options[id] = sdrDevice.getName();
		}
		
		return new AlertDialog.Builder(getActivity())
		.setItems(options, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final SdrDevice selected = devices[which];
				callback.onDeviceDialogDeviceChosen(selected);
			}
		})
		.setTitle(R.string.choose_device)
		.create();
	}
	
	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		final OnDeviceDialog callback = (OnDeviceDialog) getActivity();
		callback.onDeviceDialogCanceled();
	}
	
	public interface OnDeviceDialog {
		void onDeviceDialogDeviceChosen(SdrDevice selected);
		void onDeviceDialogCanceled();
	}
}
