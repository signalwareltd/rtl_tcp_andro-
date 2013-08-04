/*
 * rtl_tcp_andro is an Android port of the famous rtl_tcp driver for 
 * RTL2832U based USB DVB-T dongles. It does not require root.
 * Copyright (C) 2012 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package marto.rtl_tcp_andro.tools;

public class RtlTcpStartException extends Exception {

	public enum err_info {
		permission_denied,
		root_required,
		no_devices_found,
		unknown_error,
		replug,
		already_running
	}
	
	private static final long serialVersionUID = -2093258176426113336L;

	private final err_info err;
	public RtlTcpStartException(final err_info err) {
		this.err = err;
	}
	
	public err_info getReason() {
		return err;
	}

}
