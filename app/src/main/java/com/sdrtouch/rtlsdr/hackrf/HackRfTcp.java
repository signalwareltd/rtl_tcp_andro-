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

package com.sdrtouch.rtlsdr.hackrf;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfUsbException;
import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.tools.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_ANDROID_EXIT;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_ANDROID_GAIN_BY_PERCENTAGE;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_AGC_MODE;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_FREQ;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_GAIN;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_GAIN_MODE;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_IF_TUNER_GAIN;
import static com.sdrtouch.rtlsdr.hackrf.TcpCommand.TCP_SET_SAMPLE_RATE;

public class HackRfTcp {
    private final static int DEFAULT_LNA_GAIN = 30;
    private final static int DEFAULT_VGA_GAIN = 32;

    private final static int SOCKET_TIMEOUT_MS = 20 * 1_000;
    public static final List<TcpCommand> SUPPORTED_COMMANDS = Arrays.asList(
            TCP_SET_FREQ,
            TCP_SET_SAMPLE_RATE,
            TCP_ANDROID_EXIT,
            TCP_ANDROID_GAIN_BY_PERCENTAGE,
            TCP_SET_IF_TUNER_GAIN,
            TCP_SET_GAIN,
            TCP_SET_AGC_MODE,
            TCP_SET_GAIN_MODE
    );
    private final ServerSocket socket = new ServerSocket();
    private final Hackrf hackrf;
    private final SdrTcpArguments sdrTcpArguments;
    private volatile boolean canceled = false;

    public HackRfTcp(Hackrf hackrf, SdrTcpArguments sdrTcpArguments) throws IOException {
        this.hackrf = hackrf;
        this.sdrTcpArguments = sdrTcpArguments;
    }

    public void prepareToAcceptConnections() throws IOException {
        String address = sdrTcpArguments.getAddress();
        int port = sdrTcpArguments.getPort();
        socket.bind(new InetSocketAddress(address, port));
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
    }

    private void executeCommand(TcpCommand command, long argument) throws HackrfUsbException {
        int perc, scaled;
        switch (command) {
            case TCP_SET_FREQ:
                hackrf.setFrequency(argument);
                break;
            case TCP_SET_SAMPLE_RATE:
                setSampleRate((int) argument);
                break;
            case TCP_SET_IF_TUNER_GAIN:
                perc = remap_rtl_gain_to_perc((int) argument);
                scaled = (perc * 40) / 100; // map from 0 to 40
                if (scaled < 0) scaled = 0;
                if (scaled > 40) scaled = 40;
                hackrf.setRxLNAGain(scaled);
                break;
            case TCP_SET_GAIN:
                perc = remap_rtl_gain_to_perc((int) argument);
                scaled = (perc * 62) / 100; // map from 0 to 62
                if (scaled < 0) scaled = 0;
                if (scaled > 62) scaled = 62;
                hackrf.setRxVGAGain(scaled);
                break;
            case TCP_SET_AGC_MODE:
            case TCP_SET_GAIN_MODE:
                // we don't have automatic mode, so just put some default gain values that should work ok
                hackrf.setRxVGAGain(DEFAULT_VGA_GAIN);
                hackrf.setRxLNAGain(DEFAULT_LNA_GAIN);
                break;
            case TCP_ANDROID_GAIN_BY_PERCENTAGE:
                int scaled_lna = ((int) argument * 40) / 100; // map from 0 to 40
                if (scaled_lna < 0) scaled_lna = 0;
                if (scaled_lna > 40) scaled_lna = 40;
                hackrf.setRxLNAGain(scaled_lna);

                int scaled_vga = ((int) argument * 62) / 100;
                if (scaled_vga < 0) scaled_vga = 0;
                if (scaled_vga > 62) scaled_vga = 62;
                hackrf.setRxVGAGain(scaled_vga);
                break;
            case TCP_ANDROID_EXIT:
                canceled = true;
                Thread.currentThread().interrupt();
                break;
             default:
                 Log.appendLine("Unsupported command "+command);
                 break;
        }
    }

