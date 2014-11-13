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

import marto.rtl_tcp_andro.core.RtlTcpException;
import marto.rtl_tcp_andro.tools.BinaryRunnerService;
import marto.rtl_tcp_andro.tools.BinaryRunnerService.LocalBinder;
import marto.rtl_tcp_andro.tools.DialogManager;
import marto.rtl_tcp_andro.tools.Log;
import marto.rtl_tcp_andro.tools.RtlTcpStartException;
import marto.rtl_tcp_andro.tools.StrRes;
import marto.rtl_tcp_andro.tools.DialogManager.dialogs;
import marto.rtl_tcp_andro.tools.RtlTcpStartException.err_info;
import marto.rtl_tcp_andro.tools.UsbPermissionHelper;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import static marto.rtl_tcp_andro.StreamActivity.DISABLE_JAVA_FIX_PREF;
import static marto.rtl_tcp_andro.StreamActivity.PREFS_NAME;

public class DeviceOpenActivity extends FragmentActivity implements BinaryRunnerService.ExceptionListener {
	
	private final static String DEFAULT_USPFS_PATH = "/dev/bus/usb";
	
	public static Intent intent = null;
	private String arguments;
	
	private BinaryRunnerService mService;
    private boolean mBound = false;
    
    private String args = null;
    private String uspfs_path = null;
    private int fd = -1;
	
	public static PendingIntent permissionIntent;
	
	private final ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LocalBinder binder = (LocalBinder) service;
            mService = binder.getService(DeviceOpenActivity.this);
            mBound = true;		
            mService.start(args, fd, uspfs_path);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBound = false;
			finishWithError();
		}
    };
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.progress);
		
		final SharedPreferences pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		UsbPermissionHelper.force_root = pref.getBoolean(DISABLE_JAVA_FIX_PREF, false);
		
		StrRes.res = getResources();
		
		final Uri data = getIntent().getData();
		arguments = data.toString().replace("iqsrc://", ""); // quick and dirty fix; me don't like it
		
		intent = getIntent();

	}

	
	@Override
	protected void onStart() {
		super.onStart();
		
		try {
			permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.android.example.USB_PERMISSION"), 0);
			registerReceiver(mUsbReceiver, new IntentFilter("com.android.example.USB_PERMISSION"));
		} catch (Throwable e) {
			UsbPermissionHelper.force_root = true;
		}
		
		try {
			UsbPermissionHelper.STATUS res = UsbPermissionHelper.findDevice(DeviceOpenActivity.this, false);

			switch (res) {
			case SHOW_DEVICE_DIALOG:
				showDialog(dialogs.DIAG_LIST_USB);
				break;
			case CANNOT_FIND:
				finishWithError(err_info.no_devices_found);
				break;
			default:
				break;
			}
			
		} catch (Exception e) {
			finishWithError(e);
		}
		
	}
	
	
	@Override
	protected void onStop() {
		super.onStop();
		
		try {
			unregisterReceiver(mUsbReceiver);
		} catch (Throwable e) {};
		
		if (mBound && mService != null) mService.unregisterListener(this);
		if (mBound) {
			mService = null;
			unbindService(mConnection);
		}
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
	
	private final static String properDeviceName(String deviceName) {
		if (deviceName == null) return DEFAULT_USPFS_PATH;
		deviceName = deviceName.trim();
		if (deviceName.isEmpty()) return DEFAULT_USPFS_PATH;
		
		final String[] paths = deviceName.split("/");
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < paths.length-2; i++)
			if (i == 0)
				sb.append(paths[i]);
			else
				sb.append("/"+paths[i]);
		final String stripped_name = sb.toString().trim();
		if (stripped_name.isEmpty())
			return DEFAULT_USPFS_PATH;
		else
			return stripped_name;
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

		startBinary(arguments, connection.getFileDescriptor(), properDeviceName(device.getDeviceName()));	
	}

	/**
	 * Initializes open procedure without passing fds to libusb
	 */
	public void openDeviceUsingRoot() {
		android.util.Log.d("rtl_tcp_andro", "Opening with root!");

		try {
			UsbPermissionHelper.fixRootPermissions();
		} catch (RtlTcpStartException e) {
			finishWithError(e);
		}

		startBinary(arguments, -1, null);
	}
	

	
	/** 
	 * Starts the tcp binary
	 */
	public void startBinary(final String arguments, final int fd, final String uspfs_path) {
		try {
			//start the service
			this.args = arguments;
			this.fd = fd;
			this.uspfs_path = uspfs_path;
			final Intent intent = new Intent(this, BinaryRunnerService.class);
			startService(intent);
			bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		} catch (Exception e) {
			finishWithError(e);
		}
	}
	
	/**
	 * Announce there is an error. The id of the error could be get with RtlTcpExceptionId
	 * from the parent activity. 
	 *
	 * @param id See {@link RtlTcpStartException.err_info}
	 * @param second_id  See {@link RtlTcpException.id}
	 * @param second_id  See {@link RtlTcpException#translateToString}
	 */
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
	
	public void finishWithError(final Exception e) {
		if (e == null) {
			finishWithError();
			return;
		}
		if ((e instanceof RtlTcpStartException))
			finishWithError(((RtlTcpStartException) e).getReason());
		else if (e instanceof RtlTcpException) {
			final RtlTcpException rtlexception = (RtlTcpException) e;
			finishWithError(rtlexception.getReason(), rtlexception.id, rtlexception.getMessage());
		} else
			finishWithError();
	}
	
	public void finishWithError(final RtlTcpStartException.err_info info) {
		finishWithError(info, null, null);
	}
	
	public void finishWithError(final RtlTcpStartException.err_info info, Integer second_id, String msg) {
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
	
	private void finishWithSuccess() {
		final Intent data = new Intent();
		
		if (getParent() == null) {
		    setResult(RESULT_OK, data);
		} else {
		    getParent().setResult(RESULT_OK, data);
		}
		finish();
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
						} else {
							finishWithError(err_info.permission_denied);
							Log.appendLine("Android OS granted permissions but device was lost (due to a bug?).");
						}
					} 
					else {
						finishWithError(err_info.permission_denied);
						Log.appendLine("Android OS refused giving permissions.");
					}
				}
			}

		}
	};

	@Override
	public void onException(Exception e) {
		if (e == null ){
			finishWithSuccess();
			return;
		}
		finishWithError(e);
	}


	@Override
	public void onStarted() {
		finishWithSuccess();
	}

}
