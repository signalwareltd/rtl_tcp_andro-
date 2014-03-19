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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;

public class BinaryRunnerService extends Service {
	
	private static final int MSG_START = 0;
	private static final int MSG_STOP = 1;
	
	private static final String TAG = "rtl_tcp_andro";
	
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;

	private RtlTcp.OnProcessTalkCallback callback1;
	private final static StringBuilder log = new StringBuilder();
	private PowerManager.WakeLock wl = null;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	private final static class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(Message msg) {
			final int startid = msg.arg1;
			final BinaryRunnerService thiservice = (BinaryRunnerService) msg.obj;

			switch (msg.arg2) {
			case MSG_START:

				final Bundle data = msg.getData();

				final String exename = data.getString("exe");
				final String args = data.getString("args");
				final boolean root = data.getBoolean("root");

				try {

					// close any previous attempts and try to reopen
					RtlTcp.stop();

					try {
						thiservice.wl = null;
						thiservice.wl = ((PowerManager)thiservice.getSystemService(
								Context.POWER_SERVICE)).newWakeLock(
										PowerManager.SCREEN_BRIGHT_WAKE_LOCK
										| PowerManager.ON_AFTER_RELEASE,
										TAG);
						thiservice.wl.acquire();
						log.append("Ackquired wake lock. Will keep the screen on.\n");
					} catch (Throwable e) {e.printStackTrace();}

					if (root)
						log.append("#su\n");
					log.append("#"+exename+" "+args+"\n");

					RtlTcp.unregisterWordCallback(thiservice.callback1);
					RtlTcp.registerWordCallback(thiservice.callback1 = new RtlTcp.OnProcessTalkCallback() {

						@Override
						public void OnProcessTalk(final String line) {
							log.append("rtl-tcp: ");
							log.append(line);
							log.append("\n");
						}

						@Override
						public void OnClosed(int exitvalue, final RtlTcpException e) {
							if (e != null)
								log.append("Exit message: "+e.getMessage()+"\n");
							else
								log.append("Exit code: "+exitvalue+"\n");
							
							Message msg = thiservice.mServiceHandler.obtainMessage();
							msg.arg1 = startid;
							msg.arg2 = MSG_STOP;
							msg.obj = thiservice;
							thiservice.mServiceHandler.sendMessage(msg);
						}
					});

					// TODO! GAIN ROOT HERE if root
					RtlTcp.start(args);

				} catch (Exception e) {
					e.printStackTrace();
				}

				final Notification notification = new Notification(android.R.drawable.ic_media_play, thiservice.getText(R.string.notif_title),
						System.currentTimeMillis());
				final Intent notificationIntent = new Intent(thiservice, StreamActivity.class);
				final PendingIntent pendingIntent = PendingIntent.getActivity(thiservice, 0, notificationIntent, 0);
				notification.setLatestEventInfo(thiservice, thiservice.getText(R.string.notif_title),
						thiservice.getText(R.string.notif_message), pendingIntent);
				thiservice.startForeground(startid, notification);

				break;
			case MSG_STOP:
				thiservice.stopSelf(startid);
				break;
			}
		}
	}
	
	@Override
	public void onStart(final Intent intent, final int startId) {
		super.onStart(intent, startId);
		
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_DEFAULT);
	    thread.start();

	    // Get the HandlerThread's Looper and use it for our Handler
	    mServiceLooper = thread.getLooper();
	    mServiceHandler = new ServiceHandler(mServiceLooper);

		log.delete(0, log.length());
		
		Message msg = mServiceHandler.obtainMessage();
	    msg.arg1 = startId;
	    msg.arg2 = MSG_START;
	    msg.obj = this;
	    msg.setData(intent.getExtras());
	    mServiceHandler.sendMessage(msg);


	}
	
	@SuppressLint("NewApi")
	@Override
	public void onDestroy() {
		RtlTcp.stop();
		
		try {
			wl.release();
			log.append("Wake lock released.\n");
		} catch (Throwable t) {};
		
		super.onDestroy();
	}

}
