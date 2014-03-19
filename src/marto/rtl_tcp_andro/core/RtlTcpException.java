package marto.rtl_tcp_andro.core;

import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.tools.StrRes;

public class RtlTcpException extends Exception {
	
	private static final long serialVersionUID = 9112234577039075951L;
	public final int id;

	static final String translateToString(final int id) {
		switch (id) {
		case RtlTcp.LIBUSB_ERROR_IO:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_IO");
		case RtlTcp.LIBUSB_ERROR_INVALID_PARAM:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_INVALID_PARAM");
		case RtlTcp.LIBUSB_ERROR_ACCESS:
			return StrRes.get(R.string.exception_LIBUSB_ERROR_ACCESS);
		case RtlTcp.LIBUSB_ERROR_NO_DEVICE:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NO_DEVICE");
		case RtlTcp.LIBUSB_ERROR_NOT_FOUND:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NOT_FOUND");
		case RtlTcp.LIBUSB_ERROR_BUSY:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_BUSY");
		case RtlTcp.LIBUSB_ERROR_TIMEOUT:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_TIMEOUT");
		case RtlTcp.LIBUSB_ERROR_OVERFLOW:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_OVERFLOW");
		case RtlTcp.LIBUSB_ERROR_PIPE:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_PIPE");
		case RtlTcp.LIBUSB_ERROR_INTERRUPTED:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_INTERRUPTED");
		case RtlTcp.LIBUSB_ERROR_NO_MEM:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NO_MEM");
		case RtlTcp.LIBUSB_ERROR_NOT_SUPPORTED:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_NOT_SUPPORTED");
		case RtlTcp.LIBUSB_ERROR_OTHER:
			return StrRes.get(R.string.exception_LIBUSB_GENERIC, "LIBUSB_ERROR_OTHER");
		
		case RtlTcp.EXIT_OK:
			return StrRes.get(R.string.exception_OK);
		case RtlTcp.EXIT_WRONG_ARGS:
			return StrRes.get(R.string.exception_WRONG_ARGS);
		case RtlTcp.EXIT_INVALID_FD:
			return StrRes.get(R.string.exception_INVALID_FD);
		case RtlTcp.EXIT_NO_DEVICES:
			return StrRes.get(R.string.exception_NO_DEVICES);
		case RtlTcp.EXIT_FAILED_TO_OPEN_DEVICE:
			return StrRes.get(R.string.exception_FAILED_TO_OPEN_DEVICE);
		case RtlTcp.EXIT_CANNOT_RESTART:
			return StrRes.get(R.string.exception_CANNOT_RESTART);
		case RtlTcp.EXIT_CANNOT_CLOSE:
			return StrRes.get(R.string.exception_CANNOT_CLOSE);
		case RtlTcp.EXIT_UNKNOWN:
			return StrRes.get(R.string.exception_UNKNOWN);
		case RtlTcp.EXIT_SIGNAL_CAUGHT:
			return StrRes.get(R.string.exception_SIGNAL_CAUGHT);


		default:
			return StrRes.get(R.string.exception_DEFAULT, id);
		}
	}
	
	public RtlTcpException(final int id) {
		super(translateToString(id));
		this.id = id;
	}

}
