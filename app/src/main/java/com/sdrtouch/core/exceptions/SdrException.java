package com.sdrtouch.core.exceptions;

import com.sdrtouch.tools.StrRes;

import marto.rtl_tcp_andro.R;

public class SdrException extends Exception {
	
	public enum err_info {
		permission_denied,
		root_required,
		no_devices_found,
		unknown_error,
		replug,
		already_running
	}
	
	public final static int LIBUSB_ERROR_IO = -1;
	public final static int LIBUSB_ERROR_INVALID_PARAM = -2;
	public final static int ERROR_ACCESS = -3;
	public final static int LIBUSB_ERROR_NO_DEVICE = -4;
	public final static int LIBUSB_ERROR_NOT_FOUND = -5;
	public final static int LIBUSB_ERROR_BUSY = -6;
	public final static int LIBUSB_ERROR_TIMEOUT = -7; 
	public final static int LIBUSB_ERROR_OVERFLOW = -8;
	public final static int LIBUSB_ERROR_PIPE = -9;
	public final static int LIBUSB_ERROR_INTERRUPTED = -10;
	public final static int LIBUSB_ERROR_NO_MEM = -11; 
	public final static int LIBUSB_ERROR_NOT_SUPPORTED = -12;
	public final static int LIBUSB_ERROR_OTHER = -99;
	
	public final static int EXIT_OK = 0;
	public final static int EXIT_WRONG_ARGS = 1;
	public final static int EXIT_INVALID_FD = 2;
	public final static int EXIT_NO_DEVICES = 3;
	public final static int EXIT_FAILED_TO_OPEN_DEVICE = 4;
	public final static int EXIT_CANNOT_RESTART = 5;
	public final static int EXIT_CANNOT_CLOSE = 6;
	public final static int EXIT_UNKNOWN = 7;
	public final static int EXIT_SIGNAL_CAUGHT = 8;
	public final static int EXIT_NOT_ENOUGH_POWER = 9;
	public final static int EXIT_PLATFORM_NOT_SUPPORTED = 10;
	
	private static final long serialVersionUID = 9112234577039075951L;
	private final int id;
	private final err_info reason;

	private static String translateToString(final int id) {
		switch (id) {
		case LIBUSB_ERROR_IO:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_IO");
		case LIBUSB_ERROR_INVALID_PARAM:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_INVALID_PARAM");
		case ERROR_ACCESS:
			return StrRes.get(R.string.exception_LIBUSB_ERROR_ACCESS);
		case LIBUSB_ERROR_NO_DEVICE:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NO_DEVICE");
		case LIBUSB_ERROR_NOT_FOUND:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NOT_FOUND");
		case LIBUSB_ERROR_BUSY:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_BUSY");
		case LIBUSB_ERROR_TIMEOUT:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_TIMEOUT");
		case LIBUSB_ERROR_OVERFLOW:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_OVERFLOW");
		case LIBUSB_ERROR_PIPE:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_PIPE");
		case LIBUSB_ERROR_INTERRUPTED:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_INTERRUPTED");
		case LIBUSB_ERROR_NO_MEM:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NO_MEM");
		case LIBUSB_ERROR_NOT_SUPPORTED:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NOT_SUPPORTED");
		case LIBUSB_ERROR_OTHER:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_OTHER");
		
		case EXIT_OK:
			return StrRes.get(R.string.exception_OK);
		case EXIT_WRONG_ARGS:
			return StrRes.get(R.string.exception_WRONG_ARGS);
		case EXIT_INVALID_FD:
			return StrRes.get(R.string.exception_INVALID_FD);
		case EXIT_NO_DEVICES:
			return StrRes.get(R.string.exception_NO_DEVICES);
		case EXIT_FAILED_TO_OPEN_DEVICE:
			return StrRes.get(R.string.exception_FAILED_TO_OPEN_DEVICE);
		case EXIT_CANNOT_RESTART:
			return StrRes.get(R.string.exception_CANNOT_RESTART);
		case EXIT_CANNOT_CLOSE:
			return StrRes.get(R.string.exception_CANNOT_CLOSE);
		case EXIT_UNKNOWN:
			return StrRes.get(R.string.exception_UNKNOWN);
		case EXIT_SIGNAL_CAUGHT:
			return StrRes.get(R.string.exception_SIGNAL_CAUGHT);
		case EXIT_NOT_ENOUGH_POWER:
			return StrRes.get(R.string.exception_NOT_ENOUGH_POWER);
		case EXIT_PLATFORM_NOT_SUPPORTED:
			return StrRes.get(R.string.platform_not_supported);
		default:
			return StrRes.get(R.string.exception_DEFAULT, id);
		}
	}
	
	private static err_info toReason(final int id) {
		switch (id) {
		case LIBUSB_ERROR_IO:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_INVALID_PARAM:
			return err_info.unknown_error;
		case ERROR_ACCESS:
			return err_info.permission_denied;
		case LIBUSB_ERROR_NO_DEVICE:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_NOT_FOUND:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_BUSY:
			return err_info.already_running;
		case LIBUSB_ERROR_TIMEOUT:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_OVERFLOW:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_PIPE:
			return err_info.unknown_error;
		case LIBUSB_ERROR_INTERRUPTED:
			return err_info.unknown_error;
		case LIBUSB_ERROR_NO_MEM:
			return err_info.unknown_error;
		case LIBUSB_ERROR_NOT_SUPPORTED:
			return err_info.no_devices_found;
		case LIBUSB_ERROR_OTHER:
			return err_info.unknown_error;
		
		case EXIT_OK:
			return null;
		case EXIT_WRONG_ARGS:
			return err_info.unknown_error;
		case EXIT_INVALID_FD:
			return err_info.permission_denied;
		case EXIT_NO_DEVICES:
			return err_info.no_devices_found;
		case EXIT_FAILED_TO_OPEN_DEVICE:
			return err_info.no_devices_found;
		case EXIT_CANNOT_RESTART:
			return err_info.unknown_error;
		case EXIT_CANNOT_CLOSE:
			return err_info.replug;
		case EXIT_UNKNOWN:
			return err_info.unknown_error;
		case EXIT_SIGNAL_CAUGHT:
			return err_info.unknown_error;
		case EXIT_NOT_ENOUGH_POWER:
			return err_info.unknown_error;
		case EXIT_PLATFORM_NOT_SUPPORTED:
			return err_info.unknown_error;
		default:
			return null;
		}
	}
	
	public SdrException(final int id) {
		super(translateToString(id));
		this.id = id;
		this.reason = toReason(id);
	}
	
	public err_info getReason() {
		return reason;
	}
	
	public int getId() {
		return id;
	}
}
