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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.core.devices.SdrDeviceProvider;
import com.sdrtouch.core.exceptions.SdrException;
import com.sdrtouch.core.exceptions.SdrException.err_info;
import com.sdrtouch.rtlsdr.BinaryRunnerService.LocalBinder;
import com.sdrtouch.rtlsdr.driver.RtlSdrDeviceProvider;
import com.sdrtouch.tools.DeviceDialog;
import com.sdrtouch.tools.ExceptionTools;
import com.sdrtouch.tools.Log;

import java.util.ArrayList;
import java.util.List;

import marto.rtl_tcp_andro.R;

public class DeviceOpenActivity extends FragmentActivity implements DeviceDialog.OnDeviceDialog {
	
	private final static SdrDeviceProvider[] SDR_DEVICE_PROVIDERS = new SdrDeviceProvider[] { new RtlSdrDeviceProvider() };
    
	private SdrTcpArguments sdrTcpArguments;
	private SdrDevice sdrDevice;

    private boolean isBound = false;
	private final ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder ibinder) {
			isBound = true;
			LocalBinder binder = (LocalBinder) ibinder;
            binder.startWithDevice(sdrDevice, sdrTcpArguments);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			isBound = false;
			finishWithError();
		}
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.clear();

		if (!RtlSdrApplication.IS_PLATFORM_SUPPORTED) {
			finishWithError(new SdrException(SdrException.EXIT_PLATFORM_NOT_SUPPORTED));
			return;
		}
		
		setContentView(R.layout.progress);
		
		final Uri data = getIntent().getData();
		try {
			sdrTcpArguments = SdrTcpArguments.fromString(data.toString().replace("iqsrc://", ""));
		} catch (IllegalArgumentException e) {
			finishWithError(e, err_info.unknown_error, SdrException.EXIT_WRONG_ARGS);
		}
	}

	
	@Override
	protected void onStart() {
		super.onStart();

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
		if (isBound) unbindService(mConnection);
		
		sdrDevice = null;
		sdrTcpArguments = null;
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
			this.sdrDevice = sdrDevice;
			sdrDevice.addOnStatusListener(onDeviceStatusListener);
			Intent serviceIntent = new Intent(this, BinaryRunnerService.class);
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
		data.putExtra("supportedTcpCommands", sdrDevice.getSupportedCommands());
		
		if (getParent() == null) {
		    setResult(RESULT_OK, data);
		} else {
		    getParent().setResult(RESULT_OK, data);
		}
		
		Log.appendLine("Device was open. Closing the prompt activity.");
		finish();
	}
}
