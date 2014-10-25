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

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;

import marto.rtl_tcp_andro.DeviceOpenActivity;
import marto.rtl_tcp_andro.R;
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
	
	public static boolean force_root = false; 
	
	public enum STATUS {SHOW_DEVICE_DIALOG, REQUESTED_OPEN_DEVICE, CANNOT_FIND, CANNOT_FIND_TRY_ROOT};
	
	/**
	 * Fixes permissions so that rtl_tcp could be run.
	 * @return any additional parameters that should be passed to rtl_tcp so that it gets the right permissions
	 * @throws Exception if device cannot be acquired
	 */
	public static STATUS findDevice(final DeviceOpenActivity ctx, final boolean root) throws RtlTcpStartException {

		if (!root && !force_root) {
			try {
				STATUS stat = fixJavaUSBAPIPermissions(ctx);
				if (stat != STATUS.CANNOT_FIND_TRY_ROOT) return stat;
			} catch (Exception e) {
				e.printStackTrace();
				if (e instanceof RtlTcpStartException) throw (RtlTcpStartException) e;
			}
		}

		try {
			ctx.openDeviceUsingRoot();
			return STATUS.REQUESTED_OPEN_DEVICE;
		} catch (Exception e) {
			e.printStackTrace();
			if (e instanceof RtlTcpStartException) throw (RtlTcpStartException) e;
		}

		return STATUS.CANNOT_FIND;
	}
	
	@SuppressLint("NewApi")
	private static STATUS fixJavaUSBAPIPermissions(final DeviceOpenActivity activity) throws Exception {

		try {
			final UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
			// try to see whether we got the device from intent

			UsbDevice device = (UsbDevice) DeviceOpenActivity.intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

			if (device == null) {
				// auto selection by enumeration
				final HashSet<String> allowed = getDeviceData(activity);
				final HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
				
				if (deviceList.isEmpty()) return STATUS.CANNOT_FIND;

				for (final String desc : deviceList.keySet()) {
					final UsbDevice candidate = deviceList.get(desc);
					final String candstr = "v"+candidate.getVendorId()+"p"+candidate.getProductId();
					if (allowed.contains(candstr)) {
						if (device != null) {
							// we have more than one device that matches, let the user choose
							return STATUS.SHOW_DEVICE_DIALOG;
						}
						device = candidate;
					}
				}

			}
			
			if (device == null) {
				return STATUS.CANNOT_FIND;
			} else {
				activity.openDevice(device);
				return STATUS.REQUESTED_OPEN_DEVICE;
			}
		} catch (Throwable e) {
			if (e instanceof RtlTcpStartException) throw (RtlTcpStartException) e;
		}

		return STATUS.CANNOT_FIND_TRY_ROOT;
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
						final Integer vendorId = Integer.valueOf( as.getAttributeValue(null, "vendor-id"), 16);
						final Integer productId = Integer.valueOf( as.getAttributeValue(null, "product-id"), 16);
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

	private static void chmodRecursive(final String dir, final OutputStreamWriter osw) throws IOException {
		osw.write("chmod 777 "+dir+"\n");
		osw.flush();
		
		final String[] files = new File(dir).list();
		if (files == null) return;
		for (final String s : files) {
			final String fname = dir + s; 
			final File f = new File(fname);
			
			if (f.isDirectory())
				chmodRecursive(fname+"/", osw);
			else {
				osw.write("chmod 777 "+fname+"\n");
				osw.flush();
			}
		}
	}
	
	public static void fixRootPermissions() throws RtlTcpStartException {
		Runtime runtime = Runtime.getRuntime();
		OutputStreamWriter osw = null;
		Process proc = null;
		try { // Run Script

			proc = runtime.exec("su");
			
			osw = new OutputStreamWriter(proc.getOutputStream());
			
			chmodRecursive("/dev/bus/usb/", osw);
			
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
		
		if (proc.exitValue() != 0) {
			Log.appendLine("Root refused to give permissions.");
			throw new RtlTcpStartException(err_info.permission_denied);
		}
	}

}
