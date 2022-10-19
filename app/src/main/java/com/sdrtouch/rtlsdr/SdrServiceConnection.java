package com.sdrtouch.rtlsdr;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.sdrtouch.core.SdrTcpArguments;
import com.sdrtouch.core.devices.SdrDevice;

public class SdrServiceConnection implements ServiceConnection {
    private final SdrDevice sdrDevice;
    private final SdrTcpArguments sdrTcpArguments;
    private final Runnable onDisconnected;
    private volatile boolean isBound;

    SdrServiceConnection(SdrDevice sdrDevice, SdrTcpArguments sdrTcpArguments, Runnable onDisconnected) {
        this.sdrDevice = sdrDevice;
        this.sdrTcpArguments = sdrTcpArguments;
        this.onDisconnected = onDisconnected;
        this.isBound = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder ibinder) {
        isBound = true;
        BinaryRunnerService.LocalBinder binder = (BinaryRunnerService.LocalBinder) ibinder;
        binder.startWithDevice(sdrDevice, sdrTcpArguments);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        isBound = false;
        onDisconnected.run();
    }

    boolean isBound() {
        return isBound;
    }
}
