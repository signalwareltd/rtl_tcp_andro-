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

package com.sdrtouch.tools;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class UsbPermissionHelper {

	public final static boolean isAndroidUsbSupported;
	
	static {
		isAndroidUsbSupported = isAndroidUsbSupported();
	}
	
	private UsbPermissionHelper() {}

	private static boolean isAndroidUsbSupported() {
		try {
			Class.forName( "android.hardware.usb.UsbManager" );
			return true;
		} catch( ClassNotFoundException e ) {
			return false;
		}
	}

	/** This method is safe to be called from old Android versions */
	public static Set<UsbDevice> getAvailableUsbDevices(final Context ctx, int xmlResourceId) {
		Set<UsbDevice> usbDevices = new HashSet<>();
		if (isAndroidUsbSupported) {
			final UsbManager manager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);

			final HashSet<Pair<Integer, Integer>> allowed = getDeviceData(ctx.getResources(), xmlResourceId);
			final HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

			for (final Entry<String, UsbDevice> desc : deviceList.entrySet()) {
				UsbDevice candidate = desc.getValue();
				final Pair<Integer, Integer> candidatePair = new Pair<>(candidate.getVendorId(), candidate.getProductId());
				if (allowed.contains(candidatePair)) usbDevices.add(candidate);
			}
		}
		return usbDevices;
	}

	private static HashSet<Pair<Integer, Integer>> getDeviceData(final Resources resources, int xmlResourceId) {
		final HashSet<Pair<Integer, Integer>> ans = new HashSet<>();
		try {
			final XmlResourceParser xml = resources.getXml(xmlResourceId);

			xml.next();
			int eventType;
			while ((eventType = xml.getEventType()) != XmlPullParser.END_DOCUMENT) {

				if (eventType == XmlPullParser.START_TAG) {
					if (xml.getName().equals("usb-device")) {
						final AttributeSet as = Xml.asAttributeSet(xml);
						final Integer vendorId = parseInt(as.getAttributeValue(null, "vendor-id"));
						final Integer productId = parseInt(as.getAttributeValue(null, "product-id"));
						ans.add(new Pair<>(vendorId, productId));
					}
				}
				xml.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return ans;
	}

	private static Integer parseInt(String number) {
		if (number.startsWith("0x")) {
			return Integer.valueOf( number.substring(2), 16);
		} else {
			return Integer.valueOf( number, 10);
		}
	}
}
