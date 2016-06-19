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

#ifndef WORKPOOL_H_
#define WORKPOOL_H_

#include "extbuffer.h"
#include "queue.h"
#include <pthread.h>
#include <stddef.h>

typedef struct workpool {
	queue_t * queues;
	pthread_mutex_t * mutexes;
	pthread_cond_t * conditions;
	int max_elements;
	int max_ids;
	int elements;
	int extbuffer_type;
	volatile int valid;
} workpool_t;

// thread safe methods
extbuffer_t * pool_get_wait_lock(workpool_t * pool, int id, int block);
void pool_get_unlock(workpool_t * pool, int id,  extbuffer_t * buffer);


// non thread safe methods
void pool_init(workpool_t * pool, int max_elements, int extbuffer_type);
void pool_free(workpool_t * pool);
void pool_set_threads(workpool_t * pool, int number_of_ids);

#endif
