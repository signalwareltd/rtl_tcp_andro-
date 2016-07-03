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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Pair;
import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.core.devices.SdrDevice.OnStatusListener;
import com.sdrtouch.tools.Check;
import com.sdrtouch.tools.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class BinaryRunnerService extends Service {
	private static final String TAG = "rtl_tcp_andro";

    private PowerManager.WakeLock wl = null;

	private final IBinder mBinder = new LocalBinder();
	private boolean isRunning = false;
	private SdrDevice thisSdrDevice = null;
	private final Set<StatusCallback> statusCallbacks = new HashSet<>();
	private final Queue<Pair<SdrDevice, SdrTcpArguments>> workQueue = new LinkedList<>();
		
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startWithDevice();
		return START_NOT_STICKY;
	}
	
	private void addWork(SdrDevice sdrDevice, SdrTcpArguments arguments) {
		synchronized (workQueue) {
			Log.appendLine("Queueing");
			workQueue.add(new Pair<>(sdrDevice, arguments));
		}
	}
	
	private void startWithDevice() {
		Pair<SdrDevice, SdrTcpArguments> pair;
		synchronized (workQueue) {
			pair = workQueue.poll();
		}
		if (pair == null) {
			announceNotRunning();
			statusCallbacks.clear();
			stopSelf();
		} else {
			if (isRunning) Log.appendLine("Restarting");
			thisSdrDevice = pair.first;
			SdrTcpArguments sdrTcpArguments = pair.second;
			Log.appendLine("Arguments "+ sdrTcpArguments);
			
			Check.isNotNull(thisSdrDevice); Check.isNotNull(sdrTcpArguments);
			announceRunning(thisSdrDevice);
			thisSdrDevice.addOnStatusListener(onStatusListener);
			thisSdrDevice.openAsync(this, sdrTcpArguments);
		}
	}
	
	private final OnStatusListener onStatusListener = new OnStatusListener() {
		@Override
		public void onOpen(SdrDevice sdrDevice) {
			Log.appendLine("The rtl-tcp implementation is running and is ready to accept clients");
			ackquireWakeLock();
		}
		
		@Override
		public void onClosed(Throwable e) {
			if (e == null) {
				Log.appendLine("Successfully closed service");
			} else {
				Log.appendLine("Closed service due to exception "+e.getClass().getSimpleName()+": "+e.getMessage());
			}
			
			stopForeground(true);
			thisSdrDevice = null;

			try {
				if (wl != null) {
					wl.release();
					Log.appendLine("Wake lock released");
				}
			} catch (Throwable ignored) {}

            startWithDevice();
		}
	};
	
	@SuppressWarnings("deprecation")
	private void ackquireWakeLock() {
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
	}
	
	public void closeService() {
		if (isRunning) {
			Log.appendLine("Closing device");
			thisSdrDevice.close();
		}
	}
	
	public boolean isRunning() {
		return isRunning;
	}
	
	private void announceRunning(SdrDevice sdrDevice) {
		Log.appendLine("Starting service with device %s", sdrDevice.getName());
		isRunning = true;
		synchronized (statusCallbacks) {
			for (StatusCallback callback : statusCallbacks) callback.onServerRunning();
		}
	}
	
	private void announceNotRunning() {
		Log.appendLine("Closing service");
		isRunning = false;
		synchronized (statusCallbacks) {
			for (StatusCallback callback : statusCallbacks) callback.onServerNotRunning();
		}
	}
		
	public class LocalBinder extends Binder {
		public BinaryRunnerService getService() {
            return BinaryRunnerService.this;
        }
		
		public void registerCallback(StatusCallback callback) {
			synchronized (statusCallbacks) {
				statusCallbacks.add(callback);
				if (isRunning) callback.onServerRunning(); else callback.onServerNotRunning();
			}
		}
		
		public void startWithDevice(SdrDevice sdrDevice, SdrTcpArguments sdrTcpArguments) {
			Check.isNotNull(sdrDevice);
			Check.isNotNull(sdrTcpArguments);
			addWork(sdrDevice, sdrTcpArguments);
			if (!isRunning) startService(new Intent(getApplicationContext(), BinaryRunnerService.class));
			else closeService();
		}
    }
	
	public interface StatusCallback {
		void onServerRunning();
		void onServerNotRunning();
	}
}
