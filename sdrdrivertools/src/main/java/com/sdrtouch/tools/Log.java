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

import com.sdrtouch.core.UsedByJni;

import java.util.HashSet;
import java.util.Set;

public abstract class Log {
	private final static StringBuilder log = new StringBuilder();
	private final static Object locker = new Object();
	private final static Set<Callback> callbacks = new HashSet<>();
	
	public static void appendLine(String format, Object ... args) {
		appendLine(String.format(format, args));
	}

	@UsedByJni
	public static void appendLine(String what) {
		android.util.Log.d("RtlSdr", what);
		while (what.charAt(what.length()-1) == '\n') {
			what = what.substring(0, what.length()-1);
		}
		what+="\n";
		synchronized (locker) {
			log.append(what);
			for (final Log.Callback callback : callbacks) callback.onChanged();
		}
	}
	
	public static String getFullLog() {
		return log.toString();
	}
	
	public static void clear() {
		synchronized (locker) {
			log.setLength(0);
			log.trimToSize();
			for (final Log.Callback callback : callbacks) callback.onChanged();
		}
	}
	
	public static void registerCallback(final Log.Callback callback) {
		synchronized (locker) {
			callbacks.add(callback);
		}
	}
	
	public static void unregisterCallback(final Log.Callback callback) {
		synchronized (locker) {
			callbacks.remove(callback);
		}
	}
	
	public interface Callback {
		void onChanged();
	}
	
	private Log() {}
}