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
	private final static StringBuilder log = new StringBuilder();
	private final ArrayList<LogListener> listeners = new ArrayList<BinaryRunnerService.LogListener>();
	private final ArrayList<ExceptionListener> exception_listeners = new ArrayList<BinaryRunnerService.ExceptionListener>();
	private PowerManager.WakeLock wl = null;
	
	private final ArrayList<Exception> accummulated_errors = new ArrayList<Exception>();
	
	private final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		public BinaryRunnerService getService(final LogListener listener) {
			listeners.add(listener);
            return BinaryRunnerService.this;
        }
		
		public BinaryRunnerService getService(final ExceptionListener listener) {
			exception_listeners.add(listener);
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
	
	public final static Intent buildStartIntent(final Context ctx, final String args) {
		final Intent intent = new Intent(ctx, BinaryRunnerService.class);
		intent.putExtra("args", args);
		return intent;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		final String args = intent.getStringExtra("args");

		try {
			
			log.delete(0, log.length());
			accummulated_errors.clear();

			// close any previous attempts and try to reopen
			RtlTcp.stop();

			try {
				wl = null;
				wl = ((PowerManager)getSystemService(
						Context.POWER_SERVICE)).newWakeLock(
								PowerManager.SCREEN_BRIGHT_WAKE_LOCK
								| PowerManager.ON_AFTER_RELEASE,
								TAG);
				wl.acquire();
				log.append("Ackquired wake lock. Will keep the screen on.\n");
			} catch (Throwable e) {e.printStackTrace();}

			log.append("#rtl_tcp_andro "+args+"\n");

			RtlTcp.unregisterWordCallback(callback1);
			RtlTcp.registerWordCallback(callback1 = new RtlTcp.OnProcessTalkCallback() {

				@Override
				public void OnProcessTalk(final String line) {
					final String logline = "rtl-tcp: "+line+"\n";
					
					for (final LogListener listener : listeners) listener.onLogLine(logline);
				}

				@Override
				public void OnClosed(int exitvalue, final RtlTcpException e) {
					
					String line;
					
					if (e != null)
						line = "Exit message: "+e.getMessage()+"\n";
					else
						line = "Exit code: "+exitvalue+"\n";
					

					log.append(line);
					
					for (final ExceptionListener listener : exception_listeners) listener.onException(e);
					for (final LogListener listener : listeners) listener.onLogLine(line);
					accummulated_errors.add(e);
					
					stopSelf();
				}

				@Override
				public void OnOpened() {
					for (final ExceptionListener listener : exception_listeners) listener.onStarted();
					for (final LogListener listener : listeners) listener.onStateChanged(true);
				}
				
				
			});

			// TODO! GAIN ROOT HERE if root
			RtlTcp.start(args);
			
		} catch (Exception e) {
			for (final ExceptionListener listener : exception_listeners) listener.onException(e);
			e.printStackTrace();
			accummulated_errors.add(e);
		}

		makeForegroundNotification();
		
		
		return START_STICKY;
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
	
	public void unregisterListener(final LogListener listener) {
		listeners.remove(listener);
	}
	
	public void unregisterListener(final ExceptionListener listener) {
		exception_listeners.remove(listener);
	}
	
	public String getLog() {
		return log.toString();
	}
	
	@SuppressLint("NewApi")
	@Override
	public void onDestroy() {
		RtlTcp.stop();
		
		stopForeground(true);
		
		for (final LogListener listener : listeners) listener.onStateChanged(false);
		for (final ExceptionListener listener : exception_listeners) listener.onException(null);
		
		try {
			wl.release();
			log.append("Wake lock released.\n");
		} catch (Throwable t) {};
		
		super.onDestroy();
	}

	public static interface LogListener {
		public void onLogLine(final String line);
		public void onStateChanged(final boolean state);
	}
	
	public static interface ExceptionListener {
		public void onException(final Exception e);
		public void onStarted();
	}
}
