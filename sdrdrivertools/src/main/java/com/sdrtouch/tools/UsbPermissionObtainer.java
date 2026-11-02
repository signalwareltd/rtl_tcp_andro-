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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;

import java.util.UUID;
import java.util.concurrent.Future;

public class UsbPermissionObtainer {
    private static final String ACTION_USB_PERMISSION = "com.sdrtouch.rtlsdr.USB_PERMISSION";
    private static final String SCHEME = "rtlsdr-permission";

    public static Future<UsbDeviceConnection> obtainFdFor(Context context, UsbDevice device) {
        PermissionRequest request = new PermissionRequest(context.getApplicationContext(), device);
        request.start();
        return request;
    }

    private static boolean sameDevice(UsbDevice a, UsbDevice b) {
        return a != null && b != null && a.getDeviceId() == b.getDeviceId()
                && a.getVendorId() == b.getVendorId() && a.getProductId() == b.getProductId()
                && a.getDeviceName().equals(b.getDeviceName());
    }

    private static final class PermissionRequest extends AsyncFuture<UsbDeviceConnection> {
        private final Object lifecycle = new Object();
        private final Context context;
        private final UsbManager manager;
        private final UsbDevice device;
        private final Uri callback = Uri.parse(SCHEME + ":" + UUID.randomUUID());
        private PendingIntent pendingIntent;
        private boolean permissionRegistered;
        private boolean detachRegistered;
        private boolean cleaned;

        private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (intent == null || !ACTION_USB_PERMISSION.equals(intent.getAction())
                        || !callback.equals(intent.getData())
                        || !sameDevice(device, intent.getParcelableExtra(UsbManager.EXTRA_DEVICE))) return;
                if (isDone()) { cleanup(); return; }
                finishOpen();
            }
        };
        private final BroadcastReceiver detachReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (intent != null && UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())
                        && sameDevice(device, intent.getParcelableExtra(UsbManager.EXTRA_DEVICE))) cancel(false);
            }
        };

        PermissionRequest(Context context, UsbDevice device) {
            this.context = context;
            this.device = device;
            this.manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        }

        void start() {
            synchronized (lifecycle) {
                if (isDone()) return;
                try {
                    if (!isPresent()) { cancel(false); return; }
                    if (manager.hasPermission(device)) { finishOpen(); return; }
                    IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
                    permissionFilter.addDataScheme(SCHEME);
                    register(permissionReceiver, permissionFilter);
                    permissionRegistered = true;
                    register(detachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
                    detachRegistered = true;
                    // Immutable callback already carries its identity. USB framework
                    // fill-in extras are deliberately unnecessary: recheck the live
                    // permission and device instead of trusting a grant boolean.
                    Intent intent = new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName())
                            .setData(callback).putExtra(UsbManager.EXTRA_DEVICE, device);
                    pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                    if (!isDone()) manager.requestPermission(device, pendingIntent);
                } catch (RuntimeException error) {
                    trySetException(error);
                    cleanup();
                }
            }
        }

        private void register(BroadcastReceiver receiver, IntentFilter filter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        }

        private boolean isPresent() {
            return sameDevice(device, manager.getDeviceList().get(device.getDeviceName()));
        }

        private void finishOpen() {
            UsbDeviceConnection connection = null;
            try {
                if (isDone()) return;
                if (isPresent() && manager.hasPermission(device)) connection = manager.openDevice(device);
                // Cancellation may have won while openDevice was returning. A
                // rejected result is never published and is closed by this owner.
                if (trySetDone(connection)) connection = null;
            } catch (RuntimeException error) {
                trySetException(error);
            } finally {
                try { if (connection != null) connection.close(); }
                finally { cleanup(); }
            }
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (lifecycle) { return super.cancel(mayInterruptIfRunning); }
        }

        @Override protected void onCancelled() { cleanup(); }

        private void cleanup() {
            synchronized (lifecycle) {
                if (cleaned) return;
                cleaned = true;
                if (permissionRegistered) unregister(permissionReceiver);
                if (detachRegistered) unregister(detachReceiver);
                if (pendingIntent != null) pendingIntent.cancel();
            }
        }

        private void unregister(BroadcastReceiver receiver) {
            try { context.unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) { /* Already removed by the framework. */ }
        }
    }

    private UsbPermissionObtainer() {}
}
