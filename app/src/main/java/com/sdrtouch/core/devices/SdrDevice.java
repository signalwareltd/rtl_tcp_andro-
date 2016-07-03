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

package com.sdrtouch.core.devices;

import android.content.Context;
import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.UsedByJni;

import java.io.Serializable;
import java.util.LinkedList;

public abstract class SdrDevice implements Serializable {
	private static final long serialVersionUID = 6042726358096490615L;
	private final LinkedList<OnStatusListener> listeners = new LinkedList<>();
	
	/**
	 * Each listener can only hear OnClosed once from this SdrDevice. As soon as there is a single listener,
	 * the class becomes non-serializable! Keep it in mind!
	 */
	public void addOnStatusListener(OnStatusListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}
	
	/**
	 * Always call this when the rtl-tcp is no longer running
	 * @param e if the rtl-tcp stopped due to an exception or null if it was successful
	 */
	protected void announceOnClosed(Throwable e) {
		synchronized (listeners) {
			OnStatusListener listener;
			while ((listener = listeners.poll()) != null) listener.onClosed(e);
		}
	}
	
	/**
	 * Always call this when the rtl-tcp is ready to accept connections
	 */
	@UsedByJni
	protected void announceOnOpen() {
		synchronized (listeners) {
			for (OnStatusListener listener: listeners) listener.onOpen(this);
		}
	}

	public abstract int[] getSupportedCommands();
	
	/**
	 * This should return as soon as possible.
	 * Your implementation should notify the listers asynchronously if this has succeeded or not.
	 * This method should not throw an exception!
	 * @param ctx the android context
	 * @param sdrTcpArguments the startup arguments
	 */
	public abstract void openAsync(Context ctx, SdrTcpArguments sdrTcpArguments);
	
	/**
	 * When anyone asks to close the rtl-tcp. This implementation doesn't need to block until the device is closed.
	 * You must also call {@link #announceOnClosed(Throwable)} with a null argument to indicate successful closure.
	 */
	public abstract void close();
	
	/**
	 * Get a friendly name to be displayed to the user
	 */
	public abstract String getName();
	
	/**
	 * Note: This class is not serializable due to listeners rarely being serializable in real life.
	 * 
	 * @author martinmarinov
	 *
	 */
	public interface OnStatusListener {
		/** 
		 * When the rtl-tcp compatible service has been successfully started 
		 */
		void onOpen(SdrDevice sdrDevice);
		
		/**
		 * When the rtl-tcp compatible service has been destroyed.
		 * @param e can be null if running was successfull and there was no error
		 */
		void onClosed(Throwable e);
	}
}
