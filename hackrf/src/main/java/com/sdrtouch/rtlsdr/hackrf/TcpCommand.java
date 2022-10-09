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

package com.sdrtouch.rtlsdr.hackrf;

import android.util.SparseArray;

public enum TcpCommand {
    TCP_SET_FREQ(0x01),
    TCP_SET_SAMPLE_RATE(0x02),
    TCP_SET_GAIN_MODE(0x03),
    TCP_SET_GAIN(0x04),
    TCP_SET_FREQ_CORRECTION(0x05),
    TCP_SET_IF_TUNER_GAIN(0x06),
    TCP_SET_TEST_MODE(0x07),
    TCP_SET_AGC_MODE(0x08),
    TCP_SET_DIRECT_SAMPLING(0x09),
    TCP_SET_OFFSET_TUNING(0x0a),
    TCP_SET_RTL_XTAL(0x0b),
    TCP_SET_TUNER_XTAL(0x0c),
    TCP_SET_TUNER_GAIN_BY_ID(0x0d),
    TCP_ANDROID_EXIT(0x7e),
    TCP_ANDROID_GAIN_BY_PERCENTAGE(0x7f),
    TCP_ANDROID_ENABLE_16_BIT_SIGNED(0x80),

    // SDR Play commands
    RSP_TCP_COMMAND_SET_ANTENNA(0x1f),
    RSP_TCP_COMMAND_SET_LNASTATE(0x20),
    RSP_TCP_COMMAND_SET_IF_GAIN_R(0x21),
    RSP_TCP_COMMAND_SET_AGC(0x22),
    RSP_TCP_COMMAND_SET_AGC_SETPOINT(0x23),
    RSP_TCP_COMMAND_SET_NOTCH(0x24),
    RSP_TCP_COMMAND_SET_BIAST(0x25);

    private final static SparseArray<TcpCommand> COMMAND_MAP = new SparseArray<>(TcpCommand.values().length);

    static {
        for (TcpCommand c : TcpCommand.values()) {
            if (COMMAND_MAP.indexOfKey(c.code) >= 0) {
                throw new RuntimeException("Duplicate code for "+c);
            }
            COMMAND_MAP.put(c.code, c);
        }
    }

    private final int code;

    TcpCommand(int code) {
        this.code = code;
    }

    public static TcpCommand fromCode(int code) {
        return COMMAND_MAP.get(code);
    }

    public int getCode() {
        return code;
    }
}
