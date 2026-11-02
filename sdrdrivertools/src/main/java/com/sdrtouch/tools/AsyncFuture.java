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

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A single-completion future. Producers must cooperate with cancellation. */
public class AsyncFuture<V> implements Future<V> {
    private final Object locker = new Object();
    private V object;
    private Throwable failure;
    private boolean ready;
    private boolean cancelled;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (locker) {
            if (ready) return false;
            cancelled = true;
            ready = true;
            locker.notifyAll();
        }
        // No producer thread belongs to this future. The owner cancels its
        // external operation here; never invoke external code under locker.
        onCancelled();
        return true;
    }

    protected void onCancelled() {}

    @Override
    public boolean isCancelled() {
        synchronized (locker) { return cancelled; }
    }

    @Override
    public boolean isDone() {
        synchronized (locker) { return ready; }
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (locker) {
            while (!ready) locker.wait();
            return result();
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(unit, "unit");
        final long budget = unit.toNanos(timeout);
        final long start = System.nanoTime();
        synchronized (locker) {
            long remaining = budget;
            while (!ready) {
                if (remaining <= 0) throw new TimeoutException();
                TimeUnit.NANOSECONDS.timedWait(locker, remaining);
                remaining = budget - (System.nanoTime() - start);
            }
            return result();
        }
    }

    private V result() throws ExecutionException {
        if (cancelled) throw new CancellationException();
        if (failure != null) throw new ExecutionException(failure);
        return object;
    }

    /** False means the caller still owns (and must release) the supplied value. */
    public boolean trySetDone(V value) {
        synchronized (locker) {
            if (ready) return false;
            object = value;
            ready = true;
            locker.notifyAll();
            return true;
        }
    }

    public boolean trySetException(Throwable error) {
        Objects.requireNonNull(error, "error");
        synchronized (locker) {
            if (ready) return false;
            failure = error;
            ready = true;
            locker.notifyAll();
            return true;
        }
    }

    /** Compatible setter for producers that do not transfer a resource. */
    public void setDone(V value) { trySetDone(value); }
}
