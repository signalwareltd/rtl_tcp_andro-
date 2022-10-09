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

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionTools {

	public static String getFullStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}
	
	public static String getNicelyFormattedTrace(Throwable t) {
		StackTraceElement[] elements = t.getStackTrace();
		StringBuilder sb = new StringBuilder();
		sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append("\n");
		
		String lastForeign = null;
		for (StackTraceElement stackTraceElement : elements) {
			if (stackTraceElement.getClassName().startsWith("marto.")) {
				sb.append(String.format(" -> %s(%s:%d)\n", getSimpleClassName(stackTraceElement.getClassName()), stackTraceElement.getMethodName(), stackTraceElement.getLineNumber()));
			} else {
				String line = String.format(" -> %s ", getSimpleClassName(stackTraceElement.getClassName()));
				if (!line.equals(lastForeign)) sb.append(line);
				lastForeign = line;
			}
		}
		if (t.getCause() != null) sb.append("\nCaused by ").append(getNicelyFormattedTrace(t.getCause()));
		return sb.toString();
	}
	
	private static String getSimpleClassName(String fullName) {
		if (fullName.indexOf('.') < 0) return fullName;
		String[] path = fullName.split("\\.");
		return path[path.length-1];
	}
	
	private ExceptionTools() {}
}
