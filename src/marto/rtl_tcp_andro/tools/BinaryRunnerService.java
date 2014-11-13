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

import java.util.ArrayList;

import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.StreamActivity;
import marto.rtl_tcp_andro.core.RtlTcp;
import marto.rtl_tcp_andro.core.RtlTcpException;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

public class BinaryRunnerService extends Service {
	
	
	private static final String TAG = "rtl_tcp_andro";
	
	private final static int ONGOING_NOTIFICATION_ID = 2;

	private RtlTcp.OnProcessTalkCallback callback1;
	
	private final ArrayList<ExceptionListener> exception_listeners = new ArrayList<BinaryRunnerService.ExceptionListener>();
	private PowerManager.WakeLock wl = null;
	
	private final ArrayList<Exception> accummulated_errors = new ArrayList<Exception>();
	
	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		
		public BinaryRunnerService getService(final ExceptionListener listener) {
			if (!exception_listeners.contains(listener)) exception_listeners.add(listener);
			for (Exception e : accummulated_errors) {
				try {
					listener.onException(e);
				} catch (Throwable t) {};
			}
            return BinaryRunnerService.this;
        }
    }
	
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	public void start(final String args, final int fd, final String uspfs_path) {
		try {
			
			if (args == null) {
				Log.appendLine("Service did receive null argument.");
				stopForeground(true);
				System.exit(0);
				return;
			}

			accummulated_errors.clear();

			if (RtlTcp.isNativeRunning()) {
				Log.appendLine("Service is running. Stopping... You can safely start it again now.");
				final Exception e = new Exception("Service is running. Stopping...");
				for (final ExceptionListener listener : exception_listeners) listener.onException(e);
				if (e != null) accummulated_errors.add(e);
				RtlTcp.stop();
				try {
					Thread.sleep(500);
				} catch (Throwable t) {}
				stopForeground(true);
				System.exit(0);
				return;
			}

			// close any previous attempts and try to reopen


			try {
				wl = null;
				wl = ((PowerManager)getSystemService(
						Context.POWER_SERVICE)).newWakeLock(
								PowerManager.SCREEN_BRIGHT_WAKE_LOCK
								| PowerManager.ON_AFTER_RELEASE,
								TAG);
				wl.acquire();
				Log.appendLine("Acquired wake lock. Will keep the screen on.");
			} catch (Throwable e) {e.printStackTrace();}

			Log.appendLine("#rtl_tcp_andro "+args);

			RtlTcp.unregisterWordCallback(callback1);
			RtlTcp.registerWordCallback(callback1 = new RtlTcp.OnProcessTalkCallback() {

				@Override
				public void OnProcessTalk(final String line) {
					Log.appendLine("rtl-tcp: "+line+"\n");
				}

				@Override
				public void OnClosed(int exitvalue, final RtlTcpException e) {
					if (e != null)
						Log.appendLine("Exit message: "+e.getMessage()+"\n");
					else
						Log.appendLine("Exit code: "+exitvalue+"\n");

					for (final ExceptionListener listener : exception_listeners) listener.onException(e);
					if (e != null) accummulated_errors.add(e);

					stopSelf();
				}

				@Override
				public void OnOpened() {
					for (final ExceptionListener listener : exception_listeners) listener.onStarted();
					Log.announceStateChanged(true);
				}


			});

			RtlTcp.start(args, fd, uspfs_path);

		} catch (Exception e) {
			for (final ExceptionListener listener : exception_listeners) listener.onException(e);
			e.printStackTrace();
			accummulated_errors.add(e);
		}

		makeForegroundNotification();
	}

	@SuppressWarnings("deprecation")
	private void makeForegroundNotification() {
		try {
			Notification notification = new Notification(android.R.drawable.ic_media_play, getText(R.string.notif_title), System.currentTimeMillis());
			final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, StreamActivity.class), 0);
			notification.setLatestEventInfo(this, getText(R.string.notif_title),
					getText(R.string.notif_message), pendingIntent);
			startForeground(ONGOING_NOTIFICATION_ID, notification);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	public void stop() {
		stopSelf();
	}
	
	public void unregisterListener(final ExceptionListener listener) {
		exception_listeners.remove(listener);
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onDestroy() {
		RtlTcp.stop();
		
		stopForeground(true);
		
		Log.announceStateChanged(false);
		for (final ExceptionListener listener : exception_listeners) listener.onException(null);
		
		RtlTcp.unregisterWordCallback(callback1);
		
		try {
			wl.release();
			Log.appendLine("Wake lock released");
		} catch (Throwable t) {};
		
		super.onDestroy();
	}
	
	public static interface ExceptionListener {
		public void onException(final Exception e);
		public void onStarted();
	}
}
