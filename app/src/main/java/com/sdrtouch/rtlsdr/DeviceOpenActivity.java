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

import static com.sdrtouch.rtlsdr.SdrDeviceProviderRegistry.SDR_DEVICE_PROVIDERS;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.core.devices.SdrDeviceProvider;
import com.sdrtouch.core.exceptions.SdrException;
import com.sdrtouch.core.exceptions.SdrException.err_info;
import com.sdrtouch.rtlsdr.driver.RtlSdrDevice;
import com.sdrtouch.tools.DeviceDialog;
import com.sdrtouch.tools.ExceptionTools;
import com.sdrtouch.tools.Log;

import java.util.ArrayList;
import java.util.List;

import marto.rtl_tcp_andro.R;

public class DeviceOpenActivity extends FragmentActivity implements DeviceDialog.OnDeviceDialog {
	private volatile SdrTcpArguments sdrTcpArguments;
	private volatile UsbDevice usbDevice;

	private volatile SdrServiceConnection mConnection;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.clear();

		if (!RtlSdrApplication.IS_PLATFORM_SUPPORTED) {
			finishWithError(new SdrException(SdrException.EXIT_PLATFORM_NOT_SUPPORTED));
			return;
		}
		
		setContentView(R.layout.progress);

		Intent intent = getIntent();
		final Uri data = intent.getData();
		try {
			sdrTcpArguments = SdrTcpArguments.fromString(data.toString().replace("iqsrc://", ""));
			if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
				usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				Log.appendLine("USB device: " + usbDevice.toString());
			} else {
				usbDevice = null;
			}
		} catch (IllegalArgumentException e) {
			finishWithError(e, err_info.unknown_error, SdrException.EXIT_WRONG_ARGS);
		}
	}

	
	@Override
	protected void onStart() {
		super.onStart();

		if (usbDevice != null) {
			Log.appendLine("onStart with USB device");
			startServer(new RtlSdrDevice(getApplicationContext(), usbDevice));
			return;
		}

		Log.appendLine("onStart");

		try {
			List<SdrDevice> availableSdrDevices;
			availableSdrDevices = new ArrayList<>();

			for (SdrDeviceProvider sdrDeviceProvider : SDR_DEVICE_PROVIDERS) {
				List<SdrDevice> devicesForProvider = sdrDeviceProvider.listDevices(getApplicationContext(), false);
				availableSdrDevices.addAll(devicesForProvider);
				Log.appendLine("%s: found %d device opening options", sdrDeviceProvider.getName(), devicesForProvider.size());
			}

			switch (availableSdrDevices.size()) {
				case 0:
					finishWithError(new SdrException(SdrException.EXIT_NO_DEVICES));
					break;
				case 1:
					Log.appendLine("Only 1 option available, no need to ask user. Opening %s", availableSdrDevices.get(0).getName());
					startServer(availableSdrDevices.get(0));
					break;
				default:
					Log.appendLine("%d options available. Asking user to pick.", availableSdrDevices.size());
					showDeviceSelectionDialog(availableSdrDevices);
					break;
			}
		} catch (Throwable t) {
			finishWithError(t);
		}
	}
	
	
	private void showDeviceSelectionDialog(List<SdrDevice> availableSdrDevices) {
		// We can serialize the list of SdrDevices here since none of the devices have any callbacks
		showDialog(DeviceDialog.invokeDialog(availableSdrDevices));
	}	
	
	@Override
	protected void onStop() {
		super.onStop();
		if (mConnection != null && mConnection.isBound()) {
			unbindService(mConnection);
		}
		
		usbDevice = null;
		mConnection = null;
	}
	
	public void showDialog(final DialogFragment dialog) {

		final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		final Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		// Create and show the dialog.
		try {
			dialog.show(ft, "dialog");
		} catch (Throwable t) {t.printStackTrace();}
	}
	
	/** 
	 * Starts the tcp binary
	 */
	public void startServer(final SdrDevice sdrDevice) {
		try {
			//start the service
			mConnection = new SdrServiceConnection(sdrDevice, sdrTcpArguments, this::finishWithError);
			sdrDevice.addOnStatusListener(onDeviceStatusListener);
			Intent serviceIntent = new Intent(this, BinaryRunnerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
				startService(serviceIntent);
			}
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
		} catch (Exception e) {
			finishWithError(e);
		}
	}

	@Override
	public void onDeviceDialogDeviceChosen(SdrDevice selected) {
		Log.appendLine("User has picked %s", selected.getName());
		startServer(selected);
	}


	@Override
	public void onDeviceDialogCanceled() {
		Log.appendLine("User has canceled the device selection dialog");
		finishWithError(new SdrException(SdrException.ERROR_ACCESS));
	}
	
	private final SdrDevice.OnStatusListener onDeviceStatusListener = new  SdrDevice.OnStatusListener() {
		@Override
		public void onOpen(SdrDevice sdrDevice) {
			finishWithSuccess(sdrDevice);
		}

		@Override
		public void onClosed(Throwable e) {
			finishWithError(e);
		}
		
	};
	
	// RETURNING RESULTS BELOW

	public void finishWithError(int id, Integer second_id, String msg) {
		final Intent data = new Intent();
		data.putExtra("marto.rtl_tcp_andro.RtlTcpExceptionId", id);
		
		if (second_id != null) data.putExtra("detailed_exception_code", second_id);
		if (msg != null) data.putExtra("detailed_exception_message", msg);
		
		if (getParent() == null) {
			setResult(RESULT_CANCELED, data);
		} else {
			getParent().setResult(RESULT_CANCELED, data);
		}
		finish();
	}
	
	public void finishWithError(final Throwable e) {
		if (e == null) {
			finishWithError();
			return;
		}
		if (e instanceof SdrException) {
			final SdrException rtlexception = (SdrException) e;
			finishWithError(rtlexception.getReason(), rtlexception.getId(), rtlexception.getMessage());
		} else {
			Log.appendLine("Caught exception "+ExceptionTools.getNicelyFormattedTrace(e));
			e.printStackTrace();
			finishWithError();
		}
	}

	public void finishWithError(final Throwable e, SdrException.err_info err_info, int id) {
		if (e == null) {
			finishWithError(err_info, id, null);
			return;
		}
		Log.appendLine("Caught exception "+ExceptionTools.getNicelyFormattedTrace(e));
		e.printStackTrace();
		finishWithError(err_info, id, e.getMessage());
	}
	
	public void finishWithError(final err_info info, Integer second_id, String msg) {
		if (info != null)
			finishWithError(info.ordinal(), second_id, msg);
		else
			finishWithError(second_id, msg);
	}
	
	public void finishWithError() {
		finishWithError(null, null);
	}
	
	public void finishWithError(Integer second_id, String msg) {
		finishWithError(-1, second_id, msg);
	}
	
	private void finishWithSuccess(SdrDevice sdrDevice) {
		final Intent data = new Intent();
		// SDR device
		data.putExtra(BinaryRunnerService.EXTRA_SUPPORTED_TCP_CMDS, sdrDevice.getSupportedCommands());
		data.putExtra(BinaryRunnerService.EXTRA_DEVICE_NAME, sdrDevice.getName());
		
		if (getParent() == null) {
			setResult(RESULT_OK, data);
		} else {
			getParent().setResult(RESULT_OK, data);
		}
		
		Log.appendLine("Device was open. Closing the prompt activity.");
		finish();
	}
}
