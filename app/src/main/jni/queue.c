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

#include "queue.h"
#include <string.h>
#include <stdio.h>

typedef struct queue_element queue_element_t;

struct queue_element {
	queue_element_t * next;
	queue_element_t * prev;
	queue_element_t * last;
	void * payload;
};

void queue_add(queue_t * queue, void * ptr) {
	if (*queue == NULL) {
		queue_element_t * new_el = (queue_element_t *) malloc(sizeof(queue_element_t));
		new_el->last = new_el;
		new_el->next = NULL;
		new_el->prev = NULL;
		new_el->payload = ptr;
		*queue = (void *) new_el;
	} else {
		queue_element_t * qe = (queue_element_t *) *queue;
		queue_element_t * new_el = (queue_element_t *) malloc(sizeof(queue_element_t));
		new_el->last = NULL;
		new_el->next = NULL;
		new_el->prev = qe->last;
		new_el->payload = ptr;
		qe->last->next = new_el;
		qe->last = new_el;
	}
}

void * queue_pop(queue_t * queue) {
	if (*queue == NULL) return NULL;

	queue_element_t * qe = (queue_element_t *) *queue;

	if (qe->next != NULL) {
		qe->next->prev = NULL;
		qe->next->last = qe->last;
	}

	void * result = qe->payload;
	*queue = (void *) qe->next;

	free(qe);

	return result;
}

/* Usage:
void queue_unit_test(void) {
	queue_t queue = NULL;

	int a = 1; int b = 3; int c = 5; int d = 18;

	queue_add(&queue, (void *) &a);
	queue_add(&queue, (void *) &b);
	queue_add(&queue, (void *) &c);
	queue_add(&queue, (void *) &d);

	c = 12;

	int * ans;
	while ((ans = (int *) queue_pop(&queue)))
		printf("%d ", *ans);
	// prints out 1 3 12 18
}
*/
