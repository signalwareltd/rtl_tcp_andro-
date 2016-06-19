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

#ifndef THREADING_MARTIN_H_
#define THREADING_MARTIN_H_

#include <pthread.h>

	struct atomic_int {
		volatile int value;
        pthread_mutex_t locker;
	} typedef atomic_int_t;

    #define ATOMIC_INT_INIT(val) {(val), PTHREAD_MUTEX_INITIALIZER}

    void atomic_int_init(atomic_int_t * var, const int value);
    int atomic_int_getval(atomic_int_t * var);
    int atomic_int_getval_and_set(atomic_int_t * var, const int value);
    void atomic_int_free(atomic_int_t * var);
    void atomic_int_setval(atomic_int_t * var, const int value);

    struct thread_signaller {
        pthread_mutex_t locker;
        pthread_cond_t condition;
        volatile int already_called;
    } typedef thread_signaller_t;

    #define SIGNALLER_INIT {PTHREAD_MUTEX_INITIALIZER, PTHREAD_COND_INITIALIZER, 0}

    void signaller_wait(thread_signaller_t * var);
    void signaller_signal(thread_signaller_t * var);
    void signaller_reset(thread_signaller_t * var);

#endif
