package marto.rtl_tcp_andro.core;

import marto.rtl_tcp_andro.R;
import marto.rtl_tcp_andro.tools.RtlTcpStartException;
import marto.rtl_tcp_andro.tools.RtlTcpStartException.err_info;
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
		case RtlTcp.EXIT_NOT_ENOUGH_POWER:
			return StrRes.get(R.string.exception_NOT_ENOUGH_POWER);

		default:
			return StrRes.get(R.string.exception_DEFAULT, id);
		}
	}
	
	public final RtlTcpStartException.err_info getReason() {
		switch (id) {
		case RtlTcp.LIBUSB_ERROR_IO:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_INVALID_PARAM:
			return err_info.unknown_error;
		case RtlTcp.LIBUSB_ERROR_ACCESS:
			return err_info.permission_denied;
		case RtlTcp.LIBUSB_ERROR_NO_DEVICE:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_NOT_FOUND:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_BUSY:
			return err_info.already_running;
		case RtlTcp.LIBUSB_ERROR_TIMEOUT:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_OVERFLOW:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_PIPE:
			return err_info.unknown_error;
		case RtlTcp.LIBUSB_ERROR_INTERRUPTED:
			return err_info.unknown_error;
		case RtlTcp.LIBUSB_ERROR_NO_MEM:
			return err_info.unknown_error;
		case RtlTcp.LIBUSB_ERROR_NOT_SUPPORTED:
			return err_info.no_devices_found;
		case RtlTcp.LIBUSB_ERROR_OTHER:
			return err_info.unknown_error;
		
		case RtlTcp.EXIT_OK:
			return null;
		case RtlTcp.EXIT_WRONG_ARGS:
			return err_info.unknown_error;
		case RtlTcp.EXIT_INVALID_FD:
			return err_info.permission_denied;
		case RtlTcp.EXIT_NO_DEVICES:
			return err_info.no_devices_found;
		case RtlTcp.EXIT_FAILED_TO_OPEN_DEVICE:
			return err_info.no_devices_found;
		case RtlTcp.EXIT_CANNOT_RESTART:
			return err_info.unknown_error;
		case RtlTcp.EXIT_CANNOT_CLOSE:
			return err_info.replug;
		case RtlTcp.EXIT_UNKNOWN:
			return err_info.unknown_error;
		case RtlTcp.EXIT_SIGNAL_CAUGHT:
			return err_info.unknown_error;
		case RtlTcp.EXIT_NOT_ENOUGH_POWER:
			return err_info.unknown_error;

		default:
			return null;
		}
	}
	
	public RtlTcpException(final int id) {
		super(translateToString(id));
		this.id = id;
	}

}
