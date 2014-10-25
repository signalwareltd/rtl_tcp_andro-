package marto.rtl_tcp_andro.core;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.util.Log;

public class RtlTcp {
	private final static String TAG = "RTLTCP";

	final static int LIBUSB_ERROR_IO = -1;
	final static int LIBUSB_ERROR_INVALID_PARAM = -2;
	final static int LIBUSB_ERROR_ACCESS = -3;
	final static int LIBUSB_ERROR_NO_DEVICE = -4;
	final static int LIBUSB_ERROR_NOT_FOUND = -5;
	final static int LIBUSB_ERROR_BUSY = -6;
	final static int LIBUSB_ERROR_TIMEOUT = -7; 
	final static int LIBUSB_ERROR_OVERFLOW = -8;
	final static int LIBUSB_ERROR_PIPE = -9;
	final static int LIBUSB_ERROR_INTERRUPTED = -10;
	final static int LIBUSB_ERROR_NO_MEM = -11; 
	final static int LIBUSB_ERROR_NOT_SUPPORTED = -12;
	final static int LIBUSB_ERROR_OTHER = -99;
	
	final static int EXIT_OK = 0;
	final static int EXIT_WRONG_ARGS = 1;
	final static int EXIT_INVALID_FD = 2;
	final static int EXIT_NO_DEVICES = 3;
	final static int EXIT_FAILED_TO_OPEN_DEVICE = 4;
	final static int EXIT_CANNOT_RESTART = 5;
	final static int EXIT_CANNOT_CLOSE = 6;
	final static int EXIT_UNKNOWN = 7;
	final static int EXIT_SIGNAL_CAUGHT = 8;
	
	private volatile static AtomicBoolean running = new AtomicBoolean(false);
	private final static Object locker = new Object();
	private final static Object exitcode_locker = new Object();
	private final static ArrayList<OnProcessTalkCallback> talk_callacks = new ArrayList<RtlTcp.OnProcessTalkCallback>();
	
	private static volatile AtomicInteger exitcode = new AtomicInteger(EXIT_UNKNOWN);
	private static volatile AtomicBoolean exitcode_set = new AtomicBoolean(false);
	
	static {
        System.loadLibrary("RtlTcp");
    }
	
	private static native void open(final String args);// throws RtlTcpException;
	private static native void close();// throws RtlTcpException;
	
	private static void printf_receiver(final String data) {
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
		Log.d(TAG, data);
	}
	
	private static void printf_stderr_receiver(final String data) {
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
		Log.w(TAG, data);
	}
	
	private static void onclose(int exitcode) {
		RtlTcp.exitcode.set(exitcode);
		exitcode_set.set(true);
		synchronized (exitcode_locker) {
			exitcode_locker.notifyAll();
		}
		running.set(false);
		if (exitcode != EXIT_OK)
			Log.e(TAG, "Exitcode "+exitcode);
		else
			Log.d(TAG, "Exited with success");
	}
	
	private static void onopen() {
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnOpened();
		running.set(true);
		Log.d(TAG, "Device open");
	}
	
	public static void registerWordCallback(final OnProcessTalkCallback callback) {
		if (!talk_callacks.contains(callback)) talk_callacks.add(callback);
	}
	
	public static void unregisterWordCallback(final OnProcessTalkCallback callback) {
		talk_callacks.remove(callback);
	}
	
	
	public static void start(final String args) throws RtlTcpException {
		if (running.get()) {
			close();
			try {
				synchronized (locker) {
					locker.wait(5000);
				}
			} catch (InterruptedException e) {}

			if (running.get()) throw new RtlTcpException(EXIT_CANNOT_RESTART);
		}

		new Thread() {
			public void run() {
				exitcode_set.set(false);
				exitcode.set(EXIT_UNKNOWN);

				running.set(true);
				try {
					open(args);

					if (!exitcode_set.get()) {
						close();
						try {
							synchronized (exitcode_locker) {
								exitcode_locker.wait(1000);
							}
						} catch (InterruptedException e) {}
					}

					if (!exitcode_set.get())
						exitcode.set(EXIT_CANNOT_CLOSE);

					RtlTcpException e = null;
					final int exitcode = RtlTcp.exitcode.get();
					if (exitcode != EXIT_OK) e = new RtlTcpException(exitcode);

					for (final OnProcessTalkCallback c : talk_callacks)
						c.OnClosed(exitcode, e);

					synchronized (locker) {
						locker.notifyAll();
					}

				} finally {
					running.set(false);
				}
			};
		}.start();
	}
	
	public static void stop() {
		if (!running.get()) return;
		close();
	}
	
	public static boolean isRunning() {
		return running.get();
	}
	
	public static interface OnProcessTalkCallback {
		/** Whenever the process writes something to its stdout, this will get called */
		void OnProcessTalk(final String line);

		void OnClosed(final int exitvalue, final RtlTcpException e);

		void OnOpened();
	}
	
}
