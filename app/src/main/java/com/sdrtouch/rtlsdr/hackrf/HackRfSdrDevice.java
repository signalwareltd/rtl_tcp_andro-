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

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.mantz_it.hackrf_android.Hackrf;
import com.mantz_it.hackrf_android.HackrfCallbackInterface;
import com.mantz_it.hackrf_android.HackrfUsbException;
import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;
import com.sdrtouch.tools.Log;

import java.io.IOException;
import java.util.List;

public class HackRfSdrDevice extends SdrDevice {
    private final static double BUFF_TIME = 0.1;
    private final UsbDevice device;

    private Thread processingThread;
    private volatile HackRfTcp tcp;

    public HackRfSdrDevice(UsbDevice device) {
        this.device = device;
    }

    @Override
    public int[] getSupportedCommands() {
        List<TcpCommand> commands =  HackRfTcp.SUPPORTED_COMMANDS;
        int[] supported_commands = new int[commands.size()];
        for (int i = 0; i < commands.size(); i++) {
            supported_commands[i] = commands.get(i).getCode();
        }
        return supported_commands;
    }

    @Override
    public void openAsync(final Context ctx, final SdrTcpArguments sdrTcpArguments) {
        processingThread = new Thread() {
            @Override
            public void run() {
                int queue_size = (int) (2 * BUFF_TIME * sdrTcpArguments.getSamplerateHz());
                try {
                    Log.appendLine("Opening HackRF");
                    Hackrf.initHackrf(ctx, device, new HackrfCallbackInterface() {
                        @Override
                        public void onHackrfReady(Hackrf hackrf) {
                            try {
                                Log.appendLine("HackRF ready");
                                tcp = new HackRfTcp(hackrf, sdrTcpArguments);

                                Log.appendLine("Initialising TCP");
                                tcp.initDevice();
                                tcp.prepareToAcceptConnections();

                                // ready to accept connections
                                announceOnOpen();
                                tcp.serveAndBlock();

                                // close device
                                Log.appendLine("Closing HackRF");
                                hackrf.stop();
                                announceOnClosed(null);
                            } catch (Exception e) {
                                announceOnClosed(e);
                                if (hackrf != null) {
                                    try {
                                        Log.appendLine("Closing HackRF due to "+e.getMessage());
                                        hackrf.stop();
                                    } catch (HackrfUsbException ee) {
                                        Log.appendLine("Failed to close HackRF due to "+ee.getMessage());
                                    }
                                }
                            }
                        }

                        @Override
                        public void onHackrfError(String message) {
                            announceOnClosed(new IOException(message));
                        }
                    }, queue_size);
                } catch (Exception e) {
                    announceOnClosed(e);
                } finally {
                    processingThread = null;
                }
                Log.appendLine("HackRF device thread finished.");
            }
        };
        processingThread.start();
    }

    @Override
    public void close() {
        if (tcp != null) {
            tcp.close();
        }
        if (processingThread != null && processingThread.isAlive()) {
            processingThread.interrupt();
            try {
                Log.appendLine("Attempting to join HackRF thread");
                processingThread.join();
                Log.appendLine("HackRF thread is dead");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getName() {
        return "HackRF";
    }
}
