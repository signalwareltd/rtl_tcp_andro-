/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
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

#ifndef RTLSDR_TCP_COMMANDS_H
#define RTLSDR_TCP_COMMANDS_H

// These are the commands that clients can send to the driver
// they are send by the client via the TCP connection
// each command consists of a uint8_t code and a uint32_t parameter
// parameter descriptions are on the left

typedef enum
{
    // Standard rtl-tcp API
    TCP_SET_FREQ = 0x01, // rtlsdr_set_center_freq
    TCP_SET_SAMPLE_RATE = 0x02, // rtlsdr_set_sample_rate
    TCP_SET_GAIN_MODE = 0x03, // rtlsdr_set_tuner_gain_mode
    TCP_SET_GAIN = 0x04, // rtlsdr_set_tuner_gain
    TCP_SET_FREQ_CORRECTION = 0x05, // rtlsdr_set_freq_correction
    TCP_SET_IF_TUNER_GAIN = 0x06, // rtlsdr_set_tuner_if_gain
    TCP_SET_TEST_MODE = 0x07, // rtlsdr_set_testmode
    TCP_SET_AGC_MODE = 0x08, // rtlsdr_set_agc_mode
    TCP_SET_DIRECT_SAMPLING = 0x09, // rtlsdr_set_direct_sampling
    TCP_SET_OFFSET_TUNING = 0x0a, // rtlsdr_set_offset_tuning
    TCP_SET_RTL_XTAL = 0x0b, // rtlsdr_set_xtal_freq with rtl_freq set as the parameter
    TCP_SET_TUNER_XTAL = 0x0c, // rtlsdr_set_xtal_freq with tuner_freq set as the parameter
    TCP_SET_TUNER_GAIN_BY_ID = 0x0d, // set_gain_by_index

    // Android only rtl-tcp API
    TCP_ANDROID_EXIT = 0x7e, // send any value to cause the driver to turn off itself
    TCP_ANDROID_GAIN_BY_PERCENTAGE = 0x7f, // set device gain by percentage. send values 0 to 100, where 0 means no gain and 100 means maximum gain
    TCP_ANDROID_ENABLE_16_BIT_SIGNED = 0x80, // [NOT SUPPORTED IN RTL-SDR] set to 1 to enable 16 bit unsigned sample size (only supported devices such as SDRplay)
} tcp_commands_t;

#endif //RTLSDR_TCP_COMMANDS_H
