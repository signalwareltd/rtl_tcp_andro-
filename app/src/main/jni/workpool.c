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

#include "workpool.h"

#define WORKPOOL_WARNINGS (DEBUG_MODE)

#define TIMEOUT_MS (1000)

static extbuffer_t * pop(workpool_t * pool, int id, int block) {
	extbuffer_t * result = NULL;
	if (!pool->valid) return result;
#if WORKPOOL_WARNINGS
	if (id < 0 || id >= pool->max_ids) LOGI("WORKPOOL EXCEPTION: Tried popping impossible id %d out of %d!\n", id, pool->max_ids);
#endif

	pthread_mutex_lock(&pool->mutexes[id]);
	result = queue_pop(&pool->queues[id]);
	if (result == NULL && block) {
		struct timeval tp;
		struct timespec ts;
		gettimeofday(&tp, NULL);

		ts.tv_sec = tp.tv_sec;
		ts.tv_nsec = tp.tv_usec * 1000;
		ts.tv_nsec += TIMEOUT_MS * 1000000;
		ts.tv_sec += ts.tv_nsec / 1000000000L;
		ts.tv_nsec = ts.tv_nsec % 1000000000L;

		pthread_cond_timedwait(&pool->conditions[id], &pool->mutexes[id], &ts);

		result = queue_pop(&pool->queues[id]);
	}
	pthread_mutex_unlock(&pool->mutexes[id]);

	return result;
}

static void add(workpool_t * pool, int id, extbuffer_t * buffer) {
	if (!pool->valid) return;
#if WORKPOOL_WARNINGS
	if (buffer == NULL) {
		LOGI("WORKPOOL EXCEPTION: NULL buffer!");
		return;
	}

	if (id < 0 || id >= pool->max_ids) LOGI("WORKPOOL EXCEPTION: Tried adding impossible id %d out of %d!\n", id, pool->max_ids);
#endif

	pthread_mutex_lock(&pool->mutexes[id]);
	queue_add(&pool->queues[id], buffer);
	pthread_cond_broadcast(&pool->conditions[id]);
	pthread_mutex_unlock(&pool->mutexes[id]);
}

extbuffer_t * pool_get_wait_lock(workpool_t * pool, int id, int block) {
	if (!pool->valid) return NULL;
	extbuffer_t * result = pop(pool, id, 0);

	if (result == NULL) {
		if (id == 0 && pool->elements < pool->max_elements) {
			result = (extbuffer_t *) malloc(sizeof(extbuffer_t));
			extbuffer_init(result, pool->extbuffer_type);
			pool->elements++;
		} else if (block) {
			 result = pop(pool, id, 1);
		}
	}

	return result;
}

void pool_get_unlock(workpool_t * pool, int id,  extbuffer_t * buffer) {
	if (!pool->valid) return;
#if WORKPOOL_WARNINGS
	if (buffer == NULL) LOGI("WORKPOOL EXCEPTION: Was supplied a NULL buffer!\n");
#endif
	if (id == pool->max_ids-1) {
		extbuffer_preparetohandle(buffer, 0);
		add(pool, 0, buffer);
	} else {
		add(pool, id+1, buffer);
	}
}

void pool_init(workpool_t * pool, int max_elements, int extbuffer_type) {
	pool->queues = NULL;
	pool->mutexes = NULL;
	pool->conditions = NULL;
	pool->max_elements = max_elements;
	pool->max_ids = 0;
	pool->elements = 0;
	pool->extbuffer_type = extbuffer_type;
	pool->valid = 0;
}

void pool_free(workpool_t * pool) {
	if (pool->max_ids > 0) {
		int i;
		for (i = 0; i < pool->max_ids; i++) {
			extbuffer_t * buff;
			while ((buff = queue_pop(&pool->queues[i]))) {
				extbuffer_free(buff);
				free (buff);
				pool->elements--;
			}
			pthread_mutex_destroy(&pool->mutexes[i]);
			pthread_cond_destroy(&pool->conditions[i]);
		}

		free(pool->queues);
		free(pool->mutexes);
		free(pool->conditions);

		pool->max_ids = 0;
#if WORKPOOL_WARNINGS
		if (pool->elements != 0) LOGI("WORKPOOL EXCEPTION: Workpool free has a memory leak! %d elements were not freed!\n", pool->elements);
#endif
		pool->elements = 0;
	}
	pool->valid = 0;
}

void pool_set_threads(workpool_t * pool, int number_of_ids) {
	if (number_of_ids <= 0) return;
	pool_free(pool);
	pool->max_ids = number_of_ids;

	pool->queues = (queue_t *) malloc(sizeof(queue_t) * pool->max_ids);
	pool->mutexes = (pthread_mutex_t *) malloc(sizeof(pthread_mutex_t) * pool->max_ids);
	pool->conditions = (pthread_cond_t *) malloc(sizeof(pthread_cond_t) * pool->max_ids);

	int i;
	for (i = 0; i < pool->max_ids; i++) {
		pool->queues[i] = NULL;
		pthread_mutex_init(&pool->mutexes[i], NULL);
		pthread_cond_init(&pool->conditions[i], NULL);

	}

	pool->valid = 1;
}
