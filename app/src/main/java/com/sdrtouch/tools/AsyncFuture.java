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

package com.sdrtouch.tools;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.sdrtouch.tools.Check.isTrue;

/**
 * This is a future task that will block until result has been returned
 */
public class AsyncFuture<V> implements Future<V> {
    private final Object locker = new Object();
    private V object;
    private boolean ready = false;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        synchronized (locker) {
            return ready;
        }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (locker) {
            locker.wait();
            return object;
        }
    }

    @Override
    public V get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (locker) {
            locker.wait(unit.toMillis(timeout));
            return object;
        }
    }

    public void setDone(V object) {
        synchronized (locker) {
            isTrue(!this.ready);
            this.object = object;
            this.ready = true;
            locker.notify();
        }
    }
}
