/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
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

package com.sdrtouch.core;

import com.sdrtouch.tools.ArgumentParser;

import java.io.Serializable;

import static com.sdrtouch.tools.Check.stringLessThan;

public class SdrTcpArguments implements Serializable {
	private static final long serialVersionUID = 1L;
    private static final int MAX_STRING_LENGTH = 256;

    private static final int DEFAULT_GAIN = 24;
    private static final int DEFAULT_PPM = 0;
    private static final long DEFAULT_FREQUENCY = 100000000;
    private static final long DEFAULT_SAMPLING_RATE = 2048000;

	private final int gain;
	private final long samplerateHz;
    private final long frequencyHz;
	private final String address;
	private final int port;
	private final int ppm;

	public static SdrTcpArguments fromString(String arguments) throws IllegalArgumentException {
		return new SdrTcpArguments(new ArgumentParser(arguments));
	}

	private SdrTcpArguments(ArgumentParser arguments) {
		this.gain = arguments.getIntArgumentOrDefault("g", DEFAULT_GAIN);
        this.samplerateHz = arguments.getLongArgumentOrDefault("s", DEFAULT_SAMPLING_RATE);
        this.frequencyHz = arguments.getLongArgumentOrDefault("f", DEFAULT_FREQUENCY);
        this.address = stringLessThan(arguments.getStringArgument("a"), MAX_STRING_LENGTH);
        this.port = arguments.getIntArgument("p");
        this.ppm = arguments.getIntArgumentOrDefault("P", DEFAULT_PPM);
	}

    public int getGain() {
        return gain;
    }

    public long getSamplerateHz() {
        return samplerateHz;
    }

    public long getFrequencyHz() {
        return frequencyHz;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getPpm() {
        return ppm;
    }

    @Override
    public String toString() {
        return "SdrTcpArguments{" +
                "gain=" + gain +
                ", samplerateHz=" + samplerateHz +
                ", frequencyHz=" + frequencyHz +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", ppm=" + ppm +
                '}';
    }
}
