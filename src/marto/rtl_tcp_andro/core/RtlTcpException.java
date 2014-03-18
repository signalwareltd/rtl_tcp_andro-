package marto.rtl_tcp_andro.core;

public class RtlTcpException extends Exception {
	
	private static final long serialVersionUID = 9112234577039075951L;

	public RtlTcpException(final Exception e) {
		super(e);
	}

	public RtlTcpException() {
		super();
	}

	public RtlTcpException(final String msg) {
		super(msg);
	}

}
