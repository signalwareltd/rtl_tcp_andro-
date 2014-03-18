package marto.rtl_tcp_andro.core;

import java.util.HashSet;
import java.util.Hashtable;

import android.util.Log;

public class RtlTcp {
	
	private volatile static boolean running = false;
	private final static Object locker = new Object();
	private final static Hashtable<String, HashSet<OnProcessSaidWord>> callbacks = new Hashtable<String, HashSet<OnProcessSaidWord>>();
	private final static HashSet<OnProcessTalkCallback> talk_callacks = new HashSet<RtlTcp.OnProcessTalkCallback>();
	
	
	static {
        System.loadLibrary("RtlTcp");
    }
	
	private static native int open(final String args);// throws RtlTcpException;
	private static native void close();// throws RtlTcpException;
	
	private static void printf_receiver(final String data) {
		Log.d("RTLTCP", data);
		
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
		
		for (final String k : callbacks.keySet())
			if (data.contains(k)) {
				final HashSet<OnProcessSaidWord> wordcallbacks = callbacks.get(k);
				if (wordcallbacks != null)
					for (final OnProcessSaidWord callback : wordcallbacks)
						callback.OnProcessSaid(data);
			}
	}
	
	private static void printf_stderr_receiver(final String data) {
		Log.e("RTLTCP", data);
		
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
		
		for (final String k : callbacks.keySet())
			if (data.contains(k)) {
				final HashSet<OnProcessSaidWord> wordcallbacks = callbacks.get(k);
				if (wordcallbacks != null)
					for (final OnProcessSaidWord callback : wordcallbacks)
						callback.OnProcessSaid(data);
			}
	}
	
	public static void registerWordCallback(final OnProcessTalkCallback callback) {
		talk_callacks.add(callback);
	}
	
	public static void registerWordCallback(final OnProcessSaidWord callback, final String word) {

		HashSet<OnProcessSaidWord> wordcallbacks = callbacks.get(word);
		if (wordcallbacks == null) wordcallbacks = new HashSet<RtlTcp.OnProcessSaidWord>();
		wordcallbacks.add(callback);
		
		callbacks.put(word, wordcallbacks);
	}
	
	public static void unregisterWordCallback(final OnProcessSaidWord callback) {
		for (final String k : callbacks.keySet()) {
			final HashSet<OnProcessSaidWord> wordcallbacks = callbacks.get(k);
			if (wordcallbacks != null) {
				wordcallbacks.remove(callback);
					callbacks.put(k, wordcallbacks);
			}
		}
	}
	
	public static void unregisterWordCallback(final OnProcessTalkCallback callback) {
		talk_callacks.remove(callback);
	}
	
	
	public static void start(final String args) throws RtlTcpException {
		if (running) {
			close();
			try {
				locker.wait(1000);
			} catch (InterruptedException e) {}
			
			if (running) throw new RtlTcpException("Cannot restart");
		}

		new Thread() {
			public void run() {
				running = true;
				final int exitvalue = open(args);
				running = false;

				for (final OnProcessTalkCallback c : talk_callacks)
					c.OnClosed(exitvalue);
				
				for (final String k : callbacks.keySet()) {
					final HashSet<OnProcessSaidWord> wordcallbacks = callbacks.get(k);
					for (final OnProcessSaidWord callback : wordcallbacks)
						callback.OnClosed(exitvalue);
				}

			};
		}.start();
	}
	
	public static void stop() {
		if (!running) return;
		close();
	}
	
	
	/**
	 * A callback whenever the process outputs a line containing a certain word onto its standard output or closes.
	 */
	public static interface OnProcessSaidWord {
		void OnProcessSaid(final String line);

		void OnClosed(final int exitvalue);

	}
	
	public static interface OnProcessTalkCallback {
		/** Whenever the process writes something to its stdout, this will get called */
		void OnProcessTalk(final String line);

		void OnClosed(final int exitvalue);

	}
	
}
