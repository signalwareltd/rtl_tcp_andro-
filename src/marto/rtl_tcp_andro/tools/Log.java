package marto.rtl_tcp_andro.tools;

import java.util.ArrayList;

public abstract class Log {
	private final static StringBuilder log = new StringBuilder();
	private final static Object locker = new Object();
	private final static ArrayList<Log.Callback> callbacks = new ArrayList<Log.Callback>();
	
	
	public static void appendLine(String what) {
		while (what.charAt(what.length()-1) == '\n') {
			what = what.substring(0, what.length()-1);
		}
		what+="\n";
		synchronized (locker) {
			log.append(what);
			for (final Log.Callback callback : callbacks) callback.onAppend(what);
		}
		
	}
	
	public static String getFullLog() {
		return log.toString();
	}
	
	public static void clear() {
		synchronized (locker) {
			log.setLength(0);
			log.trimToSize();
			for (final Log.Callback callback : callbacks) callback.onClear();
		}
	}
	
	public static void registerCallback(final Log.Callback callback) {
		synchronized (locker) {
			if (!callbacks.contains(callback)) callbacks.add(callback);
		}
	}
	
	public static void unregisterCallback(final Log.Callback callback) {
		synchronized (locker) {
			callbacks.remove(callback);
		}
	}
	
	public static void announceStateChanged(final boolean state) {
		synchronized (locker) {
			for (final Log.Callback callback : callbacks) callback.onServiceStatusChanged(state);
		}
	}
	
	public  static interface Callback {
		public void onClear();
		public void onAppend(final String str);
		public void onServiceStatusChanged(boolean on);
	}
}