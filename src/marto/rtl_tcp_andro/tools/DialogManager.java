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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import marto.rtl_tcp_andro.DeviceOpenActivity;
import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.tools.RtlTcpStartException.err_info;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Creates various dialogs
 * 
 * @author martinmarinov
 *
 */
public class DialogManager extends DialogFragment {
	
	/** List of possible dialogs to create */
	public enum dialogs {
		DIAG_LIST_USB,
		DIAG_ABOUT,
		DIAG_LICENSE
	}
	
	public static DialogFragment invokeDialog(final dialogs id, final String ... args) {
		final Bundle b = new Bundle();
		
		b.putInt("elements", args.length);
		for (int i = 0; i < args.length; i++) b.putString("e"+i, args[i].toString());
		
		b.putInt("id", id.ordinal());
		
		final DialogManager dmg = new DialogManager();
		dmg.setArguments(b);
		
		return dmg;
	}
	
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		final Bundle b = getArguments();
		final int elements = b.getInt("elements");
		final String[] args = new String[elements];
		for (int i = 0; i < elements; i++) args[i] = b.getString("e"+i);		
		final dialogs id = dialogs.values()[b.getInt("id")];
		
		Dialog dialog = createDialog(id, args);
		
		if (dialog != null)
			return dialog;
		else
			return new AlertDialog.Builder(getActivity())
		.setTitle(R.string.error)
		.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		})
		.setMessage(R.string.notsupported).create();
	}
	
	/**
	 * Add new dialogs here!
	 * @param id
	 * @param args
	 * @return
	 */
	private Dialog createDialog(final dialogs id, final String[] args) {
		switch (id) {
		case DIAG_LIST_USB:
			return genUSBDeviceDialog();
		case DIAG_ABOUT:
			final AlertDialog addd = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.help)
			.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			})
			.setMessage(Html.fromHtml(getString(R.string.help_info))).create();
			try {
				addd.setOnShowListener(new DialogInterface.OnShowListener() {
					
					@Override
					public void onShow(DialogInterface paramDialogInterface) {
						try {
							final TextView tv = (TextView) addd.getWindow().findViewById(android.R.id.message);
							if (tv != null) tv.setMovementMethod(LinkMovementMethod.getInstance());
							
						} catch (Exception e) {}
					}
				});
			} catch (Exception e) {}
			
			return addd;
		case DIAG_LICENSE:
			return new AlertDialog.Builder(getActivity())
			.setTitle("COPYING")
			.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			})
			.setMessage(readWholeStream("COPYING"))
			.create();
		}
		return null;
	}
	
	private String readWholeStream(final String asset_name) {
		
		final StringBuilder total = new StringBuilder();
		
		try {
			final InputStream in = getActivity().getAssets().open(asset_name);
			final BufferedReader r = new BufferedReader(new InputStreamReader(in));
			
			String line;
			while ((line = r.readLine()) != null) {
			    total.append(line);
			    total.append("\n");
			}
			in.close();
		} catch (IOException e) {}
		
		return total.toString(); 
	}
	
	@SuppressLint("NewApi")
	private Dialog genUSBDeviceDialog() {

		try {
			if (!(getActivity() instanceof DeviceOpenActivity)) return null;
			final DeviceOpenActivity sdrviewer = (DeviceOpenActivity) getActivity();

			final UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
			final HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
			if (deviceList.isEmpty()) {
				sdrviewer.finishWithError(err_info.no_devices_found);
				return null;
			}
			final String[] options = new String[deviceList.size()];
			int i = 0;
			for (final String s : deviceList.keySet())
				options[i++] = s;

			return new AlertDialog.Builder(getActivity())
			.setItems(options, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					final String selected = options[which];

					final UsbDevice selected_device = deviceList.get(selected);

					sdrviewer.openDevice(selected_device);
				}
			})
			.setOnCancelListener(new AlertDialog.OnCancelListener() {

				@Override
				public void onCancel(DialogInterface dialog) {
					sdrviewer.finishWithError(err_info.no_devices_found);
				}
			})
			.setTitle(R.string.choose_device)
			.create();
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}
}
