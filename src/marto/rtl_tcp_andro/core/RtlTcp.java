package marto.rtl_tcp_andro.core;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;

public class RtlTcp {
	

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
	
	private volatile static boolean running = false;
	private final static Object locker = new Object();
	private final static Object exitcode_locker = new Object();
	private final static HashSet<OnProcessTalkCallback> talk_callacks = new HashSet<RtlTcp.OnProcessTalkCallback>();
	
	private static volatile int exitcode = EXIT_UNKNOWN;
	private static volatile AtomicBoolean exitcode_set = new AtomicBoolean(false);
	
	static {
        System.loadLibrary("RtlTcp");
    }
	
	private static native void open(final String args);// throws RtlTcpException;
	private static native void close();// throws RtlTcpException;
	
	private static void printf_receiver(final String data) {
		Log.d("RTLTCP", data);
		
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
	}
	
	private static void printf_stderr_receiver(final String data) {
		Log.e("RTLTCP", data);
		
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnProcessTalk(data);
	}
	
	private static void onclose(int exitcode) {
		Log.d("RTLTCP", "onClose: "+exitcode+" - "+RtlTcpException.translateToString(exitcode));
		RtlTcp.exitcode = exitcode;
		exitcode_set.set(true);
		synchronized (exitcode_locker) {
			exitcode_locker.notifyAll();
		}
	}
	
	private static void onopen() {
		Log.d("RTLTCP", "opened!");
		for (final OnProcessTalkCallback c : talk_callacks)
			c.OnOpened();
	}
	
	public static void registerWordCallback(final OnProcessTalkCallback callback) {
		talk_callacks.add(callback);
	}
	
	public static void unregisterWordCallback(final OnProcessTalkCallback callback) {
		talk_callacks.remove(callback);
	}
	
	
	public static void start(final String args) throws RtlTcpException {
		if (running) {
			close();
			try {
				synchronized (locker) {
					locker.wait(1000);
				}
			} catch (InterruptedException e) {}
			
			if (running) throw new RtlTcpException(EXIT_CANNOT_RESTART);
		}

		new Thread() {
			public void run() {
				exitcode_set.set(false);
				exitcode = EXIT_UNKNOWN;
				
				running = true;
				open(args);
				running = false;
				
				if (!exitcode_set.get()) {
					try {
						synchronized (exitcode_locker) {
							exitcode_locker.wait(1000);
						}
					} catch (InterruptedException e) {}
				}
				
				if (!exitcode_set.get())
					exitcode = EXIT_CANNOT_CLOSE;

				RtlTcpException e = null;
				if (exitcode != EXIT_OK) e = new RtlTcpException(exitcode);

				for (final OnProcessTalkCallback c : talk_callacks)
					c.OnClosed(exitcode, e);

				synchronized (locker) {
					locker.notifyAll();
				}
			};
		}.start();
	}
	
	public static void stop() {
		if (!running) return;
		close();
	}
	
	public static interface OnProcessTalkCallback {
		/** Whenever the process writes something to its stdout, this will get called */
		void OnProcessTalk(final String line);

		void OnClosed(final int exitvalue, final RtlTcpException e);

		void OnOpened();
	}
	
}
