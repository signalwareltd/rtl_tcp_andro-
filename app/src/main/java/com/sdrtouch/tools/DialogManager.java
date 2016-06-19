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
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import marto.rtl_tcp_andro.R;

/**
 * Creates various dialogs
 * 
 * @author martinmarinov
 *
 */
public class DialogManager extends DialogFragment {
	
	/** List of possible dialogs to create */
	public enum dialogs {
		DIAG_ABOUT,
		DIAG_LICENSE
	}
	
	public static DialogFragment invokeDialog(final dialogs id, final String ... args) {
		final Bundle b = new Bundle();
		
		b.putInt("elements", args.length);
		for (int i = 0; i < args.length; i++) b.putString("e"+i, args[i]);
		
		b.putInt("id", id.ordinal());
		
		final DialogManager dmg = new DialogManager();
		dmg.setArguments(b);
		
		return dmg;
	}
	
	@Override @NonNull
    public Dialog onCreateDialog( Bundle savedInstanceState) {
		final Bundle b = getArguments();
		final dialogs id = dialogs.values()[b.getInt("id")];
		
		Dialog dialog = createDialog(id);
		
		if (dialog != null)
			return dialog;
		else
			return new AlertDialog.Builder(getActivity())
		.setTitle(R.string.error)
		.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog1, int which) {
				dialog1.dismiss();
			}
		})
		.setMessage(R.string.notsupported).create();
	}

	private Dialog createDialog(final dialogs id) {
		switch (id) {
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

						} catch (Exception ignored) {
						}
					}
				});
			} catch (Exception ignored) {}
			
			return addd;
		case DIAG_LICENSE:
				return new AlertDialog.Builder(getActivity())
						.setTitle("License")
						.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						})
						.setMessage(readWholeStream(R.raw.license))
						.create();
		}
		return null;
	}

	private String readWholeStream(final int resoureId) {

		final StringBuilder total = new StringBuilder();

		try {
			final InputStream in = getActivity().getResources().openRawResource(resoureId);
			final BufferedReader r = new BufferedReader(new InputStreamReader(in));

			String line;
			while ((line = r.readLine()) != null) {
				total.append(line);
				total.append("\n");
			}
			in.close();
		} catch (IOException e) {
            e.printStackTrace();
        }

		return total.toString();
	}
}
