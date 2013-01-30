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

package marto.rtl_tcp_andro.tools;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;

import marto.rtl_tcp_andro.DeviceOpenActivity;
import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.tools.DialogManager.dialogs;
import marto.rtl_tcp_andro.tools.RtlTcpStartException.err_info;

import org.xmlpull.v1.XmlPullParser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.AttributeSet;
import android.util.Xml;

public class UsbPermissionHelper {
	
	private final static String TAG = "rtl_tcp_andro";
	
	static {
        System.loadLibrary("UsbPermissionHelper");
    }

	private final static Object locker = new Object();
	
	public static boolean global_disable_java_fix = false; 
	
	/**
	 * Fixes permissions so that rtl_tcp could be run.
	 * @return any additional parameters that should be passed to rtl_tcp so that it gets the right permissions
	 * @throws Exception if device cannot be acquired
	 */
	public static void findDevice(final DeviceOpenActivity ctx, final boolean disable_java_fix) throws Exception {

		if (!disable_java_fix && !global_disable_java_fix) {
			try {
				fixJavaUSBAPIPermissions(ctx);
				return;
			} catch (Exception e) {
				e.printStackTrace();
				if (e instanceof RtlTcpStartException) throw e;
			}
		}

		try {
			fixRootPermissions(ctx);
			return;
		} catch (Exception e) {
			e.printStackTrace();
			if (e instanceof RtlTcpStartException) throw e;
		}

		throw new RtlTcpStartException(err_info.permission_denied);
	}
	
	@SuppressLint("NewApi")
	private static void fixJavaUSBAPIPermissions(final DeviceOpenActivity activity) throws Exception {
		try {
			final UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
			UsbDevice device = null;

			// try to see whether we got the device from intent

			device = (UsbDevice) DeviceOpenActivity.intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);


			if (device == null) {
				// auto selection by enumeration
				final HashSet<String> allowed = getDeviceData(activity);
				final HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

				for (final String desc : deviceList.keySet()) {
					final UsbDevice candidate = deviceList.get(desc);
					final String candstr = "v"+candidate.getVendorId()+"p"+candidate.getProductId();
					if (allowed.contains(candstr)) {
						device = candidate;
						break;
					}
				}

				if (device == null) {
					DeviceOpenActivity.showDialogStatic(dialogs.DIAG_LIST_USB);
					return;
				}
			}

			if (device != null)
				activity.openDevice(device);


		} catch (Throwable e) {
			if (e instanceof RtlTcpStartException) throw (RtlTcpStartException) e;
			else
				throw new Exception(e);
		}
	}
	
	private static HashSet<String> getDeviceData(final Context ctx) {
		final HashSet<String> ans = new HashSet<String>();
		try {
			final XmlResourceParser xml = ctx.getResources().getXml(R.xml.device_filter);
			
			xml.next();
			int eventType;
			while ((eventType = xml.getEventType()) != XmlPullParser.END_DOCUMENT) {
				
				switch (eventType) {
				case XmlPullParser.START_TAG:
					if (xml.getName().equals("usb-device")) {
						final AttributeSet as = Xml.asAttributeSet(xml);
						final String vendorId = as.getAttributeValue(null, "vendor-id");
						final String productId = as.getAttributeValue(null, "product-id");
						ans.add("v"+vendorId+"p"+productId);
					}
					break;
				}
				xml.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return ans;
	}
	
	private static void fixRootPermissions(final DeviceOpenActivity activity) throws RtlTcpStartException {
		Runtime runtime = Runtime.getRuntime();
		OutputStreamWriter osw = null;
		Process proc = null;
		try { // Run Script

			proc = runtime.exec("su");
			
			osw = new OutputStreamWriter(proc.getOutputStream());
			osw.write("chmod -R 777 /dev/bus/usb/");
			osw.flush();
			osw.close();

		} catch (IOException ex) {
			throw new RtlTcpStartException(err_info.root_required);
		} finally {
			if (osw != null) {
				try {
					osw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		try {
			if (proc != null)
				proc.waitFor();
		} catch (InterruptedException e) {}
		
		if (proc.exitValue() != 0)
			throw new RtlTcpStartException(err_info.permission_denied);
		
		activity.openDevice();

	}

	
	public static native void native_startUnixSocketServer(final String address, int fd);
}