    private void setSampleRate(int argument) throws HackrfUsbException {
        hackrf.setSampleRate(argument, 1);
        hackrf.setBasebandFilterBandwidth(Hackrf.computeBasebandFilterBandwidth((int) (0.75 * argument)));
    }

    public void close() {
        canceled = true;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initDevice() throws HackrfUsbException {
        executeCommand(TCP_SET_SAMPLE_RATE, sdrTcpArguments.getSamplerateHz());
        executeCommand(TCP_SET_FREQ, sdrTcpArguments.getFrequencyHz());
        executeCommand(TCP_SET_GAIN_MODE, 1);
    }

    public void serveAndBlock() throws IOException, HackrfUsbException {
        DataInputStream inputStream = null;
        DataOutputStream outputStream = null;
        HardwareCommand command = null;
        Socket conn = null;

        try {
            Log.appendLine("Waiting for client on "+socket.getInetAddress().getHostAddress()+"...");
            conn = socket.accept();
            Log.appendLine("Client connected");

            conn.setTcpNoDelay(true);
            inputStream = new DataInputStream(conn.getInputStream());
            outputStream = new DataOutputStream(conn.getOutputStream());
            command = new HardwareCommand(inputStream);
            command.start();

            Log.appendLine("Starting HackRF RX");
            ArrayBlockingQueue<byte[]> rxqueue = hackrf.startRX();

            Log.appendLine("Sending RX data...");
            sendDongleInfo(outputStream);
            try {
                while (!Thread.currentThread().isInterrupted() && !command.isInterrupted() && !canceled) {
                    byte[] buff = rxqueue.poll(1, TimeUnit.SECONDS);
                    if (buff != null) {
                        // HackRF input is signed
                        // rtl sdr input is unsigned
                        for (int i = 0; i < buff.length; i++) {
                            int b = buff[i];
                            buff[i] = (byte) (b + 128);
                        }
                        outputStream.write(buff);
                        hackrf.returnBufferToBufferPool(buff);
                    }
                }
            } catch (InterruptedException ignored) {}
            if (command.e != null) {
                Exception e = command.e;
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                if (e instanceof HackrfUsbException) {
                    throw (HackrfUsbException) e;
                }
                throw new RuntimeException(e);
            }
        } finally {
            if (command != null && command.isAlive()) {
                command.interrupt();
            }
            if (conn != null) {
                conn.close();
            }
            socket.close();
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (command != null && command.isAlive()) {
                try {
                    Log.appendLine("Joining command thread.");
                    command.join();
                    Log.appendLine("Command thread has finished.");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.appendLine("Closing TCP server.");
        }
    }

    private void sendDongleInfo(DataOutputStream out) throws IOException {
        // dongle magic
        out.write(new byte[]{(byte) 'h', (byte) 'x', (byte) 'r', (byte) 'f'});
        out.writeInt(0); // tuner unknown
        out.writeInt(0); // 0 gains
    }

    private class HardwareCommand extends Thread {
        private final DataInputStream inputStream;
        private Exception e;

        public HardwareCommand(DataInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                Log.appendLine("Command listener starting");
                while (!this.isInterrupted()) {
                    int code = inputStream.readByte() & 0xFF;
                    long argument = inputStream.readInt() & 0xFFFFFFFFL;
                    TcpCommand command = TcpCommand.fromCode(code);
                    if (command != null) {
                        executeCommand(command, argument);
                    }
                }
            } catch (IOException | HackrfUsbException e) {
                Log.appendLine("Command listener closing due to " + e.getMessage());
                this.e = e;
            }
            Log.appendLine("Command listener closed");
        }
    }

    private static int remap_rtl_gain_to_perc(int tenthsOfDb) {
        return (tenthsOfDb + 10) / 5; // -10 is 0 while 490 is 100
    }
}
