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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.sdrtouch.core.exceptions.SdrException.err_info;
import com.sdrtouch.rtlsdr.BinaryRunnerService.LocalBinder;
import com.sdrtouch.tools.Check;
import com.sdrtouch.tools.DialogManager;
import com.sdrtouch.tools.DialogManager.dialogs;
import com.sdrtouch.tools.Log;

import marto.rtl_tcp_andro.R;

public class StreamActivity extends FragmentActivity implements Log.Callback {

	private TextView terminal;
	private ScrollView scroll;
	private EditText arguments;
	private ToggleButton onoff;

	private static final int START_REQ_CODE = 1;
	
	private BinaryRunnerService service;
	private final ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder ibinder) {
			LocalBinder binder = (LocalBinder) ibinder;
			binder.registerCallback(serviceStatusCallback);
			service = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					onoff.setChecked(false);
				}
			});
			service = null;
		}
    };
    
    private final BinaryRunnerService.StatusCallback serviceStatusCallback = new BinaryRunnerService.StatusCallback() {
		@Override
		public void onServerRunning() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					onoff.setChecked(true);
				}
			});
		}
		
		@Override
		public void onServerNotRunning() {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					onoff.setChecked(false);
				}
			});
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stream);

		terminal = (TextView) findViewById(R.id.terminal);
		scroll = (ScrollView) findViewById(R.id.ScrollArea);
		arguments = (EditText) findViewById(R.id.commandline);
		
		terminal.setText(Log.getFullLog());
		
		findViewById(R.id.enable_gui).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				StreamActivity.this.findViewById(R.id.statusmsg).setVisibility(View.GONE);
				StreamActivity.this.findViewById(R.id.gui).setVisibility(View.VISIBLE);
			}
		});

		
		(onoff = (ToggleButton) findViewById(R.id.onoff)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Check.isNotNull(service);
				if (service.isRunning()) {
					service.closeService();
				} else {
					Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse("iqsrc://" + arguments.getText().toString()));
					intent.setClass(StreamActivity.this, DeviceOpenActivity.class);
					StreamActivity.this.startActivityForResult(intent, START_REQ_CODE);
				}
				onoff.setChecked(service.isRunning());
			}
		});

		findViewById(R.id.license).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View ignored) {
				showDialog(dialogs.DIAG_LICENSE);
			}
		});

		findViewById(R.id.copybutton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View ignored) {
				final String textToClip = terminal.getText().toString();
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("text label",textToClip);
				clipboard.setPrimaryClip(clip);
				Toast.makeText(getApplicationContext(), R.string.copied_to_clip, Toast.LENGTH_LONG).show();
			}
		});
		
		findViewById(R.id.clearbutton).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.clear();
			}
		});
		
		findViewById(R.id.help).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				StreamActivity.this.showDialog(dialogs.DIAG_ABOUT);
			}
		});

		if (!RtlSdrApplication.IS_PLATFORM_SUPPORTED) {
			((TextView) findViewById(R.id.warntext)).setText(R.string.platform_not_supported);
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
		} catch (Throwable t) {t.printStackTrace();}
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("args", arguments.getText().toString());
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		arguments.setText(savedInstanceState.getString("args"));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		terminal.setText(Log.getFullLog());
		bindService(new Intent(this, BinaryRunnerService.class), mConnection, Context.BIND_AUTO_CREATE);
		Log.registerCallback(this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		unbindService(mConnection);
		Log.unregisterCallback(this);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (requestCode == START_REQ_CODE) {
					if (resultCode == RESULT_OK)
						Log.appendLine("Starting was successful!");
					else {
						err_info einfo = err_info.unknown_error;
						try {
							einfo = err_info.values()[data.getIntExtra("marto.rtl_tcp_andro.RtlTcpExceptionId", err_info.unknown_error.ordinal())];
						} catch (Throwable ignored) {
						}
						Log.appendLine("ERROR STARTING! Reason: " + einfo);
					}
				}
			}
		});
	}
	
	@Override
	public void onChanged() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				terminal.setText(Log.getFullLog());
				scroll.pageScroll(ScrollView.FOCUS_DOWN);
			}
		});
	}
}
