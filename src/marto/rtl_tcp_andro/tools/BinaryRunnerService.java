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

import java.util.HashMap;
import java.util.LinkedList;

import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.StreamActivity;
import marto.rtl_tcp_andro.tools.ProcessRunner.OnProcessSaidWord;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

public class BinaryRunnerService extends Service {
	
	private static final String TAG = "rtl_tcp_andro";
	
	public static BinaryRunnerService lastservice = null;
	public static Object usbconnection = null; // the usb device that is open, try to close it actually when the service dies

	private static LinkedList<OnServiceTalkCallback> callbacks = new LinkedList<OnServiceTalkCallback>();
	private final static HashMap<OnProcessSaidWord, String> wordcallbacks = new HashMap<OnProcessSaidWord, String>();
	public static ProcessRunner pr = null;
	private final static StringBuilder log = new StringBuilder();
	private int startid;
	private PowerManager.WakeLock wl = null;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
	public void onStart(final Intent intent, final int startId) {
		this.startid = startId;
		super.onStart(intent, startId);

		if (lastservice != null)
			lastservice.stopSelf(lastservice.startid);

		lastservice = this;

		log.delete(0, log.length());

		final Bundle data = intent.getExtras();

		final String exename = data.getString("exe");
		final String args = data.getString("args");
		final boolean root = data.getBoolean("root");

		try {

			// close any previous attempts and try to reopen
			if (pr != null)
				pr.stop(getApplicationContext());

			try {
				wl = null;
				wl = ((PowerManager)getSystemService(
						Context.POWER_SERVICE)).newWakeLock(
								PowerManager.SCREEN_BRIGHT_WAKE_LOCK
								| PowerManager.ON_AFTER_RELEASE,
								TAG);
				wl.acquire();
				log("Ackquired wake lock. Will keep the screen on.");
			} catch (Throwable e) {e.printStackTrace();}

			if (root)
				log("#su");
			log("#"+exename+" "+args);

			pr = new ProcessRunner(
					new String[]{},
					exename, 
					args,
					getApplicationContext(), new ProcessRunner.OnProcessTalkCallback() {

						@Override
						public void OnProcessTalk(final String line) {
							log(line);
							log.append(line);
							log.append("\n");
						}

						@Override
						public void OnClosed(int exitvalue) {
							for (final OnServiceTalkCallback cb : callbacks) cb.OnClosed(exitvalue);
							log.append("exit "+exitvalue);
							stopSelf(startId);
						}
					}, root);
			for (final OnProcessSaidWord cb : wordcallbacks.keySet()) pr.registerWordCallback(cb, wordcallbacks.get(cb));
			pr.registerWordCallback(new OnProcessSaidWord() {
				
				@Override
				public void OnProcessSaid(String line) {
					stopSelf(startId);
				}
				
				@Override
				public void OnClosed(int exitvalue) {
					stopSelf(startId);
				}
			}, "exiting");
			
			pr.start();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		final Notification notification = new Notification(android.R.drawable.ic_media_play, getText(R.string.notif_title),
				System.currentTimeMillis());
		final Intent notificationIntent = new Intent(this, StreamActivity.class);
		final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(this, getText(R.string.notif_title),
				getText(R.string.notif_message), pendingIntent);
		startForeground(startId, notification);

	}
	
	public static void log(final String line) {
		for (final OnServiceTalkCallback cb : callbacks) cb.OnProcessTalk(line);
	}
	
	public static void registerCallback(final OnServiceTalkCallback callback) {
		callbacks.add(callback);
		callback.OnWholeLogDump(log.toString());
	}
	
	public static void unregisterCallback(final OnServiceTalkCallback callback) {
		callbacks.remove(callback);
	}
	
	public static void registerWordCallback(final OnProcessSaidWord callback, final String word) {
		wordcallbacks.put(callback, word);
		if (pr != null && pr.isRunning()) pr.registerWordCallback(callback, word);
	}
	
	public static void unregisterWordCallback(final OnProcessSaidWord callback) {
		wordcallbacks.remove(callback);
		if (pr != null && pr.isRunning()) pr.unregisterWordCallback(callback);
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onDestroy() {
		if (pr != null)
			pr.stop(getApplicationContext());
		
		try {
			final UsbDeviceConnection conn = (UsbDeviceConnection) usbconnection;
			conn.close();
		} catch (Throwable t) {};
		
		try {
			wl.release();
			log("Wake lock released.");
		} catch (Throwable t) {};
		
		lastservice = null;
		super.onDestroy();
	}
	
	public static interface OnServiceTalkCallback {
		/** Whenever the process writes something to its stdout, this will get called */
		void OnProcessTalk(final String line);
		
		void OnClosed(final int exitvalue);

		void OnWholeLogDump(final String log);
	}

}
