/*
 * rtl_tcp_andro is an Android port of the famous rtl_tcp driver for 
 * RTL2832U based USB DVB-T dongles. It does not require root.
 * Copyright (C) 2012 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package marto.rtl_tcp_andro;

import java.util.HashSet;

import marto.rtl_tcp_andro.tools.BinaryRunnerService;
import marto.rtl_tcp_andro.tools.DialogManager;
import marto.rtl_tcp_andro.tools.ProcessRunner.OnProcessSaidWord;
import marto.rtl_tcp_andro.tools.RtlTcpStartException;
import marto.rtl_tcp_andro.tools.StrRes;
import marto.rtl_tcp_andro.tools.UsbPermissionHelper;
import marto.rtl_tcp_andro.tools.RtlTcpStartException.err_info;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import static marto.rtl_tcp_andro.StreamActivity.DISABLE_JAVA_FIX_PREF;
import static marto.rtl_tcp_andro.StreamActivity.PREFS_NAME;

public class DeviceOpenActivity extends FragmentActivity implements OnProcessSaidWord {
	
	private final static String TAG = "rtl_tcp_andro";
	
	public static Intent intent = null;
	private static DeviceOpenActivity mostrecent_activity = null;
	private String arguments;
	
	private HashSet<OnProcessSaidWord> registeredcallbacks = new HashSet<OnProcessSaidWord>();
	
	public static PendingIntent permissionIntent;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.progress);
		
		final SharedPreferences pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		UsbPermissionHelper.global_disable_java_fix = pref.getBoolean(DISABLE_JAVA_FIX_PREF, false);	
		android.util.Log.d("rtl_tcp_andro", "On opening prefs are "+pref.getBoolean(DISABLE_JAVA_FIX_PREF, false));
		
		StrRes.res = getResources();
		
		final Uri data = getIntent().getData();
		arguments = data.toString().replace("iqsrc://", ""); // quick and dirty fix; me don't like it
		android.util.Log.d("rtl_tcp_andro", "Args: "+arguments);
		
		mostrecent_activity = this;
		
		intent = getIntent();

		// For auto play
		try {
			final UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			if (device != null)
				openDevice(device);
		} catch (Throwable e) {}
		


	}
	
	@Override
	protected void onStart() {
		super.onStart();
		
		if (BinaryRunnerService.lastservice != null) {
			BinaryRunnerService.lastservice.stopSelf();
			try {Thread.sleep(50);} catch (Throwable e) {};
		}
		
		if (BinaryRunnerService.lastservice != null)
			finishWithError(err_info.already_running);
		
		try {
			permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), 0);
			registerReceiver(mUsbReceiver, new IntentFilter("com.android.example.USB_PERMISSION"));
		} catch (Throwable e) {
			UsbPermissionHelper.global_disable_java_fix = true;
		}
		
		try {
			UsbPermissionHelper.findDevice(DeviceOpenActivity.this, false);
		} catch (Exception e) {
			finishWithError(e);
		}
		
		BinaryRunnerService.registerWordCallback(this, "listening");
		registerError("-6", err_info.replug);
		registerError("-3", err_info.permission_denied);
		registerError("No supported devices found", err_info.no_devices_found);
	}
	
	private void registerError(final String text, final err_info err) {
		final OnProcessSaidWord callback = new OnProcessSaidWord() {
			
			@Override
			public void OnProcessSaid(String line) {
				finishWithError(err);
			}
			
			@Override
			public void OnClosed(int exitvalue) {}
		};
		
		
		BinaryRunnerService.registerWordCallback(callback, text);
		
		registeredcallbacks.add(callback);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		try {
			unregisterReceiver(mUsbReceiver);
		} catch (Throwable e) {};
		
		BinaryRunnerService.unregisterWordCallback(this);
		for (final OnProcessSaidWord callback : registeredcallbacks) BinaryRunnerService.unregisterWordCallback(callback);
		registeredcallbacks.clear();
	}
	
	public static void showDialogStatic(final DialogManager.dialogs id, final String ... args) {
		if (mostrecent_activity != null)
			mostrecent_activity.showDialog(id, args);
	}
	
	public void showDialog(final DialogManager.dialogs id, final String ... args) {

		final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		final Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		// Create and show the dialog.
		final DialogFragment newFragment = DialogManager.invokeDialog(id, args);
		try {
			newFragment.show(ft, "dialog");
		} catch (Throwable t) {t.printStackTrace();};
	}
	
	/**
	 * Opens a certain USB device and prepares an argument to be passed to libusb
	 * @param device
	 */
	@SuppressLint("NewApi")
	public void openDevice(final UsbDevice device) {
		final UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		
		if (device != null && !manager.hasPermission(device)) {
			android.util.Log.d("rtl_tcp_andro", "No permissions for device, requesting!");
			manager.requestPermission(device, permissionIntent);
			return;
		}
		
		if (device == null || !manager.hasPermission(device))
			finishWithError(err_info.permission_denied);

		final UsbDeviceConnection connection = manager.openDevice(device); 
		
		if (connection == null)
			finishWithError(err_info.unknown_error);

		final String address = getFilesDir().getAbsolutePath()+"/socket";
		UsbPermissionHelper.native_startUnixSocketServer(address, connection.getFileDescriptor());

		BinaryRunnerService.usbconnection = connection;
		startBinary(arguments + " -h "+address, false);	
	}
	
	/**
	 * Initializes open procedure without passing fds to libusb
	 */
	public void openDeviceUsingRoot() {
		android.util.Log.d("rtl_tcp_andro", "Opening with root!");
		startBinary(arguments, true);
	}
	
	/** 
	 * Starts the tcp binary
	 */
	public void startBinary(final String arguments, final boolean root) {
		try {
			//start the service
			final Intent i = new Intent(this, BinaryRunnerService.class);
			i.putExtra("exe", "rtl_tcp_andro");
			i.putExtra("args", arguments);
			i.putExtra("root", root);
			startService(i);
			
		} catch (Exception e) {
			finishWithError(e);
		}
	}
	
	/**
	 * Announce there is an error. The id of the error could be get with RtlTcpExceptionId
	 * from the parent activity. See {@link RtlTcpStartException.err_info}
	 * @param result
	 */
	public void finishWithError(int id) {
		final Intent data = new Intent();
		data.putExtra("marto.rtl_tcp_andro.RtlTcpExceptionId", id);
		
		if (getParent() == null) {
		    setResult(RESULT_CANCELED, data);
		} else {
		    getParent().setResult(RESULT_CANCELED, data);
		}
		finish();
	}
	
	public void finishWithError(final Exception e) {
		if ((e instanceof RtlTcpStartException))
			finishWithError(((RtlTcpStartException) e).getReason());
		else
			finishWithError();
	}
	
	public void finishWithError(final RtlTcpStartException.err_info info) {
		finishWithError(info.ordinal());
	}
	
	public void finishWithError() {
		finishWithError(-1);
	}
	
	private void finishWithSuccess() {
		final Intent data = new Intent();
		
		if (getParent() == null) {
		    setResult(RESULT_OK, data);
		} else {
		    getParent().setResult(RESULT_OK, data);
		}
		finish();
	}
	
	@Override
	public void finish() {
		Log.d(TAG, "RTL2832U returning back to caller!");
		super.finish();
	}


	/**
	 * Accepts permission
	 */
	public final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

		@SuppressLint("NewApi")
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			if ("com.android.example.USB_PERMISSION".equals(action)) {
				synchronized (this) {
					final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if(device != null){
							openDevice(device);
						} else
							finishWithError(err_info.permission_denied);
					} 
					else {
						finishWithError(err_info.permission_denied);
					}
				}
			}

		}
	};

	@Override
	public void OnProcessSaid(String line) {
		android.util.Log.d("rtl_tcp_andro", "Said line! "+line);
		finishWithSuccess();
	}

	@Override
	public void OnClosed(int exitvalue) {
		android.util.Log.d("rtl_tcp_andr", "Exited :(");
		finishWithError(exitvalue);
	}
}
