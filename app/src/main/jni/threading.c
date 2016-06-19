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

#include "threading.h"

void atomic_int_init(atomic_int_t * var, const int value) {
    pthread_mutex_init(&var->locker, NULL);
    atomic_int_setval(var, value);
}

int atomic_int_getval(atomic_int_t * var) {
    int val;
    pthread_mutex_lock(&var->locker);
    val = var->value;
    pthread_mutex_unlock(&var->locker);
    return val;
}

int atomic_int_getval_and_set(atomic_int_t * var, const int value) {
    int val;
    pthread_mutex_lock(&var->locker);
    val = var->value;
    var->value = value;
    pthread_mutex_unlock(&var->locker);
    return val;
}

void atomic_int_free(atomic_int_t * var) {
    pthread_mutex_destroy(&var->locker);
}

void atomic_int_setval(atomic_int_t * var, const int value) {
    pthread_mutex_lock(&var->locker);
    var->value = value;
    pthread_mutex_unlock(&var->locker);
}


void signaller_wait(thread_signaller_t * var) {
    pthread_mutex_lock(&var->locker);
    if (!var->already_called) pthread_cond_wait(&var->condition, &var->locker);
    pthread_mutex_unlock(&var->locker);
}

void signaller_signal(thread_signaller_t * var) {
    pthread_mutex_lock(&var->locker);
    var->already_called = 1;
    pthread_cond_broadcast(&var->condition);
    pthread_mutex_unlock(&var->locker);
}

void signaller_reset(thread_signaller_t * var) {
    pthread_mutex_lock(&var->locker);
    var->already_called = 0;
    pthread_mutex_unlock(&var->locker);
}